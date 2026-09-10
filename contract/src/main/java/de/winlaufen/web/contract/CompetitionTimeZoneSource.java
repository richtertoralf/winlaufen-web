package de.winlaufen.web.contract;

/**
 * How the zone used to read the competition time as a time of day was arrived at.
 *
 * <p>This is not a quality rating, and deliberately not one: it only says where the value came
 * from. It is a separate question from {@link TimeReferenceStatus}, which is about the accuracy of
 * a machine's clock. A zone can be explicitly configured on a machine whose clock is unverified,
 * and a well-synchronised machine can still be running in the default zone.
 */
public enum CompetitionTimeZoneSource {

    /** Set explicitly for this bridge, so someone decided it. */
    CONFIGURED,

    /**
     * Nothing was configured, so Sprecher-Web's own default for WinLaufen applies:
     * {@link #APPLICATION_DEFAULT_ZONE}.
     *
     * <p>Deliberately not the zone of the machine. WinLaufen is used essentially only in Germany,
     * so the German zone is the right answer for a normal event and nobody should have to configure
     * it. The host's zone, by contrast, is an accident of how someone set up that computer — a
     * Linux server on UTC would otherwise shift every measurement by the full offset without
     * anything looking unusual.
     */
    APPLICATION_DEFAULT;

    /** The zone a WinLaufen event runs in unless someone says otherwise. */
    public static final String APPLICATION_DEFAULT_ZONE = "Europe/Berlin";
}
