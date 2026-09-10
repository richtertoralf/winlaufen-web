package de.winlaufen.web.bridge.state;

import de.winlaufen.web.bridge.source.winlaufen.ResultBlock;
import de.winlaufen.web.contract.ClockSample;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;
import de.winlaufen.web.contract.TimeReference;
import de.winlaufen.web.contract.TimeReferenceSource;
import de.winlaufen.web.contract.TimeReferenceStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The bridge as a measuring point.
 *
 * <p>What matters here is the distinction the competition time alone cannot make: a clock telegram
 * is an observation of the source even when its value has not moved, and a presentation change or a
 * result block is not an observation at all.
 */
class ClockSampleTest {

    private static final String ZONE = "Europe/Berlin";

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-09-10T12:30:00.100Z"));
    private final CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(),
            TimeReference.systemClock(new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now.get();
                }
            }), ZONE);

    @Test
    void aClockTelegramProducesAMeasuredSample() {
        store.clock("14:31:40");

        ClockSample sample = store.get().state().clockSample();
        assertNotNull(sample);
        assertEquals(1, sample.revision());
        assertEquals("14:31:40", sample.competitionTime());
        assertEquals(ZONE, sample.competitionTimeZone());
        assertEquals("2026-09-10T12:30:00.100Z", sample.systemTimeAtReceipt());
        // 14:31:40 competition against 14:30:00.100 local (12:30:00.100Z in CEST).
        assertEquals(99_900, sample.competitionMinusReferenceMs());
        assertEquals(TimeReferenceStatus.UNVERIFIED, sample.referenceStatus());
        assertEquals(TimeReferenceSource.SYSTEM_CLOCK, sample.referenceSource());
    }

    /**
     * The central case. WinLaufen keeps sending the same value; every telegram is a real
     * observation and gets its own sample, while the competition time stands still.
     */
    @Test
    void aRepeatedValueStillCountsAsAnObservation() {
        store.clock("10:42:17");
        ClockSample first = store.get().state().clockSample();

        now.set(Instant.parse("2026-09-10T12:30:01.100Z"));
        store.clock("10:42:17");
        ClockSample second = store.get().state().clockSample();

        assertEquals(1, first.revision());
        assertEquals(2, second.revision());
        assertEquals(first.competitionTime(), second.competitionTime());
        assertEquals("2026-09-10T12:30:00.100Z", first.systemTimeAtReceipt());
        assertEquals("2026-09-10T12:30:01.100Z", second.systemTimeAtReceipt());
        // The measured difference moves because the reference moved, not the competition time.
        assertEquals(second.competitionMinusReferenceMs(),
                first.competitionMinusReferenceMs() - 1_000);
    }

    @Test
    void aPresentationChangeIsNotAnObservation() {
        store.clock("10:42:17");
        ClockSample sample = store.get().state().clockSample();
        long revisionBefore = store.get().sourceRevision();

        now.set(Instant.parse("2026-09-10T12:35:00.000Z"));
        store.presentation(new PresentationConfig(true, true, true, true, true));

        assertSame(sample, store.get().state().clockSample());
        assertEquals(revisionBefore + 1, store.get().sourceRevision());
    }

    @Test
    void aResultBlockIsNotAnObservation() {
        store.clock("10:42:17");
        ClockSample sample = store.get().state().clockSample();

        now.set(Instant.parse("2026-09-10T12:35:00.000Z"));
        store.result(new ResultBlock("Standardwettkampf", 1, new String[] {"H30"}, new int[] {1},
                0, 0, 0, 0, List.of(List.of("1", "201")), List.of("Rang", "StNr")));

        assertSame(sample, store.get().state().clockSample());
        assertNotNull(store.get().state().competition());
    }

    @Test
    void aMessageAndAHealthChangeAreNotObservations() {
        store.clock("10:42:17");
        ClockSample sample = store.get().state().clockSample();

        store.message("Achtung");
        assertSame(sample, store.get().state().clockSample());

        store.health(SourceHealth.STALE);
        assertSame(sample, store.get().state().clockSample());
        assertEquals(SourceHealth.STALE, store.get().state().sourceHealth());
    }

    @Test
    void aDisconnectKeepsTheLastSampleReadable() {
        store.clock("10:42:17");
        store.health(SourceHealth.DISCONNECTED);

        ClockSample sample = store.get().state().clockSample();
        assertNotNull(sample);
        assertEquals("10:42:17", sample.competitionTime());
        assertEquals(SourceHealth.DISCONNECTED, store.get().state().sourceHealth());
    }

    @Test
    void aReconnectingSourceIsSampledAgain() {
        store.clock("10:42:17");
        store.health(SourceHealth.DISCONNECTED);

        now.set(Instant.parse("2026-09-10T12:40:00.000Z"));
        store.clock("10:47:00");

        ClockSample sample = store.get().state().clockSample();
        assertEquals(2, sample.revision());
        assertEquals("10:47:00", sample.competitionTime());
        assertEquals(SourceHealth.CONNECTED, store.get().state().sourceHealth());
    }

    /** A value the protocol explicitly permits and forbids validating. It is carried, not judged. */
    @Test
    void anImplausibleClockValueIsSampledWithoutADifference() {
        store.clock("99:99:99");

        ClockSample sample = store.get().state().clockSample();
        assertEquals("99:99:99", sample.competitionTime());
        assertNull(sample.competitionMinusReferenceMs());
        assertEquals("2026-09-10T12:30:00.100Z", sample.systemTimeAtReceipt());
    }

    @Test
    void thereIsNoSampleBeforeTheFirstTelegram() {
        assertNull(store.get().state().clockSample());
    }
}
