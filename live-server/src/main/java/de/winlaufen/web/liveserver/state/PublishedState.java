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
 * <p>{@code clockObservedAtEpochMilli} is the live server's wall-clock reading of when the
 * <em>current competition time value</em> first arrived; {@code 0} means no clock has ever been
 * received. It deliberately does not move when a snapshot repeats a clock this live server already
 * had — a presentation change or a reconnect resends the state, and re-stamping it would present a
 * long-frozen clock as freshly observed. It is metadata about freshness and never a substitute for
 * the competition time: the WinLaufen clock stays a WinLaufen value and is never produced,
 * advanced or interpolated here.
 */
public record PublishedState(long publicationRevision, String streamId, long sourceRevision,
                             CanonicalState state, PresentationConfig presentation,
                             SourceHealth reportedSourceHealth, boolean bridgeLinkConnected,
                             long clockObservedAtEpochMilli) {

    public static PublishedState empty() {
        return new PublishedState(0, null, -1, CanonicalState.empty(), PresentationConfig.defaults(),
                SourceHealth.DISCONNECTED, false, 0);
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
