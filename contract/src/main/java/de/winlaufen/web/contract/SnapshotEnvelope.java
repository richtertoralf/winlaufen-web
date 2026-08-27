package de.winlaufen.web.contract;

/** Full authoritative snapshot sent by the bridge for every canonical revision. */
public record SnapshotEnvelope(String type, int schemaVersion, String channelId, String streamId,
                               long sourceRevision, CanonicalState state,
                               PresentationConfig presentation) {

    public static final int SCHEMA_VERSION = 1;

    public SnapshotEnvelope(String channelId, String streamId, long sourceRevision,
                            CanonicalState state, PresentationConfig presentation) {
        this("snapshot", SCHEMA_VERSION, channelId, streamId, sourceRevision, state, presentation);
    }
}
