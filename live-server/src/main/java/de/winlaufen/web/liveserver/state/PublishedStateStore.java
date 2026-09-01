package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.SnapshotEnvelope;
import de.winlaufen.web.contract.SourceHealth;

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

    public PublishedStateStore(String channelId) {
        this.channelId = channelId;
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
        if (old.streamId() == null && old.state().sourceHealth() == SourceHealth.DISCONNECTED) {
            return;
        }
        CanonicalState degraded = new CanonicalState(SourceHealth.DISCONNECTED, old.state().clock(),
                old.state().competition(), old.state().currentFinish(), old.state().message());
        PublishedState next = new PublishedState(old.publicationRevision() + 1, null,
                old.sourceRevision(), degraded, old.presentation());
        state.set(next);
        listeners.forEach(listener -> listener.accept(next));
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
            return true;
        }
        PublishedState next = new PublishedState(old.publicationRevision() + 1, value.streamId(),
                value.sourceRevision(), merged(old.state(), value.state()), value.presentation());
        state.set(next);
        listeners.forEach(listener -> listener.accept(next));
        return true;
    }
}
