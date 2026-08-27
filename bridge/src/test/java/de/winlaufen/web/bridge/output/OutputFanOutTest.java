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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Fan-out over two real WebSocket endpoints: one target failing must not affect the source, the
 * canonical state or the second target, and a returning target must be resynchronised with the
 * newest full snapshot without any delta history.
 */
class OutputFanOutTest {

    @Test
    void fansOutIsolatesFailureAndResyncsLatestFullSnapshot() throws Exception {
        Fake first = new Fake();
        Fake two = new Fake();
        Fake restarted = null;
        first.startReady();
        two.startReady();

        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        List<OutputTargetConfig> configs = List.of(target("one", first.port()), target("two", two.port()));

        try (OutputTargetManager manager = new OutputTargetManager(configs, "stream", store)) {
            manager.start();
            store.clock("10:00:00");
            await(() -> first.revision.get() >= 1 && two.revision.get() >= 1);

            first.stopServer();
            store.clock("10:00:01");
            await(() -> two.clock.get().equals("10:00:01"));
            assertEquals("10:00:01", store.get().state().clock(),
                    "a failing target must not change the canonical state");

            restarted = new Fake(configs.getFirst().endpoint().getPort());
            restarted.startReady();
            Fake current = restarted;
            await(() -> "10:00:01".equals(current.clock.get()));
            assertEquals(store.get().sourceRevision(), current.revision.get(),
                    "the returning target must receive the newest full snapshot");
        } finally {
            if (restarted != null) {
                restarted.stopServer();
            }
            two.stopServer();
        }
    }

    private static OutputTargetConfig target(String id, int port) {
        return new OutputTargetConfig(id, OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"),
                "local", "12345678");
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(9);
        while (System.nanoTime() < deadline) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("condition timed out");
    }

    /** Minimal live-server stand-in: validates the contract and acknowledges every snapshot. */
    private static final class Fake extends WebSocketServer {

        final AtomicLong revision = new AtomicLong(-1);
        final AtomicReference<String> clock = new AtomicReference<>("");
        private final CountDownLatch ready = new CountDownLatch(1);

        Fake() throws Exception {
            this(freePort());
        }

        Fake(int port) {
            super(new InetSocketAddress("127.0.0.1", port));
            setReuseAddr(true);
        }

        int port() {
            return getPort();
        }

        void startReady() throws Exception {
            start();
            assertTrue(ready.await(3, TimeUnit.SECONDS));
        }

        void stopServer() throws Exception {
            stop(500);
        }

        @Override
        public void onOpen(WebSocket connection, ClientHandshake handshake) { }

        @Override
        public void onClose(WebSocket connection, int code, String reason, boolean remote) { }

        @Override
        public void onMessage(WebSocket connection, String text) {
            try {
                var snapshot = ContractJson.readSnapshot(text);
                revision.set(snapshot.sourceRevision());
                clock.set(snapshot.state().clock() == null ? "" : snapshot.state().clock());
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
