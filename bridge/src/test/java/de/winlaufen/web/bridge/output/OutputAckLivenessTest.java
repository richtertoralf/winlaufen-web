package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.AckEnvelope;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SnapshotEnvelope;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * ACK liveness and delivery semantics of one output target.
 *
 * <p>The adapter holds at most the newest canonical snapshot, sends every revision at most once per
 * connection and never lets a missing ACK turn into a resend storm. These are the properties under
 * test. How many of a burst's intermediate revisions happen to reach the wire is a race between the
 * producing thread and the adapter's worker thread, so it is deliberately not asserted.
 */
class OutputAckLivenessTest {

    /** Short ACK grace period so the regression is proven without a slow test. */
    private static final long STALE_AFTER_NANOS = 800_000_000L;

    /**
     * Quiet window used to prove the absence of further traffic. The adapter's worker re-evaluates
     * its connection at least once per second, so this covers more than two full cycles: a resend
     * or a queued backlog would become visible, while a slower machine only produces less traffic
     * and can therefore never fail this check.
     */
    private static final long QUIET_MILLIS = 2_500L;

    private static final int BURST_REVISIONS = 200;

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
            await(() -> server.total() >= 1, 5_000);
            await(() -> adapter.runtime().state() == OutputConnectionState.STALE, 6_000);
            assertEquals(-1, adapter.runtime().lastAckedSourceRevision(),
                    "nothing was ever confirmed by the silent target");
            assertTrue(adapter.runtime().lastError().contains("ACK"));

