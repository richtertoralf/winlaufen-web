package de.winlaufen.web.bridge.state;

import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.PresentationConfig;

/**
 * One immutable canonical revision: competition state plus the presentation config that was
 * published atomically with it.
 */
public record CanonicalSnapshot(long sourceRevision, CanonicalState state,
                                PresentationConfig presentation) { }
