package de.winlaufen.web.liveserver.config;

import java.util.regex.Pattern;

/**
 * Purely technical live-server deployment configuration. It contains nothing an organiser
 * configures and is never delivered to browsers.
 */
public record LiveServerConfig(String httpBindAddress, int httpPort, String webSocketBindAddress,
                               int webSocketPort, String channelId, String ingestSecret) {

    /**
     * Known prototype development secret. Deliberately kept working for the prototype baseline;
     * see the security section in README.md.
     */
    public static final String DEFAULT_INGEST_SECRET = "local-development-secret";

    private static final Pattern CHANNEL_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final int MIN_SECRET_CHARS = 8;

    public LiveServerConfig {
        if (channelId == null || !CHANNEL_ID.matcher(channelId).matches()) {
            throw new IllegalArgumentException("Ungültige Channel-ID");
        }
        if (ingestSecret == null || ingestSecret.length() < MIN_SECRET_CHARS) {
            throw new IllegalArgumentException("Ingest-Secret zu kurz");
        }
    }

    public static LiveServerConfig system() {
        return new LiveServerConfig(
                System.getProperty("winlaufen.live.http.bind", "0.0.0.0"),
                port("winlaufen.live.http.port", 8080),
                System.getProperty("winlaufen.live.websocket.bind", "0.0.0.0"),
                port("winlaufen.live.websocket.port", 8081),
                System.getProperty("winlaufen.live.channel", "local"),
                System.getProperty("winlaufen.live.secret", DEFAULT_INGEST_SECRET));
    }

    public boolean usesDefaultSecret() {
        return DEFAULT_INGEST_SECRET.equals(ingestSecret);
    }

    private static int port(String key, int fallback) {
        int value = Integer.getInteger(key, fallback);
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException("Ungültiger Port: " + key);
        }
        return value;
    }
}
