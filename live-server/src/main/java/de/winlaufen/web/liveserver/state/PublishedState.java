package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;

/**
 * The last accepted bridge snapshot for one channel, plus the live server's own browser-facing
 * {@code publicationRevision} and what this live server knows about the chain that delivered it.
 *
 * <p>{@code state().sourceHealth()} is the browser-facing health and is deliberately degraded to
 * {@code DISCONNECTED} when the bridge link drops, so a viewer never shows a frozen clock as
 * current. That degradation loses the distinction a diagnosing consumer needs, which is why the
 * two facts behind it are kept separately:
 *
 * <ul>
 *   <li>{@code reportedSourceHealth} — the last health the bridge itself reported, i.e. the
 *       bridge's view of its WinLaufen connection. While {@code bridgeLinkConnected} is false this
 *       is the last known value, not a current one: nobody can observe WinLaufen without a bridge.
 *   <li>{@code bridgeLinkConnected} — whether a bridge ingest connection is open right now.
 * </ul>
 *
 * <p>Two wall-clock readings of this machine accompany the state. Both are freshness metadata and
 * never a substitute for the competition time, which stays a WinLaufen value that is never
 * produced, advanced or interpolated here.
 *
 * <ul>
 *   <li>{@code clockChangedAtEpochMilli} — when the <em>current</em> competition time value first
 *       arrived here. It deliberately does not move while a snapshot repeats a value already held,
 *       so it stays the closest known anchor for that value. {@code 0} means no clock has ever
 *       been received.
 *   <li>{@code lastUpdateAtEpochMilli} — when the last snapshot was accepted from the bridge, no
 *       matter what it carried. This is the only thing that shows data still flowing when the
 *       competition time legitimately stands still.
 * </ul>
 *
 * <p>Neither is an observation timestamp of the source, and no field claims to be one. The bridge
 * publishes a new revision for a clock telegram, a result block, a message and a presentation
 * change alike, and the envelope does not say which; this live server can prove that it accepted a
 * snapshot at a point in time, not that WinLaufen re-delivered a particular clock value then.
 */
public record PublishedState(long publicationRevision, String streamId, long sourceRevision,
                             CanonicalState state, PresentationConfig presentation,
                             SourceHealth reportedSourceHealth, boolean bridgeLinkConnected,
                             long clockChangedAtEpochMilli, long lastUpdateAtEpochMilli) {

    public static PublishedState empty() {
        return new PublishedState(0, null, -1, CanonicalState.empty(), PresentationConfig.defaults(),
                SourceHealth.DISCONNECTED, false, 0, 0);
    }

    /**
     * Whether this live server ever accepted a snapshot from a bridge. Before that there is no
     * state at all, and no competition time may be invented.
     *
     * <p>Deliberately not tied to the clock: a bridge that has just started publishes health and
     * presentation before WinLaufen has sent a single clock telegram. That is a real state with no
     * competition time yet, which the API reports as such instead of calling it "no state".
     */
    public boolean available() {
        return sourceRevision >= 0;
    }

    /** The end-to-end status of WinLaufen → bridge → live server as it stands right now. */
    public ChainStatus chainStatus() {
        if (!available()) {
            return ChainStatus.NO_STATE;
        }
        if (!bridgeLinkConnected) {
            return ChainStatus.BRIDGE_DISCONNECTED;
        }
        return switch (reportedSourceHealth) {
            case CONNECTED -> ChainStatus.CONNECTED;
            case STALE -> ChainStatus.STALE;
            case DISCONNECTED -> ChainStatus.WINLAUFEN_DISCONNECTED;
        };
    }
}
