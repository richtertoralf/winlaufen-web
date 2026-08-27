package de.winlaufen.web.bridge.source.winlaufen;

import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.SourceHealth;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WinLaufenClient implements AutoCloseable {
    private static final int WINLAUFEN_PORT = 4444;
    private final CanonicalStateStore store;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile String host;
    private volatile Socket socket;
    private Thread thread;

    public WinLaufenClient(String host, CanonicalStateStore store) { this.host = host; this.store = store; }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        thread = Thread.ofPlatform().name("winlaufen-client").start(this::run);
    }

    public void reconnectTo(String newHost) {
        host = newHost;
        closeSocket();
        if (thread != null) thread.interrupt();
    }

    private void run() {
        int failure = 0;
        while (running.get()) {
            long delay = switch (failure) { case 0 -> 0; case 1 -> 2_000; case 2 -> 5_000; default -> 10_000; };
            if (!sleep(delay)) continue;
            try {
                consumeConnection(host);
                failure = 0;
            } catch (Exception ex) {
                if (!running.get()) break;
                boolean tcpWasConnected = socket != null && socket.isConnected();
                store.health(SourceHealth.DISCONNECTED);
                failure = tcpWasConnected ? 0 : failure + 1;
            } finally { closeSocket(); }
        }
    }

    private void consumeConnection(String targetHost) throws Exception {
        Socket connection = new Socket();
        socket = connection;
        connection.connect(new InetSocketAddress(targetHost, WINLAUFEN_PORT), 5_000);
        connection.setSoTimeout(500);
        ObjectInputStream objects = new ObjectInputStream(connection.getInputStream());
        Heartbeat heartbeat = new Heartbeat(System.nanoTime());
        WinLaufenProtocolReader reader = new WinLaufenProtocolReader(objects, clock -> {
            heartbeat.accept(System.nanoTime());
            store.clock(clock.wireValue());
        }, store::result, store::message);
        while (running.get() && targetHost.equals(host)) {
            try { reader.readNext(); }
            catch (SocketTimeoutException ignored) { }
            catch (EOFException ex) { throw ex; }
            if (heartbeat.isStale(System.nanoTime())) {
                store.health(SourceHealth.STALE);
                throw new SocketTimeoutException("WinLaufen clock stale");
            }
        }
    }

    private boolean sleep(long millis) {
        try { Thread.sleep(millis); return running.get(); }
        catch (InterruptedException ignored) { return running.get(); }
    }

    private void closeSocket() {
        Socket value = socket;
        socket = null;
        if (value != null) try { value.close(); } catch (Exception ignored) { }
    }

    @Override public void close() {
        running.set(false);
        closeSocket();
        Thread value = thread;
        if (value == null) return;
        value.interrupt();
        if (value == Thread.currentThread()) return;
        try { value.join(2_000); }
        catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}
