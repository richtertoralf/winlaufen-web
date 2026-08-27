package de.winlaufen.web.contract;

/** Live-server confirmation that a snapshot was validated and adopted into the published state. */
public record AckEnvelope(String type, int schemaVersion, String channelId, String streamId,
                          long sourceRevision) {

    public AckEnvelope(String channelId, String streamId, long sourceRevision) {
        this("ack", SnapshotEnvelope.SCHEMA_VERSION, channelId, streamId, sourceRevision);
    }
}
