package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.PresentationConfig;

/**
 * The last accepted bridge snapshot for one channel, plus the live server's own browser-facing
 * {@code publicationRevision}.
 */
public record PublishedState(long publicationRevision, String streamId, long sourceRevision,
                             CanonicalState state, PresentationConfig presentation) {

    public static PublishedState empty() {
        return new PublishedState(0, null, -1, CanonicalState.empty(), PresentationConfig.defaults());
    }
}
