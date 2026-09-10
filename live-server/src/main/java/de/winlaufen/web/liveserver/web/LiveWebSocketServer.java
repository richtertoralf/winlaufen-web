package de.winlaufen.web.liveserver.web;

import de.winlaufen.web.contract.AckEnvelope;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.ContractLimits;
import de.winlaufen.web.contract.SnapshotEnvelope;
import de.winlaufen.web.contract.StartListEnvelope;
import de.winlaufen.web.liveserver.state.PublishedStartList;
import de.winlaufen.web.liveserver.state.PublishedStartListStore;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    /**
     * Sign-of-life interval for browser connections.
     *
     * <p>Derived from the source heartbeat rule: the WinLaufen clock arrives about once a second
     * and counts as stale after 4 s. The browser link uses the same order of magnitude, so a
     * viewer notices a vanished live server within seconds instead of showing a frozen clock as
     * if it were current.
     */
    public static final long BROWSER_KEEPALIVE_MILLIS = 2_000;

    private enum Role { BROWSER, INGEST }

    private final PublishedStateStore store;
    private final PublishedStartListStore startLists;
    private final String channelId;
    private final String secret;
    private final String ingestPath;
    private final ConcurrentMap<WebSocket, Long> delivered = new ConcurrentHashMap<>();
    /**
     * Start lists have their own publication revision, so a browser that connects while an import
     * is being published cannot end up with the older of the two messages.
     */
    private final ConcurrentMap<WebSocket, Long> deliveredStartLists = new ConcurrentHashMap<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private final long keepaliveMillis;
    private final ScheduledExecutorService keepalive = Executors.newSingleThreadScheduledExecutor(
            runnable -> Thread.ofPlatform().name("live-browser-keepalive").daemon().unstarted(runnable));
    private volatile Exception startupError;

    public LiveWebSocketServer(String bind, int port, PublishedStateStore store,
                               PublishedStartListStore startLists, String channelId,
                               String secret) {
        this(bind, port, store, startLists, channelId, secret,
                ContractLimits.MAX_INGEST_MESSAGE_BYTES, ContractLimits.MAX_BROWSER_MESSAGE_BYTES,
                BROWSER_KEEPALIVE_MILLIS);
    }

    /** Test seam: proves the limits without allocating a production-sized payload. */
    LiveWebSocketServer(String bind, int port, PublishedStateStore store,
                        PublishedStartListStore startLists, String channelId,
                        String secret, int ingestLimitBytes, int browserLimitBytes) {
        this(bind, port, store, startLists, channelId, secret, ingestLimitBytes, browserLimitBytes,
                BROWSER_KEEPALIVE_MILLIS);
    }

    /** Test seam: proves the keepalive without letting a test wait for the production interval. */
    LiveWebSocketServer(String bind, int port, PublishedStateStore store,
                        PublishedStartListStore startLists, String channelId,
                        String secret, int ingestLimitBytes, int browserLimitBytes,
                        long keepaliveMillis) {
        super(new InetSocketAddress(bind, port),
                drafts(ingestPath(channelId), ingestLimitBytes, browserLimitBytes));
        this.store = store;
        this.startLists = startLists;
        this.channelId = channelId;
        this.secret = secret;
        this.ingestPath = ingestPath(channelId);
        this.keepaliveMillis = keepaliveMillis;
        setReuseAddr(true);
        store.addListener(this::publish);
        startLists.addListener(this::publishStartList);
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
            send(connection, startLists.get());
        } else {
            // The read API must be able to tell "WinLaufen is gone" from "the bridge is gone", so
            // the link itself is recorded, not only what arrives over it.
            store.ingestConnected();
        }
    }

    @Override
    public void onMessage(WebSocket connection, String text) {
        if (connection.getAttachment() != Role.INGEST) {
            connection.close(CloseFrame.REFUSE, "Read only");
            return;
        }
        boolean startList = false;
        try {
            startList = StartListEnvelope.TYPE.equals(ContractJson.typeOf(text));
            if (startList) {
                acceptStartList(text);
                return;
            }
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
            // The reason names which message failed; both end the connection, and the bridge
            // answers either with a reconnect and a full resync.
            connection.close(CloseFrame.PROTOCOL_ERROR,
                    startList ? "Invalid start list" : "Invalid snapshot");
        }
    }

    /**
     * A start list is not acknowledged. The competition state already proves on every revision
     * that this live server processes what it receives, and an unusable start list closes the
     * connection here, which the bridge answers with a reconnect and a full resync. A second ACK
     * type would add a parallel liveness path for a message that arrives a few times a day.
     */
    private void acceptStartList(String text) throws Exception {
        StartListEnvelope value = ContractJson.readStartList(text);
        if (!channelId.equals(value.channelId())) {
            throw new IllegalArgumentException("Channel mismatch");
        }
        if (!startLists.accept(value)) {
            throw new IllegalArgumentException("Start list generation decreased");
        }
    }

    @Override
    public void onMessage(WebSocket connection, ByteBuffer bytes) {
        connection.close(CloseFrame.REFUSE, "Text only");
    }

    @Override
    public void onClose(WebSocket connection, int code, String reason, boolean remote) {
        delivered.remove(connection);
        deliveredStartLists.remove(connection);
        // A browser leaving changes nothing for anyone else; the bridge leaving means the
        // published copy is no longer current and must stop claiming a connected source.
        if (connection.getAttachment() == Role.INGEST) {
            store.ingestDisconnected();
        }
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
        keepalive.scheduleWithFixedDelay(this::sendKeepalive,
                keepaliveMillis, keepaliveMillis, TimeUnit.MILLISECONDS);
        started.countDown();
    }

    /**
     * Only browsers need this, and only they get it: the ingest connection has its own ACK
     * round trip. A send may race with a closing connection, which must never end the schedule.
     */
    private void sendKeepalive() {
        for (WebSocket connection : getConnections()) {
            if (connection.getAttachment() != Role.BROWSER || !connection.isOpen()) {
                continue;
            }
            try {
                connection.send(PublicJson.heartbeat());
            } catch (RuntimeException ex) {
                // The connection went away between the check and the send; onClose cleans up.
            }
        }
    }

    private void publish(PublishedState value) {
        for (WebSocket connection : getConnections()) {
            if (connection.getAttachment() == Role.BROWSER) {
                send(connection, value);
            }
        }
    }

    /**
     * Sent only when a start list was actually adopted, never with a clock telegram. A browser
     * therefore receives the participant list on connect and after an import, not every second.
     */
    private void publishStartList(PublishedStartList value) {
        for (WebSocket connection : getConnections()) {
            if (connection.getAttachment() == Role.BROWSER) {
                send(connection, value);
            }
        }
    }

    /** Guarantees that an individual browser never receives a lower start-list revision. */
    private void send(WebSocket connection, PublishedStartList value) {
        synchronized (connection) {
            long last = deliveredStartLists.getOrDefault(connection, -1L);
            if (value.publicationRevision() < last) {
                return;
            }
            connection.send(PublicJson.startList(value));
            deliveredStartLists.put(connection, value.publicationRevision());
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
        keepalive.shutdownNow();
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
