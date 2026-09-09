package de.winlaufen.web.contract;

import java.util.List;

/**
 * The complete start list the bridge currently holds, sent on the same ingest connection as
 * {@link SnapshotEnvelope} but as its own message.
 *
 * <p>It is deliberately not part of the canonical snapshot. The snapshot carries the competition
 * state and travels with every clock telegram, roughly once a second; a start list has thousands
 * of entries and changes only when the organiser imports one. Putting it into the snapshot would
 * retransmit the whole participant list every second.
 *
 * <p>Like a class snapshot this is a full state, never a delta: each envelope replaces the
 * receiver's start list completely.
 *
 * <p>{@code generation} versions that stock and nothing else. It is not a competition id, not a
 * run id and not a participant id. It counts the imports of one bridge, so it is only comparable
 * within the same {@code streamId}: a restarted bridge may legitimately send a lower generation,
 * and a receiver must accept that rather than treat it as stale.
 *
 * <p>{@code generation == 0} with no entries is the authoritative statement <em>this bridge has no
 * start list</em>. A bridge sends it on every fresh connection so that a live server cannot keep
 * showing a start list its current source no longer has.
 */
public record StartListEnvelope(String type, int schemaVersion, String channelId, String streamId,
                                long generation, String source, String sourceLabel,
                                List<StartListRow> entries) {

    public static final String TYPE = "startlist";

    public StartListEnvelope {
        entries = List.copyOf(entries);
    }

    public StartListEnvelope(String channelId, String streamId, long generation, String source,
                             String sourceLabel, List<StartListRow> entries) {
        this(TYPE, SnapshotEnvelope.SCHEMA_VERSION, channelId, streamId, generation, source,
                sourceLabel, entries);
    }

    /** Whether this envelope carries a start list at all. */
    public boolean present() {
        return generation > 0;
    }
}
