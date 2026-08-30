package de.winlaufen.web.liveserver;

import de.winlaufen.web.liveserver.config.LiveServerConfig;
import de.winlaufen.web.liveserver.state.PublishedStateStore;
import de.winlaufen.web.liveserver.web.LiveWebSocketServer;
import de.winlaufen.web.liveserver.web.PublicHttpServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live server runtime. Accepts authenticated bridge snapshots, publishes them to browsers and
 * serves the web viewer. It starts without a bridge and contains no WinLaufen protocol code.
 */
public final class LiveServerMain {

    private LiveServerMain() { }

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception ex) {
            System.err.println("WinLaufen Web Live Server konnte nicht gestartet werden: "
                    + ex.getMessage());
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        LiveServerConfig config = LiveServerConfig.system();
        PublishedStateStore store = new PublishedStateStore(config.channelId());
        LiveWebSocketServer webSocket = new LiveWebSocketServer(config.webSocketBindAddress(),
                config.webSocketPort(), store, config.channelId(), config.ingestSecret());

        PublicHttpServer[] http = new PublicHttpServer[1];
        AtomicBoolean stopped = new AtomicBoolean();
        Runnable shutdown = () -> {
            if (!stopped.compareAndSet(false, true)) {
                return;
            }
            if (http[0] != null) {
                http[0].close();
            }
            try {
                webSocket.shutdown();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        try {
            http[0] = new PublicHttpServer(config.httpBindAddress(), config.httpPort(),
                    config.webSocketPort(), store);
            http[0].start();
            webSocket.start();
            webSocket.awaitStart();
            Runtime.getRuntime().addShutdownHook(
                    Thread.ofPlatform().name("live-server-shutdown").unstarted(shutdown));
            System.out.printf("Live Server läuft: HTTP %s:%d, WebSocket %s:%d%n",
                    config.httpBindAddress(), config.httpPort(), config.webSocketBindAddress(),
                    config.webSocketPort());
            if (config.usesDefaultSecret()) {
                System.out.println("WARNUNG: Bekanntes Prototyp-Ingest-Secret aktiv. "
                        + "Port " + config.webSocketPort() + " darf nur in einem kontrollierten "
                        + "Netz erreichbar sein. Siehe README.md, Abschnitt "
                        + "\"Known prototype security limitation\".");
            }
            new CountDownLatch(1).await();
        } catch (Exception ex) {
            shutdown.run();
            throw ex;
        }
    }
}
