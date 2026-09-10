package de.winlaufen.web.contract;

/**
 * How the zone used to read the competition time as a time of day was arrived at.
 *
 * <p>This is not a quality rating, and deliberately not one: it only says where the value came
 * from. Whether it is the right zone for the event is something only the organiser knows.
 *
 * <p>It is a separate question from {@link TimeReferenceStatus}, which is about the accuracy of a
 * machine's clock. A zone can be explicitly configured on a machine whose clock is unverified, and
 * a well-synchronised machine can still be in a zone nobody confirmed for this event.
 */
public enum CompetitionTimeZoneSource {

    /** Set explicitly for this bridge, so someone decided it. */
    CONFIGURED,

    /**
     * Nothing was configured, so the zone of the machine running the bridge was used.
     *
     * <p>This is a fallback, <em>not</em> a confirmation. It is right whenever the bridge runs in
     * the time zone of the event — the normal case — and wrong in exactly the setup that is easy to
     * overlook: a Linux host set to UTC, where every difference would then be off by the whole UTC
     * offset without anything looking unusual.
     */
    SYSTEM_DEFAULT
}
