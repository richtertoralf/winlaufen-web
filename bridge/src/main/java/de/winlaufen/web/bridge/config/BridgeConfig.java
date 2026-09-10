package de.winlaufen.web.bridge.config;

import de.winlaufen.web.contract.PresentationConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The single organiser configuration. Owned by the bridge, edited only through Bridge Control.
 *
 * <p>{@code competitionTimeZone} is the zone in which the WinLaufen competition time is read when a
 * difference against a measured instant is formed. {@code null} means nothing was configured and
 * the zone of the machine is used instead — a fallback the read API reports as such, because it is
 * wrong exactly where it is easy to overlook: a Linux host set to UTC.
 */
public record BridgeConfig(String sourceType, String sourceHost, String controlBindAddress,
                           int controlPort, List<OutputTargetConfig> targets,
                           PresentationConfig presentation, String competitionTimeZone) {

    /** Configuration without an explicit competition time zone. */
    public BridgeConfig(String sourceType, String sourceHost, String controlBindAddress,
                        int controlPort, List<OutputTargetConfig> targets,
                        PresentationConfig presentation) {
        this(sourceType, sourceHost, controlBindAddress, controlPort, targets, presentation, null);
    }

    public static final int WINLAUFEN_PORT = 4444;

    public BridgeConfig {
        targets = List.copyOf(targets);
        if (!"WINLAUFEN".equals(sourceType)) {
            throw new IllegalArgumentException("Unbekanntes Quellsystem");
        }
        if (presentation == null) {
            throw new IllegalArgumentException("Presentation Config fehlt");
        }
        Set<String> ids = new HashSet<>();
        for (OutputTargetConfig target : targets) {
            if (!ids.add(target.id())) {
                throw new IllegalArgumentException("Doppelte Target-ID: " + target.id());
            }
        }
    }
}
