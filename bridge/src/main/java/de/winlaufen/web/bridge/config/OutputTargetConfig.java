package de.winlaufen.web.bridge.config;

import java.net.URI;
import java.util.regex.Pattern;

/** Persistent configuration of one output target. The secret is never part of any published state. */
public record OutputTargetConfig(String id, OutputTargetType type, boolean enabled, URI endpoint,
                                 String channelId, String secret) {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern CHANNEL_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final int MIN_SECRET_CHARS = 8;
    private static final int MAX_SECRET_CHARS = 512;

    public OutputTargetConfig {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Ungültige Target-ID");
        }
        if (channelId == null || !CHANNEL_ID.matcher(channelId).matches()) {
            throw new IllegalArgumentException("Ungültige Channel-ID");
        }
        if (type == null) {
            throw new IllegalArgumentException("Ungültiger Target-Typ");
        }
        EndpointPolicy.validate(type, endpoint);
        if (secret == null || secret.length() < MIN_SECRET_CHARS || secret.length() > MAX_SECRET_CHARS) {
            throw new IllegalArgumentException("Target-Secret muss "
                    + MIN_SECRET_CHARS + " bis " + MAX_SECRET_CHARS + " Zeichen haben");
        }
    }
}
