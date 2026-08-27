package de.winlaufen.web.web;

import de.winlaufen.web.json.Json;
import de.winlaufen.web.state.StateEvent;
import de.winlaufen.web.state.StateStore;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class LiveWebSocketServer extends WebSocketServer {
    private final StateStore store;
    private final ConcurrentMap<WebSocket, Long> deliveredRevisions = new ConcurrentHashMap<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private final ThreadPoolExecutor publisher = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(256), Thread.ofPlatform().name("websocket-publisher").factory(),
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile Exception startupError;

    public LiveWebSocketServer(int port, StateStore store) {
        super(new InetSocketAddress("0.0.0.0", port));
        this.store = store;
        // Allow an immediate rebind after a clean stop even while prior client
        // connections are still represented by TCP TIME_WAIT entries.
        setReuseAddr(true);
        store.addListener(this::publish);
    }

    public void awaitStart() throws Exception {
        if (!started.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("WebSocket-Start hat Zeitlimit überschritten");
        if (startupError != null) throw startupError;
    }

    @Override public void onOpen(WebSocket connection, ClientHandshake handshake) {
        sendIfCurrent(connection, new StateEvent(StateEvent.Type.SNAPSHOT, store.get(), -1));
    }

    @Override public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket connection, Draft draft,
                                                                                  ClientHandshake request) throws InvalidDataException {
        if (!OriginPolicy.accepts(request.getFieldValue("Origin"), request.getFieldValue("Host")))
            throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "Origin rejected");
        return super.onWebsocketHandshakeReceivedAsServer(connection, draft, request);
    }

    @Override public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        deliveredRevisions.remove(connection);
    }
    @Override public void onMessage(WebSocket connection, String message) { connection.close(1003, "Read only"); }
    @Override public void onMessage(WebSocket connection, ByteBuffer message) { connection.close(1003, "Read only"); }
    @Override public void onError(WebSocket connection, Exception error) {
        if (connection == null && started.getCount() > 0) { startupError = error; started.countDown(); }
    }
    @Override public void onStart() { started.countDown(); }

    public void publish(StateEvent event) {
        publisher.execute(() -> getConnections().forEach(connection -> {
            // onOpen installs the authoritative snapshot before this connection
            // participates in broadcasts.
            if (deliveredRevisions.containsKey(connection)) sendIfCurrent(connection, event);
        }));
    }

    private void sendIfCurrent(WebSocket connection, StateEvent event) {
        synchronized (connection) {
            long revision = event.state().revision();
            long delivered = deliveredRevisions.getOrDefault(connection, -1L);
            if (!revisionMayFollow(delivered, revision)) return;
            connection.send(Json.event(event));
            deliveredRevisions.put(connection, revision);
        }
    }

    static boolean revisionMayFollow(long delivered, long candidate) {
        return candidate >= delivered;
    }

    public void shutdown() throws InterruptedException {
        publisher.shutdownNow();
        deliveredRevisions.clear();
        stop(1_000);
        if (!publisher.awaitTermination(1, TimeUnit.SECONDS)) publisher.shutdownNow();
    }
}
