package de.winlaufen.web.bridge.output;

/** Volatile per-target runtime telemetry. Never part of the canonical competition state. */
public record OutputTargetRuntime(String targetId, OutputConnectionState state,
                                  String lastAckedStreamId, long lastAckedSourceRevision,
                                  int retryAttempt, String lastError) {

    public static OutputTargetRuntime initial(String targetId, boolean enabled) {
        return new OutputTargetRuntime(targetId,
                enabled ? OutputConnectionState.RETRY_WAIT : OutputConnectionState.DISABLED,
                null, -1, 0, null);
    }

    public OutputTargetRuntime withState(OutputConnectionState next, int retry, String error) {
        return new OutputTargetRuntime(targetId, next, lastAckedStreamId, lastAckedSourceRevision,
                retry, error);
    }

    public OutputTargetRuntime withAck(String streamId, long sourceRevision) {
        return new OutputTargetRuntime(targetId, OutputConnectionState.CONNECTED, streamId,
                sourceRevision, 0, null);
    }
}
