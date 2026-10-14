package de.winlaufen.web.contract;

import java.util.ArrayList;
import java.util.List;

/** Bounded stream-local original hints. A missing sequence means retention was exceeded. */
public record FinishHistory(long lastSequence, List<FinishHint> hints) {
    public static final int MAX_HINTS = 256;
    public static final int MAX_JSON_CHARS = 262_144;

    public FinishHistory {
        hints = List.copyOf(hints);
    }

    public static FinishHistory empty() {
        return new FinishHistory(0, List.of());
    }

    public FinishHistory append(long revision, int classIndex, String className, int rowIndex,
                                List<String> headers, List<String> row) {
        var retained = new ArrayList<>(hints);
        long next = lastSequence + 1;
        retained.add(new FinishHint(next, revision, classIndex, className, rowIndex, headers, row));
        while (retained.size() > MAX_HINTS) retained.removeFirst();
        while (!retained.isEmpty()
                && ContractJson.write(new FinishHistory(next, retained)).length() > MAX_JSON_CHARS) {
            retained.removeFirst();
        }
        return new FinishHistory(next, retained);
    }
}
