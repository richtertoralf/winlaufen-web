package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.SnapshotEnvelope;
import de.winlaufen.web.contract.SourceHealth;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Holds the published state of exactly one channel.
 *
 * <p>A snapshot replaces the whole state atomically. Within one {@code streamId} lower revisions
 * are rejected and equal revisions are confirmed idempotently; a new authenticated stream may
 * restart at revision 0.
 *
 * <p>The competition time is a WinLaufen value and is only ever carried through. This store never
 * produces, advances or interpolates it — not even while marking the copy as no longer current.
 */
public final class PublishedStateStore {

    private final String channelId;
    private final AtomicReference<PublishedState> state = new AtomicReference<>(PublishedState.empty());
    private final List<Consumer<PublishedState>> listeners = new CopyOnWriteArrayList<>();
    /**
     * Wall clock for freshness metadata only. It records when a snapshot was accepted here, which
     * is what a consumer needs to judge how old the carried competition time is. It never produces
     * or advances that competition time — that value stays exactly what WinLaufen sent.
     */
    private final Clock clock;

    public PublishedStateStore(String channelId) {
        this(channelId, Clock.systemUTC());
    }

    /** Test seam: makes freshness metadata deterministic without sleeping. */
    public PublishedStateStore(String channelId, Clock clock) {
        this.channelId = channelId;
        this.clock = clock;
    }

    public String channelId() {
        return channelId;
    }

    public PublishedState get() {
        return state.get();
    }

    public void addListener(Consumer<PublishedState> listener) {
        listeners.add(listener);
    }

    /**
     * The bridge of this channel is gone. The last copy stays visible — the competition time and
     * the results a speaker already sees must not disappear — but the published source health
     * stops claiming that the data is current. Without this, a live server would keep serving
     * {@code CONNECTED} with a frozen WinLaufen clock for as long as it runs.
     *
     * <p>The stream binding is dropped on purpose: a returning bridge resends the revision it had
     * already delivered, and only an unbound stream makes that snapshot authoritative again
     * instead of being swallowed as a duplicate.
     */
    public synchronized void ingestDisconnected() {
        PublishedState old = state.get();
        CanonicalState degraded = new CanonicalState(SourceHealth.DISCONNECTED, old.state().clock(),
                old.state().competition(), old.state().currentFinish(), old.state().message());
        if (old.streamId() == null && old.state().sourceHealth() == SourceHealth.DISCONNECTED) {
            // Nothing changes for a browser, but the link flag still has to become false: a
            // consumer must not be told the bridge is attached when it is not.
            if (old.bridgeLinkConnected()) {
                state.set(new PublishedState(old.publicationRevision(), old.streamId(),
                        old.sourceRevision(), degraded, old.presentation(),
                        old.reportedSourceHealth(), false, old.clockChangedAtEpochMilli(),
                        old.lastUpdateAtEpochMilli()));
            }
            return;
        }
        PublishedState next = new PublishedState(old.publicationRevision() + 1, null,
                old.sourceRevision(), degraded, old.presentation(), old.reportedSourceHealth(),
                false, old.clockChangedAtEpochMilli(), old.lastUpdateAtEpochMilli());
        state.set(next);
        listeners.forEach(listener -> listener.accept(next));
    }

    /**
     * A bridge ingest connection was opened. This only records the link; it publishes nothing and
     * changes no revision, because an open socket is not yet a state. The first accepted snapshot
     * does the publishing.
     *
     * <p>The reported source health stays what the previous bridge said until the new one speaks:
     * inventing {@code CONNECTED} here would claim a WinLaufen connection nobody has observed.
     */
    public synchronized void ingestConnected() {
        PublishedState old = state.get();
        if (old.bridgeLinkConnected()) {
            return;
        }
        state.set(new PublishedState(old.publicationRevision(), old.streamId(), old.sourceRevision(),
                old.state(), old.presentation(), old.reportedSourceHealth(), true,
                old.clockChangedAtEpochMilli(), old.lastUpdateAtEpochMilli()));
    }

    /**
     * Keeps the last known competition while the source has not supplied one yet.
     *
     * <p>A restarted bridge starts with an empty canonical state and reports source health and
     * competition time again long before WinLaufen resends a class snapshot — WinLaufen sends
     * those only when something changes. Adopting that snapshot wholesale would erase the results
     * a speaker is currently reading, for as long as the next athlete takes to finish.
     *
     * <p>The distinction is the one the bridge model already makes and is not a guess about empty
     * lists: {@code competition == null} means "never received from WinLaufen" — no bridge code
     * path ever sets it back to null once a class snapshot has arrived. An authoritative empty
     * standing is a real {@code Competition} whose classes carry no rows, and that one replaces
     * the stored copy like any other. The current-finish marker travels with the competition it
     * points into, so the retained pair stays consistent.
     */
    private static CanonicalState merged(CanonicalState old, CanonicalState value) {
        if (value.competition() != null || old.competition() == null) {
            return value;
        }
        return new CanonicalState(value.sourceHealth(), value.clock(), old.competition(),
                old.currentFinish(), value.message());
    }

    /**
     * When the competition time carried by this snapshot first appeared here.
     *
     * <p>Only a changed value gets a new timestamp. A snapshot repeating the clock this live server
     * already holds — a presentation change, a message, a result block, a resync after a reconnect —
     * keeps the earlier one. That is deliberate twice over: a clock frozen since the source vanished
     * must not be re-stamped as current by the next unrelated publication, and for a consumer that
     * later wants to relate the competition time to real time, the moment the value appeared is the
     * closest anchor there is.
     *
     * <p>This is explicitly not "when the source last delivered this value". The bridge bumps its
     * revision for every kind of change and the envelope does not say which occurred, so that
     * stronger statement cannot be proven here — and no field claims it. Whether data is still
     * arriving at all is answered by {@code lastUpdateAt} and by the reported source health.
     */
    private long changedAt(PublishedState old, SnapshotEnvelope value) {
        String clockValue = value.state().clock();
        if (clockValue == null) {
            return old.clockChangedAtEpochMilli();
        }
        if (clockValue.equals(old.state().clock()) && old.clockChangedAtEpochMilli() > 0) {
            return old.clockChangedAtEpochMilli();
        }
        return clock.millis();
    }

    /** @return {@code false} when the snapshot was rejected because its revision went backwards. */
    public synchronized boolean accept(SnapshotEnvelope value) {
        if (!channelId.equals(value.channelId())) {
            throw new IllegalArgumentException("Channel mismatch");
        }
        PublishedState old = state.get();
        boolean sameStream = value.streamId().equals(old.streamId());
        if (sameStream && value.sourceRevision() < old.sourceRevision()) {
            return false;
        }
        if (sameStream && value.sourceRevision() == old.sourceRevision()) {
            // Same revision, same state: no publication. The link is demonstrably alive though,
            // and a resend after a reconnect must not leave it flagged as broken.
            if (!old.bridgeLinkConnected()) {
                state.set(new PublishedState(old.publicationRevision(), old.streamId(),
                        old.sourceRevision(), old.state(), old.presentation(),
                        old.reportedSourceHealth(), true, old.clockChangedAtEpochMilli(),
                        old.lastUpdateAtEpochMilli()));
            }
            return true;
        }
        PublishedState next = new PublishedState(old.publicationRevision() + 1, value.streamId(),
                value.sourceRevision(), merged(old.state(), value.state()), value.presentation(),
                value.state().sourceHealth(), true, changedAt(old, value), clock.millis());
        state.set(next);
        listeners.forEach(listener -> listener.accept(next));
        return true;
    }
}
