package de.winlaufen.web.contract;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * Difference between a WinLaufen competition time and an instant of some measuring point.
 *
 * <p>Pure arithmetic on values that are already there. It calibrates nothing, corrects nothing and
 * says nothing about which measuring point deserves more trust.
 */
public final class CompetitionTimeOffset {

    /** Half a day in milliseconds; the fold point of the wrap-around rule below. */
    private static final long HALF_DAY_MILLIS = 12L * 60 * 60 * 1000;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private CompetitionTimeOffset() { }

    /**
     * {@code competitionTime - reference}, in milliseconds, positive when the competition time is
     * ahead of the reading of that measuring point.
     *
     * <p>WinLaufen supplies a time of day without a date, so the difference is only defined modulo
     * 24 hours. It is normalised into {@code (-12 h, +12 h]}, which is the only assumption that
     * makes the interesting case work: a competition time of {@code 00:00:02} against a reference
     * of {@code 23:59:59} is three seconds ahead, not almost 24 hours behind. The price is that a
     * genuine deviation of more than twelve hours would be reported the short way round — a
     * misconfiguration far outside anything this measurement is for.
     *
     * <p>The competition time is deliberately not turned into a timestamp anywhere else; it is read
     * as a time of day in {@code zone} solely to subtract the two.
     *
     * @return {@code null} when no difference can be formed — no time, no reference, an unparseable
     *         value such as the protocol's permitted {@code Uhr99:99:99}, or an unknown zone.
     *         Nothing is estimated in that case.
     */
    public static Long differenceMillis(String competitionTime, String zoneId, Instant reference) {
        LocalTime competition = parse(competitionTime);
        if (competition == null || zoneId == null || reference == null) {
            return null;
        }
        LocalTime referenceTime;
        try {
            referenceTime = LocalTime.ofInstant(reference, ZoneId.of(zoneId));
        } catch (RuntimeException ex) {
            return null;
        }
        long raw = millisOfDay(competition) - millisOfDay(referenceTime);
        return normalise(raw);
    }

    /**
     * Folds a raw difference into {@code (-12 h, +12 h]}. Exactly {@code -12 h} becomes
     * {@code +12 h}, which keeps the interval half-open and the result single-valued.
     */
    static long normalise(long millis) {
        long folded = Math.floorMod(millis, DAY_MILLIS);
        return folded > HALF_DAY_MILLIS ? folded - DAY_MILLIS : folded;
    }

    /**
     * The wire value as a time of day, or {@code null} when it is not one.
     *
     * <p>The protocol accepts any two-digit groups, {@code Uhr99:99:99} included, and forbids
     * validating them. Nothing is rejected here either — an unparseable value simply yields no
     * difference, and the competition time itself is carried through untouched as always.
     */
    private static LocalTime parse(String value) {
        if (value == null || value.length() != 8 || value.charAt(2) != ':' || value.charAt(5) != ':') {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static long millisOfDay(LocalTime time) {
        return time.toNanoOfDay() / 1_000_000L;
    }
}
