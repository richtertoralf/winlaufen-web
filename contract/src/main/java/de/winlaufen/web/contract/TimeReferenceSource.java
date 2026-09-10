package de.winlaufen.web.contract;

/** Where the timestamps of a measuring point come from. */
public enum TimeReferenceSource {

    /** The operating system clock of that machine, read through {@link java.time.Clock}. */
    SYSTEM_CLOCK,

    /** No source available. */
    NONE
}
