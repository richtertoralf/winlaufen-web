package de.winlaufen.web.bridge.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.BridgeConfigStore;
import de.winlaufen.web.bridge.startlist.StartListStore;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.PresentationConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The competition time zone is configured in {@code bridge.properties} and has no field on the
 * Bridge Control form.
 *
 * <p>That combination is exactly where such a value gets lost: saving the form rewrites the entire
 * configuration from what the handler passes on, so anything it forgets is gone from the file. This
 * test drives a real save over HTTP and checks the file afterwards.
 */
class BridgeControlConfigZoneTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temp;

    private Path configFile;
    private BridgeConfigStore store;
    private BridgeControlServer server;
    private final AtomicReference<BridgeConfig> current = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        configFile = temp.resolve("bridge.properties");
        Files.writeString(configFile, """
                source.host=192.168.95.198
                competition.timezone=Europe/Berlin
                outputs.count=0
                """);
        store = new BridgeConfigStore(configFile);
        current.set(store.load());
        assertEquals("Europe/Berlin", current.get().competitionTimeZone());

        server = new BridgeControlServer("127.0.0.1", 0,
                new CanonicalStateStore(PresentationConfig.defaults(),
                        current.get().competitionTimeZone()),
                store, StartListStore.besideConfig(configFile),
                current::get, List::of, current::set, List::of);
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void savingTheFormKeepsTheConfiguredCompetitionTimeZone() throws Exception {
        HttpResponse<String> response = post("sourceHost=192.168.95.198&showClub=on"
                + "&showAssociation=on&showShooting=on&targetCount=0");

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("Europe/Berlin", current.get().competitionTimeZone());
        assertEquals("Europe/Berlin", store.load().competitionTimeZone());
        assertTrue(Files.readString(configFile).contains("competition.timezone=Europe/Berlin"));
    }

    /** The zone is organiser configuration, not something the public control API hands out. */
    @Test
    void theFormItselfDoesNotOfferTheZone() throws Exception {
        HttpResponse<String> page = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base() + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("sourceHost"));
        assertTrue(!page.body().contains("competition.timezone"));
    }

    /**
     * A mistyped zone is not a startup failure — every other function works — but it silently
     * shifts every time measurement by the offset between the intended and the fallback zone. On a
     * UTC host that is two hours. So it has to be visible where an organiser actually looks.
     */
    @Test
    void anUnusableZoneFallsBackAndSaysSoOnTheSurface() throws Exception {
        server.close();
        Files.writeString(configFile, """
                source.host=192.168.95.198
                competition.timezone=Europe/Berln
                outputs.count=0
                """);
        BridgeConfigStore.LoadResult loaded = new BridgeConfigStore(configFile).loadWithNotices();
        current.set(loaded.config());
        assertNull(current.get().competitionTimeZone());

        server = new BridgeControlServer("127.0.0.1", 0,
                new CanonicalStateStore(PresentationConfig.defaults(),
                        current.get().competitionTimeZone()),
                store, StartListStore.besideConfig(configFile),
                current::get, List::of, current::set, loaded::notices);
        server.start();

        JsonNode status = MAPPER.readTree(get("/api/v1/status").body());
        // The bridge runs, and it names the zone it really uses.
        assertEquals(ZoneId.systemDefault().getId(),
                status.get("competitionTimeZone").get("zone").asText());
        assertEquals("SYSTEM_DEFAULT", status.get("competitionTimeZone").get("source").asText());

        String notices = status.get("notices").toString();
        assertTrue(notices.contains("Europe/Berln"), notices);
        assertTrue(notices.contains("Zeitzone"), notices);
    }

    @Test
    void aValidZoneIsReportedAsConfiguredAndWithoutNotices() throws Exception {
        JsonNode status = MAPPER.readTree(get("/api/v1/status").body());
        assertEquals("Europe/Berlin", status.get("competitionTimeZone").get("zone").asText());
        assertEquals("CONFIGURED", status.get("competitionTimeZone").get("source").asText());
        assertEquals(0, status.get("notices").size());
    }

    /** The surface has to have somewhere to put the notice, or the status field is pointless. */
    @Test
    void theSurfaceRendersConfigNotices() throws Exception {
        assertTrue(get("/").body().contains("config-notices"));
        assertTrue(get("/assets/control.js").body().contains("showConfigNotices"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base() + "/api/v1/config"))
                        .header("Origin", base())
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://127.0.0.1:" + server.port();
    }
}
