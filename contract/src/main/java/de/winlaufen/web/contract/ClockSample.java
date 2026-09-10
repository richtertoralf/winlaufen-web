package de.winlaufen.web.contract;

/**
 * One measurement taken by the bridge at the moment it processed a WinLaufen clock telegram.
 *
 * <p>This is what {@code clock} alone cannot express. The competition time may legitimately stand
 * still — the protocol accepts repeated values and every telegram renews liveness — so a value that
 * has not changed says nothing about whether telegrams are still arriving. A sample is created for
 * <em>every</em> recognised telegram, identical value included, and {@code revision} counts them.
 *
 * <p>{@code revision} counts within one bridge run and restarts with a new {@code streamId}, like
 * every other revision in this contract.
 *
 * <p>{@code systemTimeAtReceipt} is the reading of the <em>bridge's own system clock</em> at that
 * moment, not a verified UTC instant: that machine may be minutes off and still deliver a plausible
 * timestamp. What is known about it is in {@code referenceStatus} and {@code referenceSource}, and
 * nothing here claims more.
 *
 * <p>{@code competitionMinusReferenceMs} is the difference between the competition time of this
 * sample and that reading, positive when the competition time is ahead. It is a measurement, not a
 * calibration: whether it represents a real deviation from world time depends entirely on the
 * reference status. {@code null} when no difference could be formed.
 *
 * <p>{@code competitionMinusReferenceMs} is a measurement and nothing more. It is not an
 * established clock error: WinLaufen sends a time of day with second resolution, and when inside
 * that second it switches internally, when it builds and sends the telegram and how long the
 * transport took are all unknown. What is being compared is the value in this telegram against the
 * reading of this measuring point when it processed the telegram.
 *
 * <p>{@code competitionTimeZone} is the zone in which the competition time was read as a time of
 * day for that subtraction, and {@code competitionTimeZoneSource} says how that zone was arrived
 * at. Both travel with the sample so that a second measuring point subtracts against the same
 * interpretation instead of guessing its own — a live server whose own machine sits in UTC must not
 * quietly reinterpret a competition time that the bridge read in local time.
 */
public record ClockSample(long revision, String competitionTime, String competitionTimeZone,
                          CompetitionTimeZoneSource competitionTimeZoneSource,
                          String systemTimeAtReceipt, Long competitionMinusReferenceMs,
                          TimeReferenceStatus referenceStatus, TimeReferenceSource referenceSource) {
}
