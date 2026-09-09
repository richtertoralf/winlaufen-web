package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.StartListRow;

import java.util.List;

/**
 * The last accepted start list of one channel, plus the live server's own browser-facing
 * {@code publicationRevision}.
 *
 * <p>The revision belongs to this live-server run and only orders what one browser has already
 * received. It is not the bridge's {@code generation}, which counts the organiser's imports and is
 * only comparable within one {@code streamId}.
 *
 * <p>{@code generation == 0} with no entries means the bridge has no start list.
 */
public record PublishedStartList(long publicationRevision, String streamId, long generation,
                                 String source, String sourceLabel, List<StartListRow> entries) {

    public PublishedStartList {
        entries = List.copyOf(entries);
    }

    public static PublishedStartList empty() {
        return new PublishedStartList(0, null, 0, "", "", List.of());
    }

    /** Whether a start list is available at all. */
    public boolean present() {
        return generation > 0;
    }
}
