package de.winlaufen.web.contract;

import java.util.List;

/**
 * One original result-block pointer and its addressed row, not a derived sporting event.
 *
 * <p>{@code roundOrHeat} is the unchanged value of the same result block. It does not say whether
 * the pointer is a finish or a lap passage. {@code null} means the sender did not supply it.
 */
public record FinishHint(long sequence, long sourceRevision, int classIndex, String className,
                         int rowIndex, List<String> headers, List<String> row, Integer roundOrHeat) {
    public FinishHint {
        headers = List.copyOf(headers);
        row = List.copyOf(row);
    }

    /** A hint without a supplied round, as an older bridge sends it. */
    public FinishHint(long sequence, long sourceRevision, int classIndex, String className,
                      int rowIndex, List<String> headers, List<String> row) {
        this(sequence, sourceRevision, classIndex, className, rowIndex, headers, row, null);
    }
}
