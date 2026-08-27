package de.winlaufen.web.model;

import java.util.List;

public record ClassSnapshot(long revision, List<String> headers, List<List<String>> rows) {
    public ClassSnapshot {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }
}
