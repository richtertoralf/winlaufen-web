package de.winlaufen.web.liveserver.web;

import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.ContractLimits;
import de.winlaufen.web.liveserver.state.PublishedStateStore;
import de.winlaufen.web.liveserver.state.PublishedStateStoreTest;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.HandshakeState;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incoming WebSocket messages are limited before they can be assembled on the heap.
 *
 * <p>The limits are injected here so the regression is proven without allocating a
 * production-sized payload in the normal test suite.
 */
class WebSocketMessageLimitTest {

    private static final int INGEST_LIMIT = 4_096;
    private static final int BROWSER_LIMIT = 256;

    private LiveWebSocketServer server;
    private PublishedStateStore store;
    private int port;

    @BeforeEach
    void start() throws Exception {
        port = freePort();
        store = new PublishedStateStore("local");
        server = new LiveWebSocketServer("127.0.0.1", port, store, "local", "12345678",
                INGEST_LIMIT, BROWSER_LIMIT);
        server.start();
        server.awaitStart();
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    @Test
    void productionLimitsAreDerivedFromTheContract() {
        var ingest = new SizeLimitedDraft("/bridge/v1/channels/local", true,
                ContractLimits.MAX_INGEST_MESSAGE_BYTES);
        var browser = new SizeLimitedDraft("/bridge/v1/channels/local", false,
                ContractLimits.MAX_BROWSER_MESSAGE_BYTES);

        assertEquals(ContractLimits.MAX_INGEST_MESSAGE_BYTES, ingest.getMaxFrameSize());
        assertEquals(ContractLimits.MAX_BROWSER_MESSAGE_BYTES, browser.getMaxFrameSize());
        assertTrue(ingest.getMaxFrameSize() >= ContractLimits.MAX_JSON_CHARS,
                "a valid snapshot must never be truncated by the transport limit");
        assertTrue(browser.getMaxFrameSize() < ingest.getMaxFrameSize());
    }

    @Test
    void draftsSelectThemselvesByRequestPath() throws Exception {
        var ingest = new SizeLimitedDraft("/bridge/v1/channels/local", true, INGEST_LIMIT);
        var browser = new SizeLimitedDraft("/bridge/v1/channels/local", false, BROWSER_LIMIT);

        assertEquals(HandshakeState.NOT_MATCHED,
                ingest.acceptHandshakeAsServer(handshake(LiveWebSocketServer.BROWSER_PATH)));
        assertEquals(HandshakeState.NOT_MATCHED,
                browser.acceptHandshakeAsServer(handshake("/bridge/v1/channels/local")));
        assertEquals(HandshakeState.MATCHED,
                ingest.acceptHandshakeAsServer(handshake("/bridge/v1/channels/local")));
        assertEquals(HandshakeState.MATCHED,
                browser.acceptHandshakeAsServer(handshake(LiveWebSocketServer.BROWSER_PATH)));
    }

    @Test
    void validSnapshotIsAcceptedAndOversizedIngestMessageIsRejected() throws Exception {
        Collector ingest = connectIngest();
        ingest.send(ContractJson.snapshot(PublishedStateStoreTest.snapshot("stream", 1, "10:00:00")));
        assertTrue(ingest.next().contains("\"type\":\"ack\""));
        assertEquals("10:00:00", store.get().state().clock());

        ingest.send("x".repeat(INGEST_LIMIT * 2));
        assertTrue(ingest.closed.await(3, TimeUnit.SECONDS), "oversized ingest message must close");
        assertEquals(CloseFrame.TOOBIG, ingest.closeCode,
                "the transport limit must reject the frame, not the snapshot parser");
        assertEquals("10:00:00", store.get().state().clock(),
                "the rejected message must not change the published state");
    }

    @Test
    void oversizedBrowserMessageIsRejectedAndTheServerStaysUsable() throws Exception {
        Collector browser = connectBrowser();
        assertNotNull(browser.next());

        browser.send("x".repeat(BROWSER_LIMIT * 4));
        assertTrue(browser.closed.await(3, TimeUnit.SECONDS), "oversized browser message must close");
        assertEquals(CloseFrame.TOOBIG, browser.closeCode,
                "the frame must be refused by the transport limit before it is assembled");

        // The server must still serve other clients and still accept bridge snapshots.
        Collector second = connectBrowser();
        assertTrue(second.next().contains("\"publicationRevision\""));
        Collector ingest = connectIngest();
        ingest.send(ContractJson.snapshot(PublishedStateStoreTest.snapshot("stream", 2, "11:11:11")));
        assertTrue(ingest.next().contains("\"type\":\"ack\""));
        assertTrue(second.next().contains("11:11:11"));
        second.closeBlocking();
        ingest.closeBlocking();
    }

    @Test
    void smallBrowserMessagesAreRefusedByTheReadOnlyRule() throws Exception {
        Collector browser = connectBrowser();
        assertNotNull(browser.next());

        // Below the transport limit, so the read-only rule is what rejects it.
        browser.send("hi");
        assertTrue(browser.closed.await(3, TimeUnit.SECONDS));
        assertEquals(CloseFrame.REFUSE, browser.closeCode, "browser connections are read-only");
        assertEquals(0, store.get().publicationRevision());
    }

    @Test
    void browserSnapshotsNeverReachThePublishedState() throws Exception {
        Collector browser = connectBrowser();
        assertNotNull(browser.next());

        String forged = ContractJson.snapshot(PublishedStateStoreTest.snapshot("evil", 99, "00:00:00"));
        browser.send(forged);
        assertTrue(browser.closed.await(3, TimeUnit.SECONDS));
        assertFalse("00:00:00".equals(store.get().state().clock()));
        assertEquals(0, store.get().publicationRevision(),
                "a browser must never be able to publish state");
    }

    private Collector connectBrowser() throws Exception {
        Collector collector = new Collector(new URI("ws://127.0.0.1:" + port + "/live/v1"),
                Map.of("Origin", "http://127.0.0.1:8080"));
        assertTrue(collector.connectBlocking(3, TimeUnit.SECONDS));
        return collector;
    }

    private Collector connectIngest() throws Exception {
        Collector collector = new Collector(
                new URI("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"),
                Map.of("Authorization", "Bearer 12345678"));
        assertTrue(collector.connectBlocking(3, TimeUnit.SECONDS));
        return collector;
    }

    private static org.java_websocket.handshake.ClientHandshake handshake(String path) {
        var request = new org.java_websocket.handshake.HandshakeImpl1Client();
        request.setResourceDescriptor(path);
        request.put("Upgrade", "websocket");
        request.put("Connection", "Upgrade");
        request.put("Sec-WebSocket-Version", "13");
        request.put("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==");
        return request;
    }

    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static final class Collector extends WebSocketClient {

        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CountDownLatch closed = new CountDownLatch(1);
        volatile int closeCode;

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
        public void onClose(int code, String reason, boolean remote) {
            closeCode = code;
            closed.countDown();
        }

        @Override
        public void onError(Exception ex) { }

        String next() throws InterruptedException {
            String value = messages.poll(3, TimeUnit.SECONDS);
            assertNotNull(value);
            return value;
        }
    }
}
