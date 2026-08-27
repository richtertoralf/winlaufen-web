package de.winlaufen.web.contract;

import java.util.List;

/** A complete table snapshot of one class, exactly as supplied by the source. */
public record ClassSnapshot(long sourceRevision, List<String> headers, List<List<String>> rows) {

    public ClassSnapshot {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }
}
