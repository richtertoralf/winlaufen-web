package de.winlaufen.web.liveserver.web;

import de.winlaufen.web.contract.AckEnvelope;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.ContractLimits;
import de.winlaufen.web.contract.SnapshotEnvelope;
import de.winlaufen.web.liveserver.state.PublishedState;
import de.winlaufen.web.liveserver.state.PublishedStateStore;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Single WebSocket port with two strictly separated handshake policies.
 *
 * <ul>
 *   <li>{@code /live/v1} — browsers, same-host Origin required, read-only, tiny payload limit.</li>
 *   <li>{@code /bridge/v1/channels/<channel>} — bridge ingest, Bearer authentication, no Origin
 *       dependency, payload limited to one contract snapshot.</li>
 * </ul>
 */
public final class LiveWebSocketServer extends WebSocketServer {

    public static final String BROWSER_PATH = "/live/v1";

    private enum Role { BROWSER, INGEST }

    private final PublishedStateStore store;
    private final String channelId;
    private final String secret;
    private final String ingestPath;
    private final ConcurrentMap<WebSocket, Long> delivered = new ConcurrentHashMap<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private volatile Exception startupError;

    public LiveWebSocketServer(String bind, int port, PublishedStateStore store, String channelId,
                               String secret) {
        this(bind, port, store, channelId, secret,
                ContractLimits.MAX_INGEST_MESSAGE_BYTES, ContractLimits.MAX_BROWSER_MESSAGE_BYTES);
    }

    /** Test seam: proves the limits without allocating a production-sized payload. */
    LiveWebSocketServer(String bind, int port, PublishedStateStore store, String channelId,
                        String secret, int ingestLimitBytes, int browserLimitBytes) {
        super(new InetSocketAddress(bind, port),
                drafts(ingestPath(channelId), ingestLimitBytes, browserLimitBytes));
        this.store = store;
        this.channelId = channelId;
        this.secret = secret;
        this.ingestPath = ingestPath(channelId);
        setReuseAddr(true);
        store.addListener(this::publish);
    }

    public static String ingestPath(String channelId) {
        return "/bridge/v1/channels/" + channelId;
    }

    /**
     * Ingest frames may carry a full snapshot; browser frames may not. Registering both drafts
     * makes the library apply the correct hard limit from the first decoded frame onwards.
     */
    private static List<Draft> drafts(String ingestPath, int ingestLimitBytes, int browserLimitBytes) {
        return List.of(
                new SizeLimitedDraft(ingestPath, true, ingestLimitBytes),
                new SizeLimitedDraft(ingestPath, false, browserLimitBytes));
    }

    public void awaitStart() throws Exception {
        if (!started.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("WebSocket-Start hat Zeitlimit überschritten");
        }
        if (startupError != null) {
            throw startupError;
        }
    }

    @Override
    public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket connection,
                                                                      Draft draft,
                                                                      ClientHandshake request)
            throws InvalidDataException {
        String path = request.getResourceDescriptor();
        if (BROWSER_PATH.equals(path)) {
            if (!OriginPolicy.accepts(request.getFieldValue("Origin"), request.getFieldValue("Host"))) {
                throw reject("Origin rejected");
            }
            connection.setAttachment(Role.BROWSER);
        } else if (ingestPath.equals(path)) {
            if (!constantTimeEquals("Bearer " + secret, request.getFieldValue("Authorization"))) {
                throw reject("Authentication rejected");
            }
            connection.setAttachment(Role.INGEST);
        } else {
            throw reject("Unknown WebSocket path");
        }
        return super.onWebsocketHandshakeReceivedAsServer(connection, draft, request);
    }

    @Override
    public void onOpen(WebSocket connection, ClientHandshake handshake) {
        if (connection.getAttachment() == Role.BROWSER) {
            send(connection, store.get());
        }
    }

    @Override
    public void onMessage(WebSocket connection, String text) {
        if (connection.getAttachment() != Role.INGEST) {
            connection.close(CloseFrame.REFUSE, "Read only");
            return;
        }
        try {
            SnapshotEnvelope value = ContractJson.readSnapshot(text);
            if (!channelId.equals(value.channelId())) {
                throw new IllegalArgumentException("Channel mismatch");
            }
            if (!store.accept(value)) {
                throw new IllegalArgumentException("Revision decreased");
            }
            connection.send(ContractJson.ack(
                    new AckEnvelope(channelId, value.streamId(), value.sourceRevision())));
        } catch (Exception ex) {
            connection.close(CloseFrame.PROTOCOL_ERROR, "Invalid snapshot");
        }
    }

    @Override
    public void onMessage(WebSocket connection, ByteBuffer bytes) {
        connection.close(CloseFrame.REFUSE, "Text only");
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        delivered.remove(connection);
    }

    @Override
    public void onError(WebSocket connection, Exception ex) {
        if (connection == null && started.getCount() > 0) {
            startupError = ex;
            started.countDown();
        }
    }

    @Override
    public void onStart() {
        started.countDown();
    }

    private void publish(PublishedState value) {
        for (WebSocket connection : getConnections()) {
            if (connection.getAttachment() == Role.BROWSER) {
                send(connection, value);
            }
        }
    }

    /** Guarantees that an individual browser never receives a lower publication revision. */
    private void send(WebSocket connection, PublishedState value) {
        synchronized (connection) {
            long last = delivered.getOrDefault(connection, -1L);
            if (value.publicationRevision() < last) {
                return;
            }
            connection.send(PublicJson.state(value));
            delivered.put(connection, value.publicationRevision());
        }
    }

    public void shutdown() throws InterruptedException {
        delivered.clear();
        stop(1_000);
    }

    private static InvalidDataException reject(String message) {
        return new InvalidDataException(CloseFrame.POLICY_VALIDATION, message);
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8));
    }
}
