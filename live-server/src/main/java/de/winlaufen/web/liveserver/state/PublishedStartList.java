package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.StartListRow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The last accepted start list of one channel, plus the live server's own browser-facing
 * {@code publicationRevision}.
 *
 * <p>The revision belongs to this live-server run and only orders what one browser has already
 * received. It is not the bridge's {@code generation}, which counts the organiser's imports and is
 * only comparable within one {@code streamId}.
 *
 * <p>{@code generation == 0} with no entries means the bridge has no start list.
 *
 * <p>{@code classCount} is counted once when the list is adopted, not on every read: the read API
 * reports it as metadata on an endpoint meant for frequent polling, and counting distinct class
 * names over thousands of entries per request would be work for nothing.
 */
public record PublishedStartList(long publicationRevision, String streamId, long generation,
                                 String source, String sourceLabel, List<StartListRow> entries,
                                 int classCount) {

    public PublishedStartList {
        entries = List.copyOf(entries);
    }

    public PublishedStartList(long publicationRevision, String streamId, long generation,
                              String source, String sourceLabel, List<StartListRow> entries) {
        this(publicationRevision, streamId, generation, source, sourceLabel, entries,
                countClasses(entries));
    }

    public static PublishedStartList empty() {
        return new PublishedStartList(0, null, 0, "", "", List.of(), 0);
    }

    /**
     * Distinct class names. Counted over the published order, which is the import order, so this
     * never depends on sorting the entries.
     */
    private static int countClasses(List<StartListRow> entries) {
        Set<String> names = new LinkedHashSet<>();
        for (StartListRow row : entries) {
            names.add(row.className());
        }
        return names.size();
    }

    /** Whether a start list is available at all. */
    public boolean present() {
        return generation > 0;
    }
}
