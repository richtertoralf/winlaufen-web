package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.SnapshotEnvelope;

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
                value.sourceRevision(), value.state(), value.presentation());
        state.set(next);
        listeners.forEach(listener -> listener.accept(next));
        return true;
    }
}
