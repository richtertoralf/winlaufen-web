package de.winlaufen.web.contract;

import java.util.List;

/**
 * A complete table snapshot of one class, exactly as supplied by the source.
 *
 * <p>{@code roundOrHeat} is the value of the result block that delivered this table, passed on
 * unchanged and not interpreted. {@code null} means the sender did not supply it — a bridge older
 * than this field — and never "round zero".
 */
public record ClassSnapshot(long sourceRevision, List<String> headers, List<List<String>> rows,
                            Integer roundOrHeat) {

    public ClassSnapshot {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }

    /** A table without a supplied round, as an older bridge sends it. */
    public ClassSnapshot(long sourceRevision, List<String> headers, List<List<String>> rows) {
        this(sourceRevision, headers, rows, null);
    }
}
