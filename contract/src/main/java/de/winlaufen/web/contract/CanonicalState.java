package de.winlaufen.web.contract;

/** Output-neutral competition state. Wire values are carried through without correction. */
public record CanonicalState(SourceHealth sourceHealth, String clock, Competition competition,
                             CurrentFinish currentFinish, String message) {

    public static CanonicalState empty() {
        return new CanonicalState(SourceHealth.DISCONNECTED, null, null, null, null);
    }
}
