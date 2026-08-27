package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.state.CanonicalSnapshot;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.PresentationConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reconfiguration must be incremental: an unchanged target keeps its adapter, connection state and
 * ACK progress, and is never duplicated or restarted because a different target changed.
 */
class OutputTargetManagerTest {

    @Test
    void unchangedTargetsSurviveReconfigurationWhileOthersAreAddedAndRemoved() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        Factory factory = new Factory();
        try (OutputTargetManager manager = new OutputTargetManager(
                List.of(target("alpha", 9001), target("beta", 9002)), "stream", store, factory)) {
            manager.start();
            FakeAdapter alpha = factory.byId("alpha");
            FakeAdapter beta = factory.byId("beta");
            assertTrue(alpha.started);
            assertTrue(beta.started);

            store.clock("10:00:00");
            assertEquals(1, alpha.published.size());
            assertEquals(1, beta.published.size());

            // Remove beta, keep alpha untouched, add gamma.
            manager.reconfigure(List.of(target("alpha", 9001), target("gamma", 9003)));

            assertSame(alpha, factory.byId("alpha"), "unchanged target must keep its adapter");
            assertFalse(alpha.closed, "unchanged target must not be closed");
            assertTrue(beta.closed, "removed target must be closed");
            FakeAdapter gamma = factory.byId("gamma");
            assertTrue(gamma.started, "new target must be started");
            assertEquals(1, factory.created("alpha"), "alpha must not be rebuilt");

            store.clock("10:00:01");
            assertEquals(2, alpha.published.size());
            assertEquals(1, beta.published.size(), "removed target must no longer be fed");
            assertEquals(1, gamma.published.size());
        }
    }

    @Test
    void changingOneTargetReplacesOnlyThatTarget() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        Factory factory = new Factory();
        try (OutputTargetManager manager = new OutputTargetManager(
                List.of(target("alpha", 9001), target("beta", 9002)), "stream", store, factory)) {
            manager.start();
            FakeAdapter alpha = factory.byId("alpha");
            FakeAdapter originalBeta = factory.byId("beta");

            manager.reconfigure(List.of(target("alpha", 9001), target("beta", 9999)));

            assertSame(alpha, factory.byId("alpha"));
            assertFalse(alpha.closed);
            assertTrue(originalBeta.closed, "changed target must be replaced");
            assertEquals(2, factory.created("beta"));
            assertTrue(factory.byId("beta").started);
        }
    }

    @Test
    void closingTheManagerDetachesItsListenerAndAllAdapters() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        Factory factory = new Factory();
        OutputTargetManager manager = new OutputTargetManager(
                List.of(target("alpha", 9001)), "stream", store, factory);
        manager.start();
        FakeAdapter alpha = factory.byId("alpha");
        store.clock("10:00:00");
        assertEquals(1, alpha.published.size());

        manager.close();
        assertTrue(alpha.closed);
        store.clock("10:00:01");
        assertEquals(1, alpha.published.size(), "listener must be removed on close");
        assertTrue(manager.runtimes().isEmpty());
    }

    private static OutputTargetConfig target(String id, int port) {
        return new OutputTargetConfig(id, OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"),
                "local", "12345678");
    }

    private static final class Factory
            implements java.util.function.BiFunction<OutputTargetConfig, CanonicalSnapshot, LiveOutputAdapter> {

        private final List<FakeAdapter> adapters = new CopyOnWriteArrayList<>();

        @Override
        public LiveOutputAdapter apply(OutputTargetConfig config, CanonicalSnapshot initial) {
            FakeAdapter adapter = new FakeAdapter(config);
            adapters.add(adapter);
            return adapter;
        }

        /** @return the most recently created adapter for that target id. */
        FakeAdapter byId(String id) {
            return adapters.stream()
                    .filter(adapter -> adapter.config.id().equals(id))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("no adapter for " + id));
        }

        /** @return how many adapters were ever created for that target id. */
        int created(String id) {
            return (int) adapters.stream().filter(adapter -> adapter.config.id().equals(id)).count();
        }
    }

    private static final class FakeAdapter implements LiveOutputAdapter {

        private final OutputTargetConfig config;
        private final List<CanonicalSnapshot> published = new CopyOnWriteArrayList<>();
        private volatile boolean started;
        private volatile boolean closed;

        FakeAdapter(OutputTargetConfig config) {
            this.config = config;
        }

        @Override
        public void publish(CanonicalSnapshot snapshot) {
            published.add(snapshot);
        }

        @Override
        public OutputTargetRuntime runtime() {
            return OutputTargetRuntime.initial(config.id(), config.enabled());
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
