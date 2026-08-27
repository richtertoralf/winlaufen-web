package de.winlaufen.web.liveserver.web;

import java.net.URI;

/**
 * Browser WebSocket Origin policy.
 *
 * <p>HTTP and WebSocket intentionally use different ports, so a page served from
 * {@code http://host:8080} legitimately connects to {@code ws://host:8081}. The Origin host must
 * equal the WebSocket request host; the Origin port is not compared. Foreign Origins and requests
 * without an Origin are rejected. {@code https} is accepted because the live server may be served
 * through TLS in front of, or inside, the process.
 */
public final class OriginPolicy {

    private OriginPolicy() { }

    public static boolean accepts(String origin, String host) {
        if (origin == null || host == null) {
            return false;
        }
        try {
            URI parsed = URI.create(origin);
            String scheme = parsed.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return web && parsed.getHost() != null
                    && hostOnly(parsed.getHost()).equalsIgnoreCase(hostOnly(host));
        } catch (Exception ex) {
            return false;
        }
    }

    /** Strips an optional port and IPv6 brackets, so {@code [::1]:8081} matches {@code [::1]}. */
    static String hostOnly(String value) {
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 0 ? value.substring(1, end) : value;
        }
        int colon = value.lastIndexOf(':');
        return colon > 0 && value.indexOf(':') == colon ? value.substring(0, colon) : value;
    }
}
