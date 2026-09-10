package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.TimeReferenceSource;
import de.winlaufen.web.contract.TimeReferenceStatus;

/**
 * The live server's own measurement of a clock sample, taken when that sample arrived here.
 *
 * <p>Together with the bridge's measurement inside the sample itself this gives two independent
 * readings of the same competition time. They are reported side by side and nothing chooses between
 * them: which one is worth more depends on the time reference behind each, and that is a decision
 * for the consumer.
 *
 * <p>{@code sampleRevision} records which sample was measured, so a later snapshot carrying the
 * same sample does not move the measurement.
 *
 * <p>The difference of the two {@code systemTimeAtReceipt} values is deliberately not published as
 * a transport latency. Unless both machine clocks are demonstrably synchronised, that difference is
 * dominated by their offset, not by the network.
 */
public record ClockMeasurement(long sampleRevision, long systemTimeAtReceiptEpochMilli,
                               Long competitionMinusReferenceMs,
                               TimeReferenceStatus referenceStatus,
                               TimeReferenceSource referenceSource) {

    public static ClockMeasurement none() {
        return new ClockMeasurement(-1, 0, null, TimeReferenceStatus.UNAVAILABLE,
                TimeReferenceSource.NONE);
    }

    /** Whether a sample was ever measured here. */
    public boolean present() {
        return sampleRevision >= 0;
    }
}
