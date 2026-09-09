package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.StartListEnvelope;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Holds the published start list of exactly one channel.
 *
 * <p>Memory-only, like every other state of the live server: it is a presentation node, not an
 * archive. After a restart the bridge republishes on reconnect, so nothing has to be kept on disk.
 *
 * <p>An accepted envelope replaces the whole start list atomically. There is no merge and no
 * patch — the bridge sends a complete stock, and a receiver that merged could keep a bib that its
 * source no longer has.
 *
 * <p>Generations are only comparable inside one {@code streamId}. Within one bridge run they count
 * up, so a lower one is genuinely stale and is rejected. Across runs they say nothing: a restarted
 * bridge gets a new {@code streamId} and may legitimately arrive at generation 1 again, or with
 * none at all after a lost start list. Rejecting that as "older" would leave the live server
 * showing a start list that no longer exists.
 */
public final class PublishedStartListStore {

    private final String channelId;
    private final AtomicReference<PublishedStartList> state =
            new AtomicReference<>(PublishedStartList.empty());
    private final List<Consumer<PublishedStartList>> listeners = new CopyOnWriteArrayList<>();

    public PublishedStartListStore(String channelId) {
        this.channelId = channelId;
    }

    public String channelId() {
        return channelId;
    }

    public PublishedStartList get() {
        return state.get();
    }

    public void addListener(Consumer<PublishedStartList> listener) {
        listeners.add(listener);
    }

    /**
     * @return {@code false} when the envelope was rejected because its generation went backwards
     *         inside the same stream; the previously published start list stays in force
     */
    public synchronized boolean accept(StartListEnvelope value) {
        if (!channelId.equals(value.channelId())) {
            throw new IllegalArgumentException("Channel mismatch");
        }
        PublishedStartList old = state.get();
        boolean sameStream = value.streamId().equals(old.streamId());
        if (sameStream && value.generation() < old.generation()) {
            return false;
        }
        if (sameStream && value.generation() == old.generation()) {
            // The same generation is the same stock. A reconnect resends it, and confirming it
            // without a new publication keeps browsers from redrawing for nothing.
            return true;
        }
        PublishedStartList next = new PublishedStartList(old.publicationRevision() + 1,
                value.streamId(), value.generation(), value.source(), value.sourceLabel(),
                value.entries());
        state.set(next);
        listeners.forEach(listener -> listener.accept(next));
        return true;
    }
}
