package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.state.CanonicalSnapshot;
import de.winlaufen.web.contract.AckEnvelope;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.ContractViolationException;
import de.winlaufen.web.contract.SnapshotEnvelope;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Outgoing WebSocket connection for one output target.
 *
 * <p>Owns its own worker thread, socket, retry counter and ACK state. It holds at most the newest
 * canonical snapshot; older unsent snapshots are replaced because every snapshot is complete and
 * authoritative. A failure here never reaches the source or another target.
 */
public final class WebSocketOutputAdapter implements LiveOutputAdapter {

    /** Retry curve: immediately, then 2 s, then 5 s, then every 10 s. */
    static final long[] RETRY_DELAYS_MILLIS = {0L, 2_000L, 5_000L, 10_000L};

    /** Grace period before an open but silent target stops being reported as healthy. */
    static final long ACK_STALE_NANOS = 15_000_000_000L;

    private static final long CONNECT_TIMEOUT_SECONDS = 5L;
    private static final long SEND_POLL_MILLIS = 1_000L;
    private static final long STABLE_CONNECTION_NANOS = 2_000_000_000L;
    private static final int MAX_ERROR_CHARS = 200;

    private final OutputTargetConfig config;
    private final String streamId;
    private final long ackStaleNanos;
    private final AtomicReference<CanonicalSnapshot> latest;
    private final AtomicReference<OutputTargetRuntime> runtime;
    private final AtomicBoolean running = new AtomicBoolean();

    /** Notified when a new snapshot or an ACK arrives. Never shortens the retry backoff. */
    private final Object dataSignal = new Object();
    /** Notified only by {@link #close()}, so shutdown interrupts a pending retry wait. */
    private final Object lifecycleSignal = new Object();

    private volatile WebSocketClient client;
    private volatile Thread worker;
    private volatile long lastAckProgressNanos;
    private volatile long lastSentRevision = -1;
    /** Forces a full snapshot after every handshake, even when nothing changed meanwhile. */
    private volatile boolean resyncPending;

    public WebSocketOutputAdapter(OutputTargetConfig config, String streamId,
                                  CanonicalSnapshot initial) {
        this(config, streamId, initial, ACK_STALE_NANOS);
    }

    /** Test seam: allows shortening the ACK grace period without slowing the suite down. */
    WebSocketOutputAdapter(OutputTargetConfig config, String streamId, CanonicalSnapshot initial,
                           long ackStaleNanos) {
        this.config = config;
        this.streamId = streamId;
        this.ackStaleNanos = ackStaleNanos;
        this.latest = new AtomicReference<>(initial);
        this.runtime = new AtomicReference<>(OutputTargetRuntime.initial(config.id(), config.enabled()));
    }

    @Override
    public void start() {
        if (!config.enabled() || !running.compareAndSet(false, true)) {
            return;
        }
        worker = Thread.ofPlatform().name("output-" + config.id()).start(this::run);
    }

    @Override
    public void publish(CanonicalSnapshot value) {
        latest.set(value);
        wakeData();
    }

    @Override
    public OutputTargetRuntime runtime() {
        return runtime.get();
    }

