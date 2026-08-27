package de.winlaufen.web.config;

import de.winlaufen.web.model.OutputMode;

public record AppConfig(String winLaufenHost, OutputMode outputMode, int httpPort, int webSocketPort,
                        PublicDisplayConfig publicDisplay) {
    public static final int WINLAUFEN_PORT = 4444;
    public AppConfig(String winLaufenHost, OutputMode outputMode, int httpPort, int webSocketPort) {
        this(winLaufenHost, outputMode, httpPort, webSocketPort, PublicDisplayConfig.defaults());
    }
    public static AppConfig defaults() {
        return new AppConfig("localhost", OutputMode.LOCAL, 8080, 8081, PublicDisplayConfig.defaults());
    }
}
