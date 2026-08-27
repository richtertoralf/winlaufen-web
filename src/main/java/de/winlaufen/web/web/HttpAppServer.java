package de.winlaufen.web.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.winlaufen.web.config.AppConfig;
import de.winlaufen.web.config.ConfigStore;
import de.winlaufen.web.config.PublicDisplayConfig;
import de.winlaufen.web.json.Json;
import de.winlaufen.web.model.OutputMode;
import de.winlaufen.web.state.StateStore;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class HttpAppServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final StateStore state;
    private final ConfigStore configs;
    private final Supplier<AppConfig> currentConfig;
    private final Consumer<AppConfig> configChanged;

    public HttpAppServer(int port, StateStore state, ConfigStore configs,
                         Supplier<AppConfig> currentConfig, Consumer<AppConfig> configChanged) throws IOException {
        this.state = state;
        this.configs = configs;
        this.currentConfig = currentConfig;
        this.configChanged = configChanged;
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod())) {
                switch (path) {
                    case "/", "/dashboard" -> resource(exchange, "/web/dashboard.html", "text/html; charset=utf-8");
                    case "/renderer" -> resource(exchange, "/web/renderer.html", "text/html; charset=utf-8");
                    case "/assets/app.css" -> resource(exchange, "/web/app.css", "text/css; charset=utf-8");
                    case "/assets/dashboard.js" -> resource(exchange, "/web/dashboard.js", "text/javascript; charset=utf-8");
                    case "/assets/renderer.js" -> resource(exchange, "/web/renderer.js", "text/javascript; charset=utf-8");
                    case "/api/v1/state" -> json(exchange, 200, Json.state(state.get()));
                    case "/api/v1/health" -> json(exchange, 200, "{\"health\":" + Json.quote(state.get().health().name())
                            + ",\"clock\":" + (state.get().clock() == null ? "null" : Json.quote(state.get().clock())) + "}");
                    case "/api/v1/config" -> json(exchange, 200, Json.config(currentConfig.get()));
                    default -> text(exchange, 404, "Nicht gefunden");
                }
            } else if ("POST".equals(exchange.getRequestMethod()) && "/api/v1/config".equals(path)) {
                updateConfig(exchange);
            } else {
                exchange.getResponseHeaders().set("Allow", "/api/v1/config".equals(path) ? "GET, POST" : "GET");
                text(exchange, 405, "Methode nicht erlaubt");
            }
        } catch (IllegalArgumentException ex) { json(exchange, 400, "{\"error\":" + Json.quote(ex.getMessage()) + "}"); }
        catch (Exception ex) { json(exchange, 500, "{\"error\":\"Interner Fehler\"}"); }
        finally { exchange.close(); }
    }

    private void updateConfig(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            text(exchange, 415, "application/x-www-form-urlencoded erforderlich"); return;
        }
        if (!OriginPolicy.accepts(exchange.getRequestHeaders().getFirst("Origin"), exchange.getRequestHeaders().getFirst("Host"))) {
            text(exchange, 403, "Origin abgelehnt"); return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(8_193);
        if (body.length > 8_192) { text(exchange, 413, "Anfrage zu groß"); return; }
        Map<String, String> form = form(new String(body, StandardCharsets.UTF_8));
        AppConfig old = currentConfig.get();
        String host = ConfigStore.validateHost(required(form, "winlaufenHost"));
        OutputMode mode = OutputMode.valueOf(required(form, "outputMode"));
        if (!mode.enabled()) throw new IllegalArgumentException("Dieser Output-Modus ist in v0.1 deaktiviert");
        PublicDisplayConfig display = new PublicDisplayConfig(checked(form, "showClub"),
                checked(form, "showAssociation"), checked(form, "showNation"),
                checked(form, "showShooting"), checked(form, "showPublicMessages"));
        AppConfig next = new AppConfig(host, mode, old.httpPort(), old.webSocketPort(), display);
        configs.save(next);
        configChanged.accept(next);
        json(exchange, 200, Json.config(next));
    }

    private static Map<String, String> form(String body) {
        Map<String, String> result = new HashMap<>();
        for (String field : body.split("&")) {
            String[] pair = field.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private static String required(Map<String, String> form, String name) {
        String value = form.get(name);
        if (value == null) throw new IllegalArgumentException("Feld fehlt: " + name);
        return value;
    }

    private static boolean checked(Map<String, String> form, String name) {
        return "on".equals(form.get(name));
    }

    private static void resource(HttpExchange exchange, String name, String contentType) throws IOException {
        try (InputStream input = HttpAppServer.class.getResourceAsStream(name)) {
            if (input == null) { text(exchange, 404, "Nicht gefunden"); return; }
            byte[] body = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        bytes(exchange, status, body);
    }
    private static void text(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8"); bytes(exchange, status, body);
    }
    private static void bytes(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
    }

    @Override public void close() {
        server.stop(0);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
