package de.winlaufen.web.contract;

import java.util.List;

/** One original result-block pointer and its addressed row, not a derived sporting event. */
public record FinishHint(long sequence, long sourceRevision, int classIndex, String className,
                         int rowIndex, List<String> headers, List<String> row) {
    public FinishHint {
        headers = List.copyOf(headers);
        row = List.copyOf(row);
    }
}
