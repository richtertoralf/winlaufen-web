package de.winlaufen.web.protocol;

import java.io.ObjectInputFilter;
import java.util.Set;

public final class WinLaufenObjectFilter {
    private static final Set<Class<?>> ALLOWED = Set.of(
            String.class, Integer.class, Number.class, Object[].class,
            String[].class, int[].class, java.util.Vector.class
    );

    private WinLaufenObjectFilter() { }

    public static ObjectInputFilter create() {
        return info -> {
            // references() and streamBytes() are cumulative for the entire connection.
            // Limiting them would impose an artificial maximum event duration.
            if (info.depth() > 12 || (info.arrayLength() >= 0 && info.arrayLength() > 10_000)) {
                return ObjectInputFilter.Status.REJECTED;
            }
            Class<?> type = info.serialClass();
            if (type == null) return ObjectInputFilter.Status.UNDECIDED;
            return ALLOWED.contains(type) ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
        };
    }
}
