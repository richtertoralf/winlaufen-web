package de.winlaufen.web.liveserver.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.liveserver.state.PublishedStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicHttpServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PublicHttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = new PublicHttpServer("127.0.0.1", 0, 8081, new PublishedStateStore("local"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void servesOnlyPublicStateRuntimeAndViewer() throws Exception {
        assertBody("/", "Startliste");
        assertBody("/viewer", "Startliste");
        assertBody("/assets/viewer.js", "/api/v1/state");
        assertBody("/assets/viewer.css", "sticky");
        assertBody("/api/v1/state", "publicationRevision");
        assertBody("/api/v1/runtime", "/live/v1");

        assertEquals(404, get("/api/v1/config").statusCode());
        assertEquals(404, get("/api/v1/status").statusCode());
        assertEquals(404, get("/dashboard").statusCode());

        String script = get("/assets/viewer.js").body();
        assertFalse(script.contains("BridgeConfig"));
        assertFalse(script.contains("/bridge/"));
    }

    @Test
    void jsonEndpointsReturnParseableJson() throws Exception {
        MAPPER.readTree(get("/api/v1/state").body());
        MAPPER.readTree(get("/api/v1/runtime").body());
    }

    @Test
    void keepsTheRendererCompatibilityRedirect() throws Exception {
        HttpResponse<String> response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build()
                .send(request("/renderer"), HttpResponse.BodyHandlers.ofString());
        assertEquals(302, response.statusCode());
        assertEquals("/", response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void rejectsNonGetMethods() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base() + "/api/v1/state"))
                        .POST(HttpRequest.BodyPublishers.ofString("x")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }

    @Test
    void cleanShutdownAllowsImmediateRebindOnTheSamePort() throws Exception {
        int reused = freePort();
        PublicHttpServer first = new PublicHttpServer("127.0.0.1", reused, 8081,
                new PublishedStateStore("local"));
        first.start();
        assertEquals(reused, first.port());
        first.close();

        PublicHttpServer second = new PublicHttpServer("127.0.0.1", reused, 8081,
                new PublishedStateStore("local"));
        second.start();
        assertEquals(reused, second.port());
        second.close();
    }

    private void assertBody(String path, String expected) throws Exception {
        HttpResponse<String> response = get(path);
        assertEquals(200, response.statusCode(), path);
        assertTrue(response.body().contains(expected), path + " should contain " + expected);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(request(path), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create(base() + path)).GET().build();
    }

    private String base() {
        return "http://127.0.0.1:" + server.port();
    }

    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }
}
