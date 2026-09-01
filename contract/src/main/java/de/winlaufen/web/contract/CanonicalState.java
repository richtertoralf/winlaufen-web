package de.winlaufen.web.contract;

/**
 * Output-neutral competition state. Wire values are carried through without correction.
 *
 * <p>{@code clock} is the WinLaufen competition time, never a local clock: no stage of the chain
 * produces, advances or interpolates it.
 *
 * <p>A {@code null} {@code competition} means "not yet received from the source", not "the source
 * reports nothing". An authoritative empty standing is a real {@link Competition} whose classes
 * carry no rows. Consumers that keep a last known copy rely on this difference.
 */
public record CanonicalState(SourceHealth sourceHealth, String clock, Competition competition,
                             CurrentFinish currentFinish, String message) {

    public static CanonicalState empty() {
        return new CanonicalState(SourceHealth.DISCONNECTED, null, null, null, null);
    }
}
