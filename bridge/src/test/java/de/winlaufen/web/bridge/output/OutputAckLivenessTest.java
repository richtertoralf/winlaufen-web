package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.AckEnvelope;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.PresentationConfig;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A target that completes the handshake but stops confirming snapshots must not keep being
 * reported as healthy, and it must never queue more than the newest snapshot.
 */
class OutputAckLivenessTest {

    /** Short ACK grace period so the regression is proven without a slow test. */
    private static final long STALE_AFTER_NANOS = 800_000_000L;

    @Test
    void silentTargetBecomesStaleAndRecoversWhenAcksResume() throws Exception {
        Silent server = new Silent();
        server.startReady();
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        WebSocketOutputAdapter adapter = new WebSocketOutputAdapter(target(server.getPort()),
                "stream", store.get(), STALE_AFTER_NANOS);
        AutoCloseable subscription = store.addListener(adapter::publish);
        try {
            adapter.start();
            store.clock("10:00:00");
            await(() -> server.received.get() >= 1, 5_000);
            await(() -> adapter.runtime().state() == OutputConnectionState.STALE, 6_000);
            assertEquals(-1, adapter.runtime().lastAckedSourceRevision(),
                    "nothing was ever confirmed by the silent target");
            assertTrue(adapter.runtime().lastError().contains("ACK"));

            server.acknowledge.set(true);
            store.clock("10:00:01");
            await(() -> adapter.runtime().state() == OutputConnectionState.CONNECTED, 6_000);
            assertTrue(adapter.runtime().lastAckedSourceRevision() >= 1);
        } finally {
            adapter.close();
            subscription.close();
            server.stop(500);
        }
    }

    @Test
    void neverQueuesMoreThanTheNewestSnapshotWhileUnconfirmed() throws Exception {
        Silent server = new Silent();
        server.startReady();
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        WebSocketOutputAdapter adapter = new WebSocketOutputAdapter(target(server.getPort()),
                "stream", store.get(), WebSocketOutputAdapter.ACK_STALE_NANOS);
        AutoCloseable subscription = store.addListener(adapter::publish);
        try {
            adapter.start();
            await(() -> adapter.runtime().state() == OutputConnectionState.CONNECTED, 5_000);
            long baseline = server.received.get();
            for (int index = 0; index < 200; index++) {
                store.clock(String.format("11:%02d:%02d", index / 60, index % 60));
            }
            Thread.sleep(1_500);
            long delivered = server.received.get() - baseline;
            // Coalescing: 200 canonical revisions must not produce 200 outgoing snapshots.
            assertTrue(delivered <= 10,
                    "expected coalesced delivery, but " + delivered + " snapshots were sent");
        } finally {
            adapter.close();
            subscription.close();
            server.stop(500);
        }
    }

    private static OutputTargetConfig target(int port) {
        return new OutputTargetConfig("silent", OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"),
                "local", "12345678");
    }

    private static void await(Callable<Boolean> condition, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("condition timed out after " + timeoutMillis + " ms");
    }

    /** Accepts ingest connections and, until switched on, never sends an ACK. */
    private static final class Silent extends WebSocketServer {

        final AtomicLong received = new AtomicLong();
        final AtomicBoolean acknowledge = new AtomicBoolean();
        private final CountDownLatch ready = new CountDownLatch(1);

        Silent() throws Exception {
            super(new InetSocketAddress("127.0.0.1", freePort()));
            setReuseAddr(true);
        }

        void startReady() throws Exception {
            start();
            assertTrue(ready.await(3, TimeUnit.SECONDS));
        }

        @Override
        public void onOpen(WebSocket connection, ClientHandshake handshake) { }

        @Override
        public void onClose(WebSocket connection, int code, String reason, boolean remote) { }

        @Override
        public void onMessage(WebSocket connection, String text) {
            received.incrementAndGet();
            if (!acknowledge.get()) {
                return;
            }
            try {
                var snapshot = ContractJson.readSnapshot(text);
                connection.send(ContractJson.ack(new AckEnvelope(snapshot.channelId(),
                        snapshot.streamId(), snapshot.sourceRevision())));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void onError(WebSocket connection, Exception ex) { }

        @Override
        public void onStart() {
            ready.countDown();
        }

        private static int freePort() throws Exception {
            try (ServerSocket probe = new ServerSocket(0)) {
                return probe.getLocalPort();
            }
        }
    }
}
