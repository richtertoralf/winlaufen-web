package de.winlaufen.web.bridge.control;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.BridgeConfigStore;
import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.output.OutputTargetRuntime;
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
    private final Supplier<BridgeConfig> config;
    private final Supplier<List<OutputTargetRuntime>> runtimes;
    private final Consumer<BridgeConfig> changed;

    public BridgeControlServer(String bind, int port, CanonicalStateStore state,
                               BridgeConfigStore store, Supplier<BridgeConfig> config,
                               Supplier<List<OutputTargetRuntime>> runtimes,
                               Consumer<BridgeConfig> changed) throws IOException {
        this.state = state;
        this.store = store;
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
            case "/api/v1/status" -> json(exchange, 200, BridgeControlJson.status(state.get(), runtimes.get()));
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
                        on(form, "showPublicMessages")));
        store.save(next);
        changed.accept(next);
        json(exchange, 200, BridgeControlJson.config(next));
    }

    private static List<OutputTargetConfig> targets(Map<String, String> form, BridgeConfig old) {
        int count = Integer.parseInt(required(form, "targetCount"));
        if (count < 0 || count > MAX_TARGETS) {
            throw new IllegalArgumentException("Zu viele Targets");
        }
        List<OutputTargetConfig> targets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String prefix = "target." + index + ".";
            String id = required(form, prefix + "id");
            String secret = form.getOrDefault(prefix + "secret", "");
            if (secret.isBlank()) {
                secret = old.targets().stream()
                        .filter(target -> target.id().equals(id))
                        .map(OutputTargetConfig::secret)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Secret fehlt für " + id));
            }
            targets.add(new OutputTargetConfig(id,
                    OutputTargetType.valueOf(required(form, prefix + "type")),
                    "on".equals(form.get(prefix + "enabled")),
                    URI.create(required(form, prefix + "endpoint")),
                    required(form, prefix + "channelId"),
                    secret));
        }
        return targets;
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

    private static Map<String, String> form(String body) {
        Map<String, String> values = new HashMap<>();
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