            server.acknowledge.set(true);
            store.clock("10:00:01");
            await(() -> adapter.runtime().state() == OutputConnectionState.CONNECTED, 6_000);
            assertTrue(adapter.runtime().lastAckedSourceRevision() >= 1);
            server.rethrowFailure();
        } finally {
            adapter.close();
            subscription.close();
            server.stop(500);
        }
    }

    /**
     * The decisive coalescing proof, free of any scheduling race: while the adapter is not started
     * there is no worker thread at all, so none of the burst's revisions can reach the wire. A
     * queueing implementation would have to deliver all of them after the handshake; an adapter
     * that keeps only the newest snapshot delivers exactly one.
     */
    @Test
    void keepsOnlyTheNewestSnapshotWhileNoConnectionExists() throws Exception {
        Silent server = new Silent();
        server.startReady();
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        WebSocketOutputAdapter adapter = new WebSocketOutputAdapter(target(server.getPort()),
                "stream", store.get(), WebSocketOutputAdapter.ACK_STALE_NANOS);
        AutoCloseable subscription = store.addListener(adapter::publish);
        try {
            for (int index = 0; index < BURST_REVISIONS; index++) {
                store.clock(clockValue(index));
            }
            long newest = store.get().sourceRevision();
            assertEquals(BURST_REVISIONS, newest, "every clock update is a canonical revision");

            adapter.start();
            await(() -> server.revisions().contains(newest), 10_000);
            Thread.sleep(QUIET_MILLIS);

            List<Long> delivered = server.revisions();
            assertEquals(1, server.connectionCount(), "the target was reached over one connection");
            assertEquals(List.of(newest), delivered,
                    "only the newest snapshot may survive; superseded revisions are dropped, not queued");
            server.rethrowFailure();
        } finally {
            adapter.close();
            subscription.close();
            server.stop(500);
        }
    }

    /**
     * Burst over an already open connection. Which intermediate revisions win the race is
     * scheduler-dependent and therefore not asserted; the ordering guarantees are not.
     */
    @Test
    void burstDeliversRevisionsInOrderAndEndsOnTheNewestOne() throws Exception {
        Silent server = new Silent();
        server.startReady();
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        WebSocketOutputAdapter adapter = new WebSocketOutputAdapter(target(server.getPort()),
                "stream", store.get(), WebSocketOutputAdapter.ACK_STALE_NANOS);
        AutoCloseable subscription = store.addListener(adapter::publish);
        try {
            adapter.start();
            await(() -> adapter.runtime().state() == OutputConnectionState.CONNECTED, 5_000);

            for (int index = 0; index < BURST_REVISIONS; index++) {
                store.clock(clockValue(index));
            }
            long newest = store.get().sourceRevision();
            await(() -> server.revisions().contains(newest), 10_000);

            List<Long> afterBurst = server.revisions();
            Thread.sleep(QUIET_MILLIS);
            List<Long> afterQuietWindow = server.revisions();

            assertEquals(afterBurst, afterQuietWindow,
                    "without a newer revision nothing may follow; a backlog would keep draining");
            assertEquals(1, server.connectionCount(), "the target was reached over one connection");
            assertStrictlyIncreasing(server.perConnection());
            assertEquals(newest, afterQuietWindow.get(afterQuietWindow.size() - 1),
                    "the newest revision is delivered last, so no older one follows a newer one");
            assertTrue(afterQuietWindow.stream().allMatch(revision -> revision >= 0 && revision <= newest),
                    "only revisions that the store actually published may be delivered: " + afterQuietWindow);
            server.rethrowFailure();
        } finally {
            adapter.close();
            subscription.close();
            server.stop(500);
        }
    }

    /**
     * A missing ACK must not make the adapter repeat the same revision. The observed transition to
     * {@code STALE} proves that the worker evaluated the open connection again after the send, so
     * the guard was reached rather than merely never exercised.
     */
    @Test
    void doesNotResendTheSameRevisionWhileAcksAreMissing() throws Exception {
        Silent server = new Silent();
        server.startReady();
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        WebSocketOutputAdapter adapter = new WebSocketOutputAdapter(target(server.getPort()),
                "stream", store.get(), STALE_AFTER_NANOS);
        AutoCloseable subscription = store.addListener(adapter::publish);
        try {
            adapter.start();
            store.clock("12:00:00");
            long published = store.get().sourceRevision();
            await(() -> server.revisions().contains(published), 5_000);
            await(() -> adapter.runtime().state() == OutputConnectionState.STALE, 6_000);
            Thread.sleep(QUIET_MILLIS);

            List<Long> delivered = server.revisions();
            assertEquals(1, server.connectionCount(), "the target was reached over one connection");
            assertStrictlyIncreasing(server.perConnection());
            assertEquals(published, delivered.get(delivered.size() - 1),
                    "the published revision was sent once and never repeated: " + delivered);
            assertEquals(-1, adapter.runtime().lastAckedSourceRevision(),
                    "nothing was ever confirmed by the silent target");
            server.rethrowFailure();
        } finally {
            adapter.close();
            subscription.close();
            server.stop(500);
        }
    }

    /**
     * Per connection every revision is transmitted at most once and revisions never go backwards.
     * A full resync after a new handshake is legitimate, which is why this is checked per
     * connection instead of across the whole run.
     */
    private static void assertStrictlyIncreasing(List<List<Long>> perConnection) {
        for (List<Long> connection : perConnection) {
            for (int index = 1; index < connection.size(); index++) {
                assertTrue(connection.get(index) > connection.get(index - 1),
                        "revisions must increase strictly, so none is repeated or reordered: "
                                + connection);
            }
        }
    }

    private static String clockValue(int index) {
        return String.format("11:%02d:%02d", index / 60, index % 60);
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

    /**
     * Accepts ingest connections and, until switched on, never sends an ACK. It records the
     * {@code sourceRevision} of every received snapshot per connection, so the tests can assert the
     * delivery contract instead of a bare message count.
     */
    private static final class Silent extends WebSocketServer {

        final AtomicBoolean acknowledge = new AtomicBoolean();
        private final CountDownLatch ready = new CountDownLatch(1);
        private final Object lock = new Object();
        private final List<Long> received = new ArrayList<>();
        private final Map<WebSocket, List<Long>> perConnection = new LinkedHashMap<>();
        private volatile Exception failure;

        Silent() throws Exception {
            super(new InetSocketAddress("127.0.0.1", freePort()));
            setReuseAddr(true);
        }

        void startReady() throws Exception {
            start();
            assertTrue(ready.await(3, TimeUnit.SECONDS));
        }

        int total() {
            synchronized (lock) {
                return received.size();
            }
        }

        List<Long> revisions() {
            synchronized (lock) {
                return List.copyOf(received);
            }
        }

        List<List<Long>> perConnection() {
            synchronized (lock) {
                return perConnection.values().stream().map(List::copyOf).toList();
            }
        }

        int connectionCount() {
            synchronized (lock) {
                return perConnection.size();
            }
        }

        /** Surfaces a decoding problem in the test instead of losing it on the server thread. */
        void rethrowFailure() throws Exception {
            Exception value = failure;
            if (value != null) {
                throw value;
            }
        }

        @Override
        public void onOpen(WebSocket connection, ClientHandshake handshake) {
            synchronized (lock) {
                perConnection.computeIfAbsent(connection, key -> new ArrayList<>());
            }
        }

        @Override
        public void onClose(WebSocket connection, int code, String reason, boolean remote) { }

        @Override
        public void onMessage(WebSocket connection, String text) {
            SnapshotEnvelope snapshot;
            try {
                snapshot = ContractJson.readSnapshot(text);
            } catch (Exception ex) {
                failure = ex;
                return;
            }
            synchronized (lock) {
                received.add(snapshot.sourceRevision());
                perConnection.computeIfAbsent(connection, key -> new ArrayList<>())
                        .add(snapshot.sourceRevision());
            }
            if (!acknowledge.get()) {
                return;
            }
            try {
                connection.send(ContractJson.ack(new AckEnvelope(snapshot.channelId(),
                        snapshot.streamId(), snapshot.sourceRevision())));
            } catch (Exception ex) {
                failure = ex;
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
