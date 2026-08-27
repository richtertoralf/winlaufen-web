package de.winlaufen.web.web;

import de.winlaufen.web.config.AppConfig;
import de.winlaufen.web.config.ConfigStore;
import de.winlaufen.web.config.PublicDisplayConfig;
import de.winlaufen.web.json.JsonSyntax;
import de.winlaufen.web.model.OutputMode;
import de.winlaufen.web.state.StateStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HttpAppServerTest {
    @TempDir Path temp;
    HttpAppServer server;
    HttpClient client = HttpClient.newHttpClient();
    AtomicReference<AppConfig> config = new AtomicReference<>(new AppConfig("localhost", OutputMode.LOCAL, 8080, 8081));
    ConfigStore configStore;

    @BeforeEach void start() throws Exception {
        configStore = new ConfigStore(temp.resolve("config.properties"));
        server = new HttpAppServer(0, new StateStore(), configStore, config::get, config::set); server.start();
    }
    @AfterEach void stop() { server.close(); }

    @Test void servesStateHealthAndStaticResources() throws Exception {
        assertBody("/api/v1/state", "\"revision\":0");
        assertBody("/api/v1/health", "DISCONNECTED");
        assertBody("/", "WinLaufen Web");
        assertBody("/dashboard", "Öffentliche Darstellung");
        assertBody("/renderer", "Startliste");
        assertBody("/assets/app.css", "--green");
        assertBody("/assets/dashboard.js", "Speichern fehlgeschlagen");
        assertBody("/assets/renderer.js", "message.type === 'snapshot' || message.type === 'classSnapshot'");
        assertBody("/assets/renderer.js", "header === 'Schießen'");
        assertValidJson("/api/v1/config");
        assertValidJson("/api/v1/state");
        assertValidJson("/api/v1/health");
    }

    @Test void configRequiresValidOriginAndRejectsDisabledMode() throws Exception {
        var foreign = post("winlaufenHost=host&outputMode=LOCAL", "http://evil.test:8080");
        assertEquals(403, foreign.statusCode());
        var disabled = post("winlaufenHost=host&outputMode=SELFHOST", origin());
        assertEquals(400, disabled.statusCode());
        var valid = post("winlaufenHost=192.168.1.2&outputMode=LOCAL&showAssociation=on&showNation=on&showPublicMessages=on", origin());
        assertEquals(200, valid.statusCode());
        JsonSyntax.parse(valid.body());
        assertEquals("192.168.1.2", config.get().winLaufenHost());
        assertEquals(new PublicDisplayConfig(false, true, true, false, true), config.get().publicDisplay());
        assertEquals(config.get(), configStore.load());
    }

    @Test void configGetContainsPublicDisplayDefaults() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/config")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"showClub\":true"));
        assertTrue(response.body().contains("\"showAssociation\":true"));
        assertTrue(response.body().contains("\"showNation\":false"));
        assertTrue(response.body().contains("\"showShooting\":true"));
        assertTrue(response.body().contains("\"showPublicMessages\":false"));
    }

    private void assertBody(String path, String expected) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); assertTrue(response.body().contains(expected));
    }
    private void assertValidJson(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        JsonSyntax.parse(response.body());
    }
    private HttpResponse<String> post(String body, String origin) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/config"))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Origin", origin)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String base() { return "http://localhost:" + server.port(); }
    private String origin() { return base(); }
}
