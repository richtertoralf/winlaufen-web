package de.winlaufen.web.liveserver.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.ContractLimits;
import de.winlaufen.web.liveserver.state.PublishedStateStore;
import de.winlaufen.web.liveserver.state.PublishedStateStoreTest;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveWebSocketServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LiveWebSocketServer server;
    private PublishedStateStore store;
    private int port;

    @BeforeEach
    void start() throws Exception {
        port = freePort();
        store = new PublishedStateStore("local");
        server = new LiveWebSocketServer("127.0.0.1", port, store, "local", "12345678");
        server.start();
        server.awaitStart();
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    @Test
    void separatesAuthenticatedIngestFromOriginCheckedBrowserAndAcksAfterStore() throws Exception {
        Collector browser = connectBrowser();
        assertTrue(browser.next().contains("\"publicationRevision\":0"));

        Collector ingest = connectIngest();
        ingest.send(ContractJson.snapshot(PublishedStateStoreTest.snapshot("stream", 3, "11:22:33")));
        assertTrue(ingest.next().contains("\"type\":\"ack\""));
        assertEquals("11:22:33", store.get().state().clock());
        assertTrue(browser.next().contains("11:22:33"));

        browser.closeBlocking();
        ingest.closeBlocking();
    }

    @Test
    void rejectsWrongSecretAndForeignBrowserOrigin() throws Exception {
        assertFalse(connects(new URI("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"),
                Map.of("Authorization", "Bearer wrong-secret")));
        assertFalse(connects(new URI("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"),
                Map.of()));
        assertFalse(connects(new URI("ws://127.0.0.1:" + port + "/live/v1"),
                Map.of("Origin", "http://evil.test")));
        assertFalse(connects(new URI("ws://127.0.0.1:" + port + "/live/v1"), Map.of()));
        assertFalse(connects(new URI("ws://127.0.0.1:" + port + "/unknown"),
                Map.of("Origin", "http://127.0.0.1:44440")));
        assertFalse(connects(new URI("ws://127.0.0.1:" + port + "/bridge/v1/channels/other"),
                Map.of("Authorization", "Bearer 12345678")));
    }

    @Test
    void multipleBrowsersEachReceiveTheInitialSnapshotAndEveryUpdate() throws Exception {
        Collector one = connectBrowser();
        Collector two = connectBrowser();
        Collector three = connectBrowser();
        for (Collector browser : new Collector[]{one, two, three}) {
            JsonNode initial = MAPPER.readTree(browser.next());
            assertEquals(0, initial.get("publicationRevision").asLong());
        }

        Collector ingest = connectIngest();
        ingest.send(ContractJson.snapshot(PublishedStateStoreTest.snapshot("stream", 1, "09:00:01")));
        assertTrue(ingest.next().contains("\"type\":\"ack\""));
        ingest.send(ContractJson.snapshot(PublishedStateStoreTest.snapshot("stream", 2, "09:00:02")));
        assertTrue(ingest.next().contains("\"type\":\"ack\""));

        for (Collector browser : new Collector[]{one, two, three}) {
            assertEquals("09:00:01", clockOf(browser.next()));
            assertEquals("09:00:02", clockOf(browser.next()));
        }

        // One browser leaving must not disturb the others or the ingest connection.
        two.closeBlocking();
        ingest.send(ContractJson.snapshot(PublishedStateStoreTest.snapshot("stream", 3, "09:00:03")));
        assertTrue(ingest.next().contains("\"type\":\"ack\""));
        assertEquals("09:00:03", clockOf(one.next()));
        assertEquals("09:00:03", clockOf(three.next()));

        one.closeBlocking();
        three.closeBlocking();
        ingest.closeBlocking();
    }

    @Test
    void browserNeverSeesADecreasingPublicationRevision() throws Exception {
        Collector browser = connectBrowser();
        assertNotNull(browser.next());
        Collector ingest = connectIngest();

        long previous = -1;
        for (int revision = 1; revision <= 12; revision++) {
            // Alternating stream ids force the live server to restart the source revision.
            String stream = revision % 4 == 0 ? "b" : "a";
            ingest.send(ContractJson.snapshot(
                    PublishedStateStoreTest.snapshot(stream, revision, "08:00:" + String.format("%02d", revision))));
            assertTrue(ingest.next().contains("\"type\":\"ack\""));
        }
        String message;
        while ((message = browser.messages.poll(1, TimeUnit.SECONDS)) != null) {
            JsonNode parsed = MAPPER.readTree(message);
            if ("heartbeat".equals(parsed.get("type").asText())) {
                assertFalse(parsed.has("publicationRevision"), "a sign of life carries no state");
                continue;
            }
            long current = parsed.get("publicationRevision").asLong();
            assertTrue(current > previous,
                    "publicationRevision must strictly increase per browser, got "
                            + current + " after " + previous);
            previous = current;
        }
        assertTrue(previous > 0, "the browser must have received updates");

        browser.closeBlocking();
        ingest.closeBlocking();
    }

    /**
     * The regression behind the frozen viewer: a restarted live server starts its publication
     * revision at 0 again. A browser that kept the counter of the previous run would discard every
     * snapshot of the new run, so the viewer must reset the guard for each connection.
     */
    @Test
    void aRestartedLiveServerStartsItsPublicationRevisionAtZeroAgain() throws Exception {
        Collector ingest = connectIngest();
        for (int revision = 1; revision <= 3; revision++) {
            ingest.send(ContractJson.snapshot(
                    PublishedStateStoreTest.snapshot("stream", revision, "08:00:0" + revision)));
            assertTrue(ingest.next().contains("\"type\":\"ack\""));
        }
        assertEquals(3, store.get().publicationRevision());
        ingest.closeBlocking();

        int reused = server.getPort();
        server.shutdown();
        server = new LiveWebSocketServer("127.0.0.1", reused, new PublishedStateStore("local"),
                "local", "12345678");
        server.start();
        server.awaitStart();

        Collector browser = connectBrowser();
        JsonNode first = MAPPER.readTree(browser.next());
        assertEquals("snapshot", first.get("type").asText());
        assertEquals(0, first.get("publicationRevision").asLong(),
                "the new run knows nothing of the previous counter");
        browser.closeBlocking();
    }

    @Test
    void browsersGetASignOfLifeAndIngestConnectionsDoNot() throws Exception {
        int fastPort = freePort();
        var fastStore = new PublishedStateStore("local");
        var fast = new LiveWebSocketServer("127.0.0.1", fastPort, fastStore, "local", "12345678",
                ContractLimits.MAX_INGEST_MESSAGE_BYTES, ContractLimits.MAX_BROWSER_MESSAGE_BYTES,
                80);
        fast.start();
        fast.awaitStart();
        try {
            Collector browser = new Collector(new URI("ws://127.0.0.1:" + fastPort + "/live/v1"),
                    Map.of("Origin", "http://127.0.0.1:44440"));
            assertTrue(browser.connectBlocking(3, TimeUnit.SECONDS));
            Collector ingest = new Collector(
                    new URI("ws://127.0.0.1:" + fastPort + "/bridge/v1/channels/local"),
                    Map.of("Authorization", "Bearer 12345678"));
            assertTrue(ingest.connectBlocking(3, TimeUnit.SECONDS));

            assertEquals("snapshot", MAPPER.readTree(browser.next()).get("type").asText(),
                    "the first message of a connection stays the full snapshot");
            for (int index = 0; index < 3; index++) {
                assertEquals("heartbeat", MAPPER.readTree(browser.next()).get("type").asText(),
                        "the browser link keeps getting a sign of life while nothing changes");
            }
            assertNull(ingest.messages.poll(300, TimeUnit.MILLISECONDS),
                    "the ingest connection has its own ACK round trip and gets no keepalive");

            browser.closeBlocking();
            ingest.closeBlocking();
        } finally {
            fast.shutdown();
        }
    }

    /**
     * Without this the live server would keep serving CONNECTED with a frozen WinLaufen clock for
     * as long as it runs, and every spectator would believe the data is current.
     */
    @Test
    void aBrowserLearnsThatTheBridgeIsGoneAndKeepsTheLastCompetitionTime() throws Exception {
        Collector browser = connectBrowser();
        assertNotNull(browser.next());
        Collector ingest = connectIngest();
        ingest.send(ContractJson.snapshot(
                PublishedStateStoreTest.snapshot("stream", 1, "10:07:41")));
        assertTrue(ingest.next().contains("\"type\":\"ack\""));

        JsonNode live = nextSnapshot(browser);
        assertEquals("CONNECTED", live.get("state").get("health").asText());
        assertEquals("10:07:41", live.get("state").get("clock").asText());

        ingest.closeBlocking();

        JsonNode degraded = nextSnapshot(browser);
        assertEquals("DISCONNECTED", degraded.get("state").get("health").asText(),
                "a vanished bridge must not stay published as a connected source");
        assertEquals("10:07:41", degraded.get("state").get("clock").asText(),
                "the last competition time stays exactly as WinLaufen delivered it");
        assertTrue(degraded.get("publicationRevision").asLong()
                        > live.get("publicationRevision").asLong(),
                "the browser receives it as a normal published state");

        browser.closeBlocking();
    }

    /** The whole restart sequence as a browser sees it, over real WebSocket connections. */
    @Test
    void anOpenBrowserKeepsItsResultsWhileTheBridgeRestarts() throws Exception {
        Collector browser = connectBrowser();
        assertNotNull(browser.next());

        Collector ingest = connectIngest();
        ingest.send(ContractJson.snapshot(PublishedStateStoreTest.results(
                "stream-1", 7, "10:07:41", "Lauf", List.of("Meier", "Schulz"))));
        assertTrue(ingest.next().contains("\"type\":\"ack\""));
        assertEquals(2, rowCount(nextSnapshot(browser)));

        ingest.closeBlocking();
        JsonNode gone = nextSnapshot(browser);
        assertEquals("DISCONNECTED", gone.get("state").get("health").asText());
        assertEquals(2, rowCount(gone), "a vanished bridge never clears the table");
        assertEquals("10:07:41", gone.get("state").get("clock").asText());

        // Neu gestartete Bridge: neuer Stream, Uhr und Health, aber noch keine Klassendaten.
        Collector restarted = connectIngest();
        restarted.send(ContractJson.snapshot(
                PublishedStateStoreTest.snapshot("stream-2", 0, "10:09:12")));
        assertTrue(restarted.next().contains("\"type\":\"ack\""));

        JsonNode back = nextSnapshot(browser);
        assertEquals("CONNECTED", back.get("state").get("health").asText());
        assertEquals("10:09:12", back.get("state").get("clock").asText(),
                "the competition time is exactly the newly delivered one");
        assertEquals(2, rowCount(back), "the results a speaker is reading stay on screen");

        restarted.send(ContractJson.snapshot(PublishedStateStoreTest.results(
                "stream-2", 1, "10:09:13", "Lauf", List.of("Meier", "Schulz", "Weber"))));
        assertTrue(restarted.next().contains("\"type\":\"ack\""));
        assertEquals(3, rowCount(nextSnapshot(browser)),
                "the next authoritative standing replaces the table as usual");

        browser.closeBlocking();
        restarted.closeBlocking();
    }

    @Test
    void cleanShutdownAllowsImmediateRebindOnTheSamePort() throws Exception {
        assertTrue(server.isReuseAddr());
        int reused = server.getPort();
        server.shutdown();

        server = new LiveWebSocketServer("127.0.0.1", reused, new PublishedStateStore("local"),
                "local", "12345678");
        server.start();
        server.awaitStart();
        assertEquals(reused, server.getPort());

        Collector browser = connectBrowser();
        assertTrue(browser.next().contains("\"publicationRevision\":0"));
        browser.closeBlocking();
    }

    private static int rowCount(JsonNode message) {
        JsonNode competition = message.get("state").get("competition");
        assertFalse(competition.isNull(), "a competition is expected in this message");
        JsonNode snapshot = competition.get("classes").get(0).get("snapshot");
        return snapshot.isNull() ? 0 : snapshot.get("rows").size();
    }

    /** Skips the technical sign of life, which never carries competition state. */
    private JsonNode nextSnapshot(Collector collector) throws Exception {
        for (;;) {
            JsonNode parsed = MAPPER.readTree(collector.next());
            if (!"heartbeat".equals(parsed.get("type").asText())) {
                return parsed;
            }
        }
    }

    private String clockOf(String message) throws Exception {
        return MAPPER.readTree(message).get("state").get("clock").asText();
    }

    private Collector connectBrowser() throws Exception {
        Collector collector = new Collector(new URI("ws://127.0.0.1:" + server.getPort() + "/live/v1"),
                Map.of("Origin", "http://127.0.0.1:44440"));
        assertTrue(collector.connectBlocking(3, TimeUnit.SECONDS));
        return collector;
    }

    private Collector connectIngest() throws Exception {
        Collector collector = new Collector(
                new URI("ws://127.0.0.1:" + server.getPort() + "/bridge/v1/channels/local"),
                Map.of("Authorization", "Bearer 12345678"));
        assertTrue(collector.connectBlocking(3, TimeUnit.SECONDS));
        return collector;
    }

    private static boolean connects(URI uri, Map<String, String> headers) {
        try {
            Collector collector = new Collector(uri, headers);
            boolean open = collector.connectBlocking(1, TimeUnit.SECONDS);
            collector.close();
            return open;
        } catch (Exception ex) {
            return false;
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static final class Collector extends WebSocketClient {

        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        Collector(URI uri, Map<String, String> headers) {
            super(uri, headers);
        }

        @Override
        public void onOpen(ServerHandshake handshake) { }

        @Override
        public void onMessage(String message) {
            messages.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) { }

        @Override
        public void onError(Exception ex) { }

        String next() throws InterruptedException {
            String value = messages.poll(3, TimeUnit.SECONDS);
            assertNotNull(value);
            return value;
        }
    }
}
