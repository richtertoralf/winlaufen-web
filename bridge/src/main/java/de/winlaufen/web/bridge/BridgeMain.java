package de.winlaufen.web.bridge;

import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.BridgeConfigStore;
import de.winlaufen.web.bridge.control.BridgeControlServer;
import de.winlaufen.web.bridge.output.OutputTargetManager;
import de.winlaufen.web.bridge.source.winlaufen.WinLaufenClient;
import de.winlaufen.web.bridge.state.CanonicalStateStore;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridge runtime. Owns the WinLaufen source, the canonical state, the organiser configuration and
 * the output fan-out. It starts without a live server and never serves the web viewer.
 */
public final class BridgeMain {

    private BridgeMain() { }

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception ex) {
            System.err.println("WinLaufen Web Bridge konnte nicht gestartet werden: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        BridgeConfigStore configStore = BridgeConfigStore.fromSystemProperties();
        BridgeConfigStore.LoadResult loaded = configStore.loadWithNotices();
        loaded.notices().forEach(notice -> System.out.println("Hinweis: " + notice));

        AtomicReference<BridgeConfig> config = new AtomicReference<>(loaded.config());
        CanonicalStateStore state = new CanonicalStateStore(config.get().presentation());
        WinLaufenClient source = new WinLaufenClient(config.get().sourceHost(), state);
        String streamId = UUID.randomUUID().toString();
        OutputTargetManager outputs = new OutputTargetManager(config.get().targets(), streamId, state);

        BridgeControlServer[] control = new BridgeControlServer[1];
        AtomicBoolean stopped = new AtomicBoolean();
        Runnable shutdown = () -> {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            if (control[0] != null) {
                control[0].close();
            }
            source.close();
            outputs.close();
        };

        try {
            control[0] = new BridgeControlServer(config.get().controlBindAddress(),
                    config.get().controlPort(), state, configStore, config::get, outputs::runtimes,
                    next -> apply(config, next, source, state, outputs));
            control[0].start();
            outputs.start();
            source.start();
            Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform().name("bridge-shutdown").unstarted(shutdown));
            System.out.printf("Bridge Control läuft: http://localhost:%d/ (Stream %s)%n",
                    config.get().controlPort(), streamId);
            System.out.println("Konfiguration: " + configStore.path());
            new CountDownLatch(1).await();
        } catch (Exception ex) {
            shutdown.run();
            throw ex;
        }
    }

    /**
     * Applies a configuration change to exactly the components it affects. Targets are reconciled
     * incrementally, so an unrelated target keeps its connection, ACK state and retry counter.
     */
    private static void apply(AtomicReference<BridgeConfig> config, BridgeConfig next,
                              WinLaufenClient source, CanonicalStateStore state,
                              OutputTargetManager outputs) {
        BridgeConfig old = config.getAndSet(next);
        if (!old.sourceHost().equals(next.sourceHost())) {
            source.reconnectTo(next.sourceHost());
        }
        if (!old.presentation().equals(next.presentation())) {
            state.presentation(next.presentation());
        }
        if (!old.targets().equals(next.targets())) {
            outputs.reconfigure(next.targets());
        }
    }
}
