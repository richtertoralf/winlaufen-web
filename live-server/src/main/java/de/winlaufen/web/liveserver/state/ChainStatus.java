package de.winlaufen.web.liveserver.state;

/**
 * End-to-end status of the delivery chain WinLaufen → bridge → live server, as one value a
 * consumer can act on without interpreting every detail itself.
 *
 * <p>It exists because a competition time and a result table prove nothing about the chain: both
 * are the last known copy and stay readable on purpose while the source is gone. Only
 * {@link #CONNECTED} means the whole chain is currently healthy; every other value says where it
 * is broken, and the detailed fields stay available alongside it.
 *
 * <p>These are derived values, not a new state machine: they combine the health the bridge already
 * reports for its WinLaufen connection with whether this live server currently has a bridge ingest
 * connection.
 */
public enum ChainStatus {

    /** WinLaufen is connected to the bridge, and the bridge is publishing to this live server. */
    CONNECTED,

    /**
     * The bridge is connected to this live server and reports that its WinLaufen source has gone
     * quiet — no clock telegram for longer than the protocol's stale window. The last known state
     * stays readable and is not current.
     */
    STALE,

    /**
     * Bridge and live server work, but the bridge currently has no connection to WinLaufen. The
     * last known state stays readable and is not current.
     */
    WINLAUFEN_DISCONNECTED,

    /**
     * This live server currently has no bridge ingest connection, so nothing is being published to
     * it. What WinLaufen is doing is unobservable from here; the last known state stays readable.
     */
    BRIDGE_DISCONNECTED,

    /**
     * This live server has not received a single snapshot since it started. There is no
     * competition time, and none is invented.
     */
    NO_STATE
}
