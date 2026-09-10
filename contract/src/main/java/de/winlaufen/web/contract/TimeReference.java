package de.winlaufen.web.contract;

import java.time.Clock;
import java.time.Instant;

/**
 * The time reference of one measuring point: an instant plus what is known about it.
 *
 * <p>Bridge and live server both measure, and both have to state the quality of what they measured.
 * Keeping that behind one small interface means a verified source — an NTP query answered once and
 * cached, say — can be added later by supplying another implementation, without touching the sample
 * model, the contract or the API.
 */
public interface TimeReference {

    Instant now();

    TimeReferenceStatus status();

    TimeReferenceSource source();

    /**
     * The plain system clock, honestly labelled {@link TimeReferenceStatus#UNVERIFIED}.
     *
     * <p>Java offers no portable way to ask whether the operating system clock is disciplined by an
     * external source, and asking the operating system would mean a platform-specific external
     * command per query. Claiming {@code SYNCHRONIZED} on the grounds that a machine is online, or
     * that it runs Linux, would be a guess — and a wrong guess here is worse than no statement,
     * because a consumer would take a measurement for a calibration. So this says what it knows: a
     * clock is present, its accuracy is unknown.
     */
    static TimeReference systemClock() {
        return systemClock(Clock.systemUTC());
    }

    /** Test seam: the same honest statement over an injected clock. */
    static TimeReference systemClock(Clock clock) {
        return new TimeReference() {
            @Override
            public Instant now() {
                return clock.instant();
            }

            @Override
            public TimeReferenceStatus status() {
                return TimeReferenceStatus.UNVERIFIED;
            }

            @Override
            public TimeReferenceSource source() {
                return TimeReferenceSource.SYSTEM_CLOCK;
            }
        };
    }
}
