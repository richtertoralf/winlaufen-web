package de.winlaufen.web.web;

import java.net.URI;

public final class OriginPolicy {
    private OriginPolicy() { }

    public static boolean accepts(String origin, String requestHost) {
        if (origin == null || requestHost == null) return false;
        try {
            URI uri = URI.create(origin);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
            return uri.getHost().equalsIgnoreCase(hostOnly(requestHost));
        } catch (IllegalArgumentException ex) { return false; }
    }

    private static String hostOnly(String value) {
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 0 ? value.substring(1, end) : value;
        }
        int colon = value.lastIndexOf(':');
        return colon > 0 && value.indexOf(':') == colon ? value.substring(0, colon) : value;
    }
}
