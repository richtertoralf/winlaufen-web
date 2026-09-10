package de.winlaufen.web.bridge.control;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.BridgeConfigStore;
import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.output.OutputTargetRuntime;
import de.winlaufen.web.bridge.startlist.CanonicalStartList;
import de.winlaufen.web.bridge.startlist.StartListImport;
import de.winlaufen.web.bridge.startlist.StartListParser;
import de.winlaufen.web.bridge.startlist.StartListStore;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.PresentationConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The only organiser user interface. It binds to all local interfaces by default for trusted-LAN
 * administration and never serves the web viewer or any public state.
 */
public final class BridgeControlServer implements AutoCloseable {

    private static final int MAX_BODY_BYTES = 32_768;
    private static final int MAX_TARGETS = 32;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final CanonicalStateStore state;
    private final BridgeConfigStore store;
    private final StartListStore startLists;
    private final Supplier<BridgeConfig> config;
    private final Supplier<List<OutputTargetRuntime>> runtimes;
    private final Consumer<BridgeConfig> changed;

    public BridgeControlServer(String bind, int port, CanonicalStateStore state,
                               BridgeConfigStore store, StartListStore startLists,
                               Supplier<BridgeConfig> config,
                               Supplier<List<OutputTargetRuntime>> runtimes,
                               Consumer<BridgeConfig> changed) throws IOException {
        this.state = state;
        this.store = store;
        this.startLists = startLists;
        this.config = config;
        this.runtimes = runtimes;
        this.changed = changed;
        this.server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod())) {
                handleGet(exchange, path);
            } else if ("POST".equals(exchange.getRequestMethod()) && "/api/v1/config".equals(path)) {
                update(exchange);
            } else if ("POST".equals(exchange.getRequestMethod())
                    && "/api/v1/startlist".equals(path)) {
                importStartList(exchange);
            } else {
                text(exchange, 405, "Methode nicht erlaubt");
            }
        } catch (IllegalArgumentException ex) {
            json(exchange, 400, BridgeControlJson.error(ex.getMessage()));
        } catch (Exception ex) {
            json(exchange, 500, BridgeControlJson.error("Interner Fehler"));
        } finally {
            exchange.close();
        }
    }

    private void handleGet(HttpExchange exchange, String path) throws IOException {
        switch (path) {
            case "/" -> resource(exchange, "/bridge-control/index.html", "text/html; charset=utf-8");
            case "/assets/control.css" -> resource(exchange, "/bridge-control/control.css", "text/css; charset=utf-8");
            case "/assets/control.js" -> resource(exchange, "/bridge-control/control.js", "text/javascript; charset=utf-8");
            case "/api/v1/config" -> json(exchange, 200, BridgeControlJson.config(config.get()));
            case "/api/v1/status" -> json(exchange, 200,
                    BridgeControlJson.status(state.get(), runtimes.get(), startLists.current()));
            default -> text(exchange, 404, "Nicht gefunden");
        }
    }

    private void update(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            text(exchange, 415, "Formulardaten erforderlich");
            return;
        }
        if (!sameOrigin(exchange.getRequestHeaders().getFirst("Origin"),
                exchange.getRequestHeaders().getFirst("Host"))) {
            text(exchange, 403, "Origin abgelehnt");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            text(exchange, 413, "Anfrage zu groß");
            return;
        }
        Map<String, String> form = form(new String(body, StandardCharsets.UTF_8));
        BridgeConfig old = config.get();
        BridgeConfig next = new BridgeConfig("WINLAUFEN",
                BridgeConfigStore.validateHost(required(form, "sourceHost")),
                old.controlBindAddress(), old.controlPort(),
                targets(form, old),
                new PresentationConfig(on(form, "showClub"), on(form, "showAssociation"),
                        on(form, "showNation"), on(form, "showShooting"),
                        on(form, "showPublicMessages")),
                // Not on this form, and it still has to survive a save: the whole configuration is
                // rewritten from what is passed here, so a value left out would silently vanish.
                old.competitionTimeZone());
        store.save(next);
        changed.accept(next);
        logWarnings(next);
        json(exchange, 200, BridgeControlJson.config(next));
    }

    /**
     * Imports a WinLaufen start-list export and replaces the whole stored start list with it.
     *
     * <p>The file arrives as the raw request body with its name in the {@code name} query
     * parameter, which is the smallest shape this server can accept: {@code com.sun.net.httpserver}
     * brings no multipart parser, and hand-writing one for a single upload would be far more code
     * than the upload itself. {@code application/octet-stream} is required on purpose — it is not
     * a CORS-safelisted content type, so together with the Origin check a foreign page cannot post
     * here without a preflight this server never answers.
     *
     * <p>The supplied name is never used as a path. It only selects the format by its extension
     * and becomes the diagnostic label; the parser reduces it to a bare file name, and nothing is
     * ever written under it. The upload itself stays in memory, bounded by the parser limit, so no
     * temporary file has to be cleaned up.
     *
     * <p>Parse, validate, persist and swap happen in that order inside the store. Any failure
     * leaves the previous start list in force, which is why every error path here returns without
     * having touched it.
     */
    private void importStartList(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("application/octet-stream")) {
            text(exchange, 415, "Startlistendatei als application/octet-stream erforderlich");
            return;
        }
        if (!sameOrigin(exchange.getRequestHeaders().getFirst("Origin"),
                exchange.getRequestHeaders().getFirst("Host"))) {
            text(exchange, 403, "Origin abgelehnt");
            return;
        }
        String name = form(exchange.getRequestURI().getRawQuery()).get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Dateiname fehlt");
        }
        byte[] data = exchange.getRequestBody().readNBytes(StartListParser.MAX_INPUT_BYTES + 1);
        if (data.length > StartListParser.MAX_INPUT_BYTES) {
            text(exchange, 413, "Startlistendatei ist größer als "
                    + StartListParser.MAX_INPUT_BYTES + " Bytes");
            return;
        }
        // Throws StartListFormatException, an IllegalArgumentException, which the handler turns
        // into a 400 carrying exactly the parser's own German message.
        StartListImport parsed = StartListParser.parse(data, name);
        CanonicalStartList adopted;
        try {
            adopted = startLists.replace(parsed);
        } catch (IOException ex) {
            // The path belongs in the bridge log, not in a browser response.
            System.out.println("WARNUNG: Startliste konnte nicht gespeichert werden: "
                    + ex.getMessage());
            json(exchange, 500, BridgeControlJson.error(
                    "Startliste konnte nicht gespeichert werden. Bisherige Startliste bleibt gültig."));
            return;
        }
        System.out.printf("Startliste übernommen: %s, %d Teilnehmer, Generation %d%n",
                adopted.sourceLabel(), adopted.entries().size(), adopted.generation());
        json(exchange, 200, BridgeControlJson.startListResult(adopted));
    }

    /**
     * The operator does not have to keep Bridge Control open to see an accepted risk. The texts
     * come from the same views the API returns, so log and surface can never disagree.
     */
    private static void logWarnings(BridgeConfig config) {
        for (BridgeControlJson.TargetView view : BridgeControlJson.views(config)) {
            if (view.transportWarning() != null) {
                System.out.println("WARNUNG: Output Target \"" + view.id() + "\": "
                        + view.transportWarning());
            }
            if (view.secretWarning() != null) {
                System.out.println("WARNUNG: Output Target \"" + view.id() + "\": "
                        + view.secretWarning());
            }
        }
    }

    static List<OutputTargetConfig> targets(Map<String, String> form, BridgeConfig old) {
        int count = Integer.parseInt(required(form, "targetCount"));
        if (count < 0 || count > MAX_TARGETS) {
            throw new IllegalArgumentException("Zu viele Targets");
        }
        List<OutputTargetConfig> targets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String prefix = "target." + index + ".";
            String id = required(form, prefix + "id");
            OutputTargetType type = OutputTargetType.valueOf(required(form, prefix + "type"));
            String secret = form.getOrDefault(prefix + "secret", "");
            if (secret.isBlank()) {
                secret = old.targets().stream()
                        .filter(target -> target.id().equals(id))
                        .map(OutputTargetConfig::secret)
                        .findFirst()
                        .orElseGet(() -> secretForNewTarget(type, id));
            }
            targets.add(new OutputTargetConfig(id, type,
                    "on".equals(form.get(prefix + "enabled")),
                    URI.create(required(form, prefix + "endpoint")),
                    required(form, prefix + "channelId"),
                    secret));
        }
        return targets;
    }

    /**
     * Convenience of the simple self-hosted case: a brand new SELFHOST target may be created with
     * the address alone and then falls back to the documented prototype ingest secret, which is
     * exactly what the installer writes on a presentation node. The fallback is deliberately
     * narrow — it never applies to an existing target, never to the local target, and never to
     * RICHTER_PROJECTS — and the resulting target carries a permanent warning.
     */
    private static String secretForNewTarget(OutputTargetType type, String id) {
        if (type != OutputTargetType.SELFHOST) {
            throw new IllegalArgumentException("Secret fehlt für " + id);
        }
        return BridgeConfigStore.DEFAULT_LOCAL_SECRET;
    }

    /** Same-origin check for the configuration form; a missing or foreign Origin is rejected. */
    static boolean sameOrigin(String origin, String host) {
        if (origin == null || host == null) {
            return false;
        }
        try {
            URI parsed = URI.create(origin);
            return "http".equalsIgnoreCase(parsed.getScheme())
                    && parsed.getHost() != null
                    && hostOnly(parsed.getHost()).equalsIgnoreCase(hostOnly(host));
        } catch (Exception ex) {
            return false;
        }
    }

    /** Strips an optional port and IPv6 brackets, so {@code [::1]:44442} matches {@code [::1]}. */
    static String hostOnly(String value) {
        String host = value;
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(1, end) : host;
        }
        int colon = host.lastIndexOf(':');
        return colon > 0 && host.indexOf(':') == colon ? host.substring(0, colon) : host;
    }

    private static boolean on(Map<String, String> form, String key) {
        return "on".equals(form.get(key));
    }

    private static String required(Map<String, String> form, String key) {
        String value = form.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Feld fehlt: " + key);
        }
        return value;
    }

    /** Decodes a form body or a query string; an absent query yields no values. */
    private static Map<String, String> form(String body) {
        Map<String, String> values = new HashMap<>();
        if (body == null || body.isEmpty()) {
            return values;
        }
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }

    private static void resource(HttpExchange exchange, String name, String type) throws IOException {
        try (InputStream input = BridgeControlServer.class.getResourceAsStream(name)) {
            if (input == null) {
                text(exchange, 404, "Nicht gefunden");
                return;
            }
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static void json(HttpExchange exchange, int status, String value) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        bytes(exchange, status, value);
    }

    private static void text(HttpExchange exchange, int status, String value) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        bytes(exchange, status, value);
    }

    private static void bytes(HttpExchange exchange, int status, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, encoded.length);
        exchange.getResponseBody().write(encoded);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
