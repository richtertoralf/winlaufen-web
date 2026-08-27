package de.winlaufen.web.liveserver.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.winlaufen.web.liveserver.state.PublishedStateStore;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Public read-only HTTP surface: the web viewer, the published state and the browser WebSocket
 * endpoint hint. It never exposes bridge configuration, target data or credentials.
 */
public final class PublicHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final PublishedStateStore store;
    private final int webSocketPort;

    public PublicHttpServer(String bind, int port, int webSocketPort, PublishedStateStore store)
            throws IOException {
        this.store = store;
        this.webSocketPort = webSocketPort;
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
            if (!"GET".equals(exchange.getRequestMethod())) {
                text(exchange, 405, "Methode nicht erlaubt");
                return;
            }
            switch (exchange.getRequestURI().getPath()) {
                case "/", "/viewer" -> resource(exchange, "/web-viewer/viewer.html", "text/html; charset=utf-8");
                case "/renderer" -> {
                    // Compatibility route of the pre-modular renderer URL.
                    exchange.getResponseHeaders().set("Location", "/");
                    exchange.sendResponseHeaders(302, -1);
                }
                case "/assets/viewer.css" -> resource(exchange, "/web-viewer/viewer.css", "text/css; charset=utf-8");
                case "/assets/viewer.js" -> resource(exchange, "/web-viewer/viewer.js", "text/javascript; charset=utf-8");
                case "/api/v1/state" -> json(exchange, PublicJson.state(store.get()));
                case "/api/v1/runtime" -> json(exchange, PublicJson.runtime(webSocketPort));
                default -> text(exchange, 404, "Nicht gefunden");
            }
        } finally {
            exchange.close();
        }
    }

    private static void resource(HttpExchange exchange, String name, String type) throws IOException {
        try (InputStream input = PublicHttpServer.class.getResourceAsStream(name)) {
            if (input == null) {
                text(exchange, 404, "Nicht gefunden");
                return;
            }
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static void json(HttpExchange exchange, String value) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        bytes(exchange, 200, value);
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
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
