package de.winlaufen.web.contract;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CompetitionTimeOffsetTest {

    private static final String ZONE = "Europe/Berlin";

    /** 2026-09-10 is summer time, so Europe/Berlin is UTC+2. */
    private static Instant berlin(String localTime) {
        return Instant.parse("2026-09-10T" + localTime + "Z").minusSeconds(2 * 3600);
    }

    @Test
    void aCompetitionTimeAheadOfTheReferenceIsPositive() {
        Long difference = CompetitionTimeOffset.differenceMillis("14:31:40", ZONE,
                berlin("14:30:00.100"));
        assertEquals(99_900, difference);
    }

    @Test
    void aCompetitionTimeBehindTheReferenceIsNegative() {
        Long difference = CompetitionTimeOffset.differenceMillis("14:30:00", ZONE,
                berlin("14:31:40.000"));
        assertEquals(-100_000, difference);
    }

    @Test
    void anIdenticalTimeIsZero() {
        assertEquals(0, CompetitionTimeOffset.differenceMillis("14:30:00", ZONE,
                berlin("14:30:00.000")));
    }

    /**
     * The case the wrap-around rule exists for. Two seconds past midnight against a reference one
     * second before it is three seconds, not almost a day.
     */
    @Test
    void midnightIsTheShortWayRound() {
        assertEquals(3_000, CompetitionTimeOffset.differenceMillis("00:00:02", ZONE,
                berlin("23:59:59.000")));
        assertEquals(-3_000, CompetitionTimeOffset.differenceMillis("23:59:59", ZONE,
                Instant.parse("2026-09-11T00:00:02Z").minusSeconds(2 * 3600)));
    }

    @Test
    void theFoldPointIsHalfADayAndStaysSingleValued() {
        long halfDay = 12L * 60 * 60 * 1000;
        assertEquals(halfDay, CompetitionTimeOffset.normalise(halfDay));
        assertEquals(halfDay, CompetitionTimeOffset.normalise(-halfDay));
        assertEquals(-halfDay + 1, CompetitionTimeOffset.normalise(halfDay + 1));
        assertEquals(0, CompetitionTimeOffset.normalise(24L * 60 * 60 * 1000));
    }

    /**
     * The protocol permits values that are not times at all and forbids validating them. They
     * simply yield no difference; nothing is estimated and nothing is rejected.
     */
    @Test
    void aValueThatIsNotATimeYieldsNoDifference() {
        assertNull(CompetitionTimeOffset.differenceMillis("99:99:99", ZONE, berlin("14:30:00.000")));
        assertNull(CompetitionTimeOffset.differenceMillis("", ZONE, berlin("14:30:00.000")));
        assertNull(CompetitionTimeOffset.differenceMillis("14:30", ZONE, berlin("14:30:00.000")));
        assertNull(CompetitionTimeOffset.differenceMillis(null, ZONE, berlin("14:30:00.000")));
    }

    @Test
    void aMissingOrUnknownZoneYieldsNoDifference() {
        assertNull(CompetitionTimeOffset.differenceMillis("14:30:00", null, berlin("14:30:00.000")));
        assertNull(CompetitionTimeOffset.differenceMillis("14:30:00", "Mars/Olympus",
                berlin("14:30:00.000")));
    }

    /** The zone is what makes the subtraction meaningful; a UTC host must not silently differ. */
    @Test
    void theZoneDecidesHowTheCompetitionTimeIsRead() {
        Instant at = berlin("14:30:00.000");
        assertEquals(0, CompetitionTimeOffset.differenceMillis("14:30:00", "Europe/Berlin", at));
        assertEquals(2 * 3600_000, CompetitionTimeOffset.differenceMillis("14:30:00", "UTC", at));
    }
}
