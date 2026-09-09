package de.winlaufen.web.liveserver.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.liveserver.state.PublishedStartListStore;
import de.winlaufen.web.liveserver.state.PublishedStartListStoreTest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start list over real connections: bridge ingest into the live server, live server out to a
 * browser.
 *
 * <p>This is where the two message kinds meet. The tests pin that they stay independent — a clock
 * telegram never carries participants, and a start list never disturbs the competition state.
 */
class StartListIngestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LiveWebSocketServer server;
    private PublishedStateStore store;
    private PublishedStartListStore startLists;

    @BeforeEach
    void start() throws Exception {
        store = new PublishedStateStore("local");
        startLists = new PublishedStartListStore("local");
        server = new LiveWebSocketServer("127.0.0.1", freePort(), store, startLists, "local",
                "12345678");
        server.start();
        server.awaitStart();
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    @Test
    void aBrowserReceivesTheStartListOnConnectAndAfterEveryImport() throws Exception {
        Collector ingest = connectIngest();
        ingest.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 1, "12", "13")));
        await(() -> startLists.get().generation() == 1);

        Collector browser = connectBrowser();
        JsonNode initial = browser.next("startlist");
        assertEquals(1, initial.get("generation").asLong());
        assertEquals(2, initial.get("entries").size());
        assertEquals("12", initial.get("entries").get(0).get("bib").asText());
        assertEquals("MÜLLER", initial.get("entries").get(0).get("lastName").asText());
        assertEquals("Startliste.csv", initial.get("sourceLabel").asText());

        ingest.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 2, "99")));

        JsonNode updated = browser.next("startlist");
        assertEquals(2, updated.get("generation").asLong());
        assertEquals(1, updated.get("entries").size());
        assertEquals("99", updated.get("entries").get(0).get("bib").asText());

        browser.closeBlocking();
        ingest.closeBlocking();
    }

    @Test
    void aBrowserWithoutAnyStartListLearnsThatThereIsNone() throws Exception {
        Collector browser = connectBrowser();

        JsonNode initial = browser.next("startlist");

        assertEquals(0, initial.get("generation").asLong());
        assertEquals(0, initial.get("entries").size());
        browser.closeBlocking();
    }

    /** The whole point of the separate message: participants do not travel with the clock. */
    @Test
    void clockUpdatesReachTheBrowserWithoutAnyStartListMessage() throws Exception {
        Collector ingest = connectIngest();
        ingest.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 1, "12")));
        await(() -> startLists.get().generation() == 1);
        Collector browser = connectBrowser();
        browser.next("startlist");
        browser.drain();

        for (int second = 1; second <= 5; second++) {
            ingest.send(ContractJson.snapshot(PublishedStateStoreTest.snapshot(
                    "stream-a", second, "10:00:0" + second)));
            assertTrue(ingest.next().contains("\"type\":\"ack\""));
        }
        await(() -> "10:00:05".equals(store.get().state().clock()));
        Thread.sleep(200);

        assertEquals(0, browser.countOf("startlist"),
                "five clock telegrams must not carry the participant list");
        assertTrue(browser.countOf("snapshot") >= 1, "the state itself must keep arriving");

        browser.closeBlocking();
        ingest.closeBlocking();
    }

    @Test
    void anInvalidStartListLeavesThePublishedOneInForce() throws Exception {
        Collector ingest = connectIngest();
        ingest.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 1, "12", "13")));
        await(() -> startLists.get().generation() == 1);

        // A start list whose entries and generation contradict each other.
        ingest.send("{\"type\":\"startlist\",\"schemaVersion\":1,\"channelId\":\"local\","
                + "\"streamId\":\"stream-a\",\"generation\":0,\"source\":\"IMPORT_CSV\","
                + "\"sourceLabel\":\"x\",\"entries\":[{\"bib\":\"9\",\"className\":\"U16\","
                + "\"startTime\":\"\",\"lastName\":\"\",\"firstName\":\"\",\"club\":\"\","
                + "\"association\":\"\",\"course\":\"\",\"birthYear\":\"\",\"gender\":\"\","
                + "\"nation\":\"\"}]}");

        assertTrue(ingest.closed.await(3, TimeUnit.SECONDS),
                "an unusable message closes the connection, as it does for a snapshot");
        assertEquals(1, startLists.get().generation(), "the published start list stays in force");
        assertEquals(2, startLists.get().entries().size());
    }

    @Test
    void aStaleGenerationIsRejectedWithoutDisturbingTheStartList() throws Exception {
        Collector ingest = connectIngest();
        ingest.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 5, "12", "13")));
        await(() -> startLists.get().generation() == 5);

        ingest.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 4, "99")));

        assertTrue(ingest.closed.await(3, TimeUnit.SECONDS));
        assertEquals(5, startLists.get().generation());
        assertEquals("12", startLists.get().entries().getFirst().bib());
    }

    @Test
    void aRestartedBridgeMayPublishALowerGenerationOnANewStream() throws Exception {
        Collector first = connectIngest();
        first.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 17, "12")));
        await(() -> startLists.get().generation() == 17);
        first.closeBlocking();

        Collector restarted = connectIngest();
        restarted.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-b", 1, "99")));

        await(() -> startLists.get().generation() == 1);
        assertEquals("99", startLists.get().entries().getFirst().bib());
        assertEquals("stream-b", startLists.get().streamId());
        restarted.closeBlocking();
    }

    /** A live server restart is the reason a bridge resends on every connection. */
    @Test
    void aRestartedLiveServerGetsTheStartListBackFromTheBridgeAlone() throws Exception {
        Collector ingest = connectIngest();
        ingest.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 2, "12", "13")));
        await(() -> startLists.get().generation() == 2);
        ingest.closeBlocking();

        int reused = server.getPort();
        server.shutdown();
        store = new PublishedStateStore("local");
        startLists = new PublishedStartListStore("local");
        server = new LiveWebSocketServer("127.0.0.1", reused, store, startLists, "local",
                "12345678");
        server.start();
        server.awaitStart();
        assertFalse(startLists.get().present(), "a fresh run starts without a start list");

        Collector reconnected = connectIngest();
        reconnected.send(ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 2, "12", "13")));

        await(() -> startLists.get().generation() == 2);
        assertEquals(2, startLists.get().entries().size());
        reconnected.closeBlocking();
    }

    @Test
    void aRealisticEventSizePassesThroughToTheBrowser() throws Exception {
        Collector ingest = connectIngest();
        String[] bibs = new String[1999];
        for (int index = 0; index < bibs.length; index++) {
            bibs[index] = Integer.toString(index + 1);
        }
        String wire = ContractJson.startList(
                PublishedStartListStoreTest.envelope("stream-a", 1, bibs));

        ingest.send(wire);
        await(() -> startLists.get().entries().size() == 1999);
        Collector browser = connectBrowser();

        JsonNode received = browser.next("startlist");
        assertEquals(1999, received.get("entries").size());
        assertEquals("1", received.get("entries").get(0).get("bib").asText());
        assertEquals("1999", received.get("entries").get(1998).get("bib").asText());

        browser.closeBlocking();
        ingest.closeBlocking();
    }

    // --- Helpers -------------------------------------------------------------------------------

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

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("condition not reached within 5 s");
    }

    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static final class Collector extends WebSocketClient {

        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final java.util.concurrent.CountDownLatch closed = new java.util.concurrent.CountDownLatch(1);
        private final List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();

        Collector(URI uri, Map<String, String> headers) {
            super(uri, headers);
        }

        @Override
        public void onOpen(ServerHandshake handshake) { }

        @Override
        public void onMessage(String message) {
            messages.add(message);
            seen.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closed.countDown();
        }

        @Override
        public void onError(Exception ex) { }

        String next() throws InterruptedException {
            String value = messages.poll(3, TimeUnit.SECONDS);
            assertNotNull(value);
            return value;
        }

        JsonNode next(String type) throws Exception {
            for (int attempt = 0; attempt < 20; attempt++) {
                JsonNode parsed = MAPPER.readTree(next());
                if (type.equals(parsed.get("type").asText())) {
                    return parsed;
                }
            }
            throw new AssertionError("no message of type " + type);
        }

        /** Forgets everything received so far, so a later count starts from zero. */
        void drain() {
            messages.clear();
            seen.clear();
        }

        long countOf(String type) throws Exception {
            long count = 0;
            for (String message : seen) {
                if (type.equals(MAPPER.readTree(message).get("type").asText())) {
                    count++;
                }
            }
            return count;
        }
    }
}
