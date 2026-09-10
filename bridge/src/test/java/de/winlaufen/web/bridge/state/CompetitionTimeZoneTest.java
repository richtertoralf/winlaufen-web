package de.winlaufen.web.bridge.state;

import de.winlaufen.web.contract.ClockSample;
import de.winlaufen.web.contract.CompetitionTimeZoneSource;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.TimeReference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Where the competition time zone comes from, and that the answer is never dressed up.
 *
 * <p>The zone decides how a time of day is subtracted from an instant, so getting it wrong shifts
 * every measurement by a whole UTC offset — silently. The API therefore reports not only which zone
 * was used but how it was arrived at.
 */
class CompetitionTimeZoneTest {

    private static final Instant AT = Instant.parse("2026-09-10T12:30:00Z");

    private static CanonicalStateStore storeWith(String configuredZone) {
        return new CanonicalStateStore(PresentationConfig.defaults(),
                TimeReference.systemClock(Clock.fixed(AT, ZoneOffset.UTC)), configuredZone);
    }

    private static ClockSample sampleOf(String configuredZone) {
        CanonicalStateStore store = storeWith(configuredZone);
        store.clock("14:30:00");
        ClockSample sample = store.get().state().clockSample();
        assertNotNull(sample);
        return sample;
    }

    @Test
    void anExplicitZoneIsReportedAsConfigured() {
        ClockSample sample = sampleOf("Europe/Berlin");
        assertEquals("Europe/Berlin", sample.competitionTimeZone());
        assertEquals(CompetitionTimeZoneSource.CONFIGURED, sample.competitionTimeZoneSource());
        // 14:30:00 competition against 14:30:00 local (12:30Z in CEST).
        assertEquals(0, sample.competitionMinusReferenceMs());
    }

    /**
     * The normal case for a German event: nobody configures anything and it is simply right.
     * WinLaufen is used essentially only in Germany, so that is the application's own default.
     */
    @Test
    void withoutConfigurationTheApplicationDefaultApplies() {
        ClockSample sample = sampleOf(null);
        assertEquals("Europe/Berlin", sample.competitionTimeZone());
        assertEquals(CompetitionTimeZoneSource.APPLICATION_DEFAULT,
                sample.competitionTimeZoneSource());
        // 14:30:00 competition against 14:30:00 Berlin local time.
        assertEquals(0, sample.competitionMinusReferenceMs());
    }

    /**
     * The setup that used to be the trap: a Linux host on UTC. The machine's zone no longer decides
     * anything, so a German event stays correct without a single configuration entry.
     */
    @Test
    void aHostInAnotherZoneDoesNotChangeTheCompetitionZone() {
        ClockSample sample = sampleOf(null);

        assertEquals("Europe/Berlin", sample.competitionTimeZone());
        assertEquals(CompetitionTimeZoneSource.APPLICATION_DEFAULT,
                sample.competitionTimeZoneSource());
        // Would be +7 200 000 ms if the host zone (UTC on this test machine's CI) decided.
        assertEquals(0, sample.competitionMinusReferenceMs());
    }

    /** Abroad: one entry in the configuration file, and it is honoured and reported as such. */
    @Test
    void anEventAbroadIsConfiguredExplicitly() {
        ClockSample sample = sampleOf("America/New_York");

        assertEquals("America/New_York", sample.competitionTimeZone());
        assertEquals(CompetitionTimeZoneSource.CONFIGURED, sample.competitionTimeZoneSource());
        // 12:30Z is 08:30 in New York, so 14:30:00 competition time is six hours ahead.
        assertEquals(6 * 3600_000, sample.competitionMinusReferenceMs());
    }

    /** The zone travels with every sample, so a receiver never has to fall back to its own. */
    @Test
    void everySampleCarriesTheZoneItWasMeasuredWith() {
        CanonicalStateStore store = storeWith("Europe/Berlin");
        store.clock("14:30:00");
        store.clock("14:30:01");

        ClockSample sample = store.get().state().clockSample();
        assertEquals(2, sample.revision());
        assertEquals("Europe/Berlin", sample.competitionTimeZone());
        assertEquals(CompetitionTimeZoneSource.CONFIGURED, sample.competitionTimeZoneSource());
    }
}
