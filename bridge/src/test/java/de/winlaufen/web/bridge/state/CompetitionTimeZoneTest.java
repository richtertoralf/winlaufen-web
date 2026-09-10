package de.winlaufen.web.bridge.state;

import de.winlaufen.web.contract.ClockSample;
import de.winlaufen.web.contract.CompetitionTimeZoneSource;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.TimeReference;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
     * Nothing configured: the machine's zone is used, and the sample says it is only a fallback.
     * Whichever zone this test machine happens to be in, the source must be the honest one.
     */
    @Test
    void withoutConfigurationTheMachineZoneIsUsedAndMarkedAsFallback() {
        ClockSample sample = sampleOf(null);
        assertEquals(ZoneId.systemDefault().getId(), sample.competitionTimeZone());
        assertEquals(CompetitionTimeZoneSource.SYSTEM_DEFAULT, sample.competitionTimeZoneSource());
    }

    /**
     * The setup that is easy to overlook: a Linux host on UTC. Reading a German competition time as
     * UTC is off by the whole offset, and the only protection is that the sample says where the
     * zone came from.
     */
    @Test
    void aUtcHostWithoutConfigurationIsVisiblyAFallback() {
        ClockSample utc = sampleOf(null);
        ClockSample berlin = sampleOf("Europe/Berlin");

        assertEquals(CompetitionTimeZoneSource.SYSTEM_DEFAULT, utc.competitionTimeZoneSource());
        assertEquals(CompetitionTimeZoneSource.CONFIGURED, berlin.competitionTimeZoneSource());

        // Same telegram, same instant, two hours apart purely because of the zone.
        ClockSample explicitUtc = sampleOf("UTC");
        assertEquals("UTC", explicitUtc.competitionTimeZone());
        assertEquals(2 * 3600_000, explicitUtc.competitionMinusReferenceMs());
        assertEquals(0, berlin.competitionMinusReferenceMs());
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
