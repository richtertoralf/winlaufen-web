package de.winlaufen.web.bridge.config;

import de.winlaufen.web.contract.PresentationConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The single organiser configuration. Owned by the bridge, edited only through Bridge Control. */
public record BridgeConfig(String sourceType, String sourceHost, String controlBindAddress,
                           int controlPort, List<OutputTargetConfig> targets,
                           PresentationConfig presentation) {

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
