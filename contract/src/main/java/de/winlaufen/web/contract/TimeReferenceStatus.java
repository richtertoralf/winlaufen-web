package de.winlaufen.web.contract;

/**
 * What is known about the time reference of a measuring point — nothing more.
 *
 * <p>A system clock can be minutes off and still report a plausible instant. A measurement is
 * therefore only as good as what can actually be said about the clock behind it, and this enum says
 * exactly that and never guesses. Being online, running Linux or running Windows proves nothing.
 */
public enum TimeReferenceStatus {

    /**
     * The reference is demonstrably synchronised against an external time source. Only to be used
     * when that can really be established, never as an assumption.
     */
    SYNCHRONIZED,

    /**
     * A reference exists and delivers timestamps, but nothing is known about its accuracy. This is
     * the honest answer for a plain system clock and is the normal case in a closed competition
     * network. It is not an error: the measurements are still usable, and a consumer decides what
     * to make of them.
     */
    UNVERIFIED,

    /** No reference at all, so no timestamp and no difference were measured. */
    UNAVAILABLE
}
