package de.winlaufen.web.contract;

/**
 * Output-neutral competition state. Wire values are carried through without correction.
 *
 * <p>{@code clock} is the WinLaufen competition time, never a local clock: no stage of the chain
 * produces, advances or interpolates it.
 *
 * <p>{@code clockSample} is the bridge's measurement of the telegram that last delivered a clock —
 * every telegram, including one repeating the current value. The competition time alone cannot say
 * whether telegrams are still arriving, because a standing value is a legitimate state; the sample
 * can. {@code null} means no clock telegram has been processed on this stream yet.
 *
 * <p>A {@code null} {@code competition} means "not yet received from the source", not "the source
 * reports nothing". An authoritative empty standing is a real {@link Competition} whose classes
 * carry no rows. Consumers that keep a last known copy rely on this difference.
 */
public record CanonicalState(SourceHealth sourceHealth, String clock, Competition competition,
                             CurrentFinish currentFinish, String message, ClockSample clockSample) {

    /**
     * A state without a clock measurement. Every stage that copies a state forward must pass the
     * existing sample explicitly instead — losing it would make a running source look as if it had
     * never delivered a telegram.
     */
    public CanonicalState(SourceHealth sourceHealth, String clock, Competition competition,
                          CurrentFinish currentFinish, String message) {
        this(sourceHealth, clock, competition, currentFinish, message, null);
    }

    public static CanonicalState empty() {
        return new CanonicalState(SourceHealth.DISCONNECTED, null, null, null, null, null);
    }
}
