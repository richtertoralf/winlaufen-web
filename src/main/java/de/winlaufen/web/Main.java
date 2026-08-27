package de.winlaufen.web;

import de.winlaufen.web.config.AppConfig;
import de.winlaufen.web.config.ConfigStore;
import de.winlaufen.web.protocol.WinLaufenClient;
import de.winlaufen.web.state.StateStore;
import de.winlaufen.web.web.HttpAppServer;
import de.winlaufen.web.web.LiveWebSocketServer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        try { run(); }
        catch (Exception ex) {
            System.err.println("WinLaufen Web konnte nicht gestartet werden: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        ConfigStore configStore = ConfigStore.inUserHome();
        AtomicReference<AppConfig> config = new AtomicReference<>(configStore.load());
        StateStore state = new StateStore();
        WinLaufenClient client = new WinLaufenClient(config.get().winLaufenHost(), state);
        LiveWebSocketServer webSocket = new LiveWebSocketServer(config.get().webSocketPort(), state);
        HttpAppServer[] http = new HttpAppServer[1];
        AtomicBoolean stopped = new AtomicBoolean();
        Runnable shutdown = () -> {
            if (!stopped.compareAndSet(false, true)) return;
            if (http[0] != null) http[0].close();
            client.close();
            try { webSocket.shutdown(); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        };
        try {
            http[0] = new HttpAppServer(config.get().httpPort(), state, configStore, config::get, next -> {
                AppConfig previous = config.getAndSet(next);
                if (!previous.winLaufenHost().equals(next.winLaufenHost())) client.reconnectTo(next.winLaufenHost());
            });
            http[0].start();
            webSocket.start();
            webSocket.awaitStart();
            client.start();
            System.out.printf("WinLaufen Web läuft: http://localhost:%d/ (Renderer: /renderer, WebSocket: %d)%n",
                    config.get().httpPort(), config.get().webSocketPort());
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().name("winlaufen-shutdown").unstarted(shutdown));
            new CountDownLatch(1).await();
        } catch (Exception ex) {
            shutdown.run();
            throw ex;
        }
    }
}