    @Override
    public void close() {
        running.set(false);
        WebSocketClient current = client;
        if (current != null) {
            current.close();
        }
        wakeData();
        synchronized (lifecycleSignal) {
            lifecycleSignal.notifyAll();
        }
        Thread thread = worker;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(2_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        int failures = 0;
        while (running.get()) {
            long delay = retryDelayMillis(failures);
            if (delay > 0) {
                setState(OutputConnectionState.RETRY_WAIT, failures, runtime.get().lastError());
                awaitRetryDelay(delay);
                if (!running.get()) {
                    break;
                }
            }
            setState(OutputConnectionState.CONNECTING, failures, null);
            try {
                failures = consumeConnection() ? 0 : failures + 1;
            } catch (Exception ex) {
                if (!running.get()) {
                    break;
                }
                failures++;
                setState(OutputConnectionState.RETRY_WAIT, failures, safe(ex));
            }
        }
    }

    static long retryDelayMillis(int failures) {
        return RETRY_DELAYS_MILLIS[Math.min(failures, RETRY_DELAYS_MILLIS.length - 1)];
    }

    /**
     * @return {@code true} when the connection was established and stayed usable long enough to
     *         reset the retry counter. A target that accepts and immediately drops keeps backing off.
     */
    private boolean consumeConnection() throws Exception {
        Client connection = new Client(config.endpoint(),
                Map.of("Authorization", "Bearer " + config.secret()));
        client = connection;
        try {
            if (!connection.connectBlocking(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Verbindungsaufbau fehlgeschlagen");
            }
            long openedNanos = System.nanoTime();
            lastSentRevision = -1;
            lastAckProgressNanos = openedNanos;
            resyncPending = true;
            while (running.get() && connection.isOpen()) {
                deliver(connection);
                awaitData();
            }
            boolean stable = System.nanoTime() - openedNanos >= STABLE_CONNECTION_NANOS;
            if (running.get() && !stable) {
                setState(OutputConnectionState.RETRY_WAIT, 0, "Verbindung sofort wieder geschlossen");
            }
            return stable;
        } finally {
            connection.close();
            client = null;
        }
    }

    private void deliver(Client connection) {
        CanonicalSnapshot value = latest.get();
        OutputTargetRuntime current = runtime.get();
        boolean acknowledged = !resyncPending
                && streamId.equals(current.lastAckedStreamId())
                && current.lastAckedSourceRevision() >= value.sourceRevision();
        if (acknowledged) {
            markLive();
            return;
        }
        // Each revision is transmitted at most once per connection; a missing ACK must not turn
        // into a resend storm. The next canonical revision supersedes it anyway.
        if (!resyncPending && value.sourceRevision() <= lastSentRevision) {
            evaluateAckLiveness();
            return;
        }
        // Socket-level backpressure: never queue a second full snapshot behind an unflushed one.
        if (connection.hasBufferedData()) {
            evaluateAckLiveness();
            return;
        }
        String json;
        try {
            json = ContractJson.snapshot(new SnapshotEnvelope(config.channelId(), streamId,
                    value.sourceRevision(), value.state(), value.presentation()));
        } catch (ContractViolationException ex) {
            // A data problem is not repaired by reconnecting. Skip this revision and keep the
            // transport open so the next canonical revision can recover on its own.
            setState(current.state() == OutputConnectionState.CONNECTED
                            ? OutputConnectionState.CONNECTED
                            : current.state(),
                    current.retryAttempt(), "Snapshot nicht publizierbar: " + safe(ex));
            return;
        }
        connection.send(json);
        resyncPending = false;
        lastSentRevision = value.sourceRevision();
        evaluateAckLiveness();
    }

    private void evaluateAckLiveness() {
        OutputTargetRuntime current = runtime.get();
        boolean pending = !streamId.equals(current.lastAckedStreamId())
                || lastSentRevision > current.lastAckedSourceRevision();
        if (!pending) {
            markLive();
            return;
        }
        if (System.nanoTime() - lastAckProgressNanos > ackStaleNanos) {
            setState(OutputConnectionState.STALE, current.retryAttempt(),
                    "Keine ACK-Bestätigung seit " + (ackStaleNanos / 1_000_000_000L) + " s");
        }
    }

    private void markLive() {
        runtime.updateAndGet(old -> old.state() == OutputConnectionState.CONNECTED && old.lastError() == null
                ? old
                : old.withState(OutputConnectionState.CONNECTED, 0, null));
    }

    private void setState(OutputConnectionState state, int retry, String error) {
        runtime.updateAndGet(old -> old.withState(state, retry, error));
    }

    private void awaitRetryDelay(long millis) {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        synchronized (lifecycleSignal) {
            while (running.get()) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    return;
                }
                try {
                    lifecycleSignal.wait(Math.max(1L, remainingNanos / 1_000_000L));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void awaitData() {
        synchronized (dataSignal) {
            try {
                dataSignal.wait(SEND_POLL_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void wakeData() {
        synchronized (dataSignal) {
            dataSignal.notifyAll();
        }
    }

    private static String safe(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > MAX_ERROR_CHARS ? message.substring(0, MAX_ERROR_CHARS) : message;
    }

    private final class Client extends WebSocketClient {

        Client(URI uri, Map<String, String> headers) {
            super(uri, headers);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            lastAckProgressNanos = System.nanoTime();
            setState(OutputConnectionState.CONNECTED, 0, null);
            wakeData();
        }

        @Override
        public void onMessage(String text) {
            AckEnvelope ack;
            try {
                ack = ContractJson.readAck(text);
            } catch (Exception ex) {
                close(CloseFrame.PROTOCOL_ERROR, "Invalid ACK");
                return;
            }
            if (!config.channelId().equals(ack.channelId()) || !streamId.equals(ack.streamId())) {
                return;
            }
            lastAckProgressNanos = System.nanoTime();
            runtime.updateAndGet(old -> {
                boolean sameStream = streamId.equals(old.lastAckedStreamId());
                if (sameStream && ack.sourceRevision() < old.lastAckedSourceRevision()) {
                    return old;
                }
                return old.withAck(streamId, ack.sourceRevision());
            });
            wakeData();
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            wakeData();
        }

        @Override
        public void onError(Exception ex) {
            OutputTargetRuntime current = runtime.get();
            setState(current.state(), current.retryAttempt(), safe(ex));
        }
    }
}
