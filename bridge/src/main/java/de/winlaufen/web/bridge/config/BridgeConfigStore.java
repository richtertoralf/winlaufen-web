package de.winlaufen.web.bridge.config;

import de.winlaufen.web.contract.PresentationConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.IDN;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Loads and stores the organiser configuration as {@code java.util.Properties}.
 *
 * <p>A pre-modular configuration is migrated deterministically: source host and presentation
 * values are carried over and the former exclusive LOCAL output mode becomes the first regular
 * output target. Deployment parameters that now belong to the separate live-server process cannot
 * be migrated into this file and are reported as upgrade notices instead of being dropped silently.
 */
public final class BridgeConfigStore {

    /**
     * Known prototype development secret. It is deliberately kept working for the prototype
     * baseline; see the security section in README.md.
     */
    public static final String DEFAULT_LOCAL_SECRET = "local-development-secret";

    public static final int DEFAULT_LIVE_WEBSOCKET_PORT = 44441;
    public static final int DEFAULT_LIVE_HTTP_PORT = 44440;
    public static final int DEFAULT_CONTROL_PORT = 44442;
    public static final String DEFAULT_CONTROL_BIND = "0.0.0.0";

    private static final int FORMER_DEFAULT_LIVE_HTTP_PORT = 8080;
    private static final int FORMER_DEFAULT_LIVE_WEBSOCKET_PORT = 8081;
    private static final URI FORMER_DEFAULT_LOCAL_ENDPOINT =
            URI.create("ws://127.0.0.1:8081/bridge/v1/channels/local");
    private static final URI DEFAULT_LOCAL_ENDPOINT =
            URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local");

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9._:-]{0,251}[A-Za-z0-9])?");
    private static final int MAX_HOST_CHARS = 253;

    private final Path path;

    public BridgeConfigStore(Path path) {
        this.path = path;
    }

    /**
     * System property that moves the organiser configuration out of the user home. A system
     * service installation points it at a machine-wide file such as
     * {@code /etc/winlaufen-web/bridge.properties}.
     */
    public static final String CONFIG_PATH_PROPERTY = "winlaufen.bridge.config";

    public static BridgeConfigStore inUserHome() {
        return new BridgeConfigStore(
                Path.of(System.getProperty("user.home"), ".winlaufen-web", "config.properties"));
    }

    /**
     * The configured location, or the user-home default when {@link #CONFIG_PATH_PROPERTY} is
     * unset. The file format is identical in both cases.
     */
    public static BridgeConfigStore fromSystemProperties() {
        String configured = System.getProperty(CONFIG_PATH_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return inUserHome();
        }
        return new BridgeConfigStore(Path.of(configured.trim()));
    }

    public Path path() {
        return path;
    }

    /** Configuration plus any operator-visible notices produced while migrating an old file. */
    public record LoadResult(BridgeConfig config, List<String> notices) { }

    public BridgeConfig load() throws IOException {
        return loadWithNotices().config();
    }

    public LoadResult loadWithNotices() throws IOException {
        Properties values = new Properties();
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                values.load(input);
            }
        }
        List<String> notices = new ArrayList<>();
        String host = validateHost(
                values.getProperty("source.host", values.getProperty("winlaufen.host", "localhost")));
        int count = integer(values.getProperty("outputs.count"), -1);

        List<OutputTargetConfig> targets = new ArrayList<>();
        if (count < 0) {
            targets.add(migrateLegacyLocalTarget(values, notices));
        } else {
            for (int index = 0; index < count; index++) {
                targets.add(target(values, index, notices));
            }
        }

        BridgeConfig config = new BridgeConfig("WINLAUFEN", host,
                values.getProperty("bridge.control.bind", DEFAULT_CONTROL_BIND),
                port(values.getProperty("bridge.control.port"), DEFAULT_CONTROL_PORT),
                targets, presentation(values));
        return new LoadResult(config, List.copyOf(notices));
    }

    /**
     * Turns the former exclusive LOCAL output mode into a regular output target. The old browser
     * WebSocket port is reused for the local ingest endpoint; the old HTTP port now belongs to the
     * live-server process and is reported instead of silently discarded. Exact former default
     * ports are migrated to the fixed current network contract, while individual values remain.
     */
    private static OutputTargetConfig migrateLegacyLocalTarget(Properties values, List<String> notices) {
        boolean legacy = values.getProperty("winlaufen.host") != null
                || values.getProperty("output.mode") != null
                || values.getProperty("websocket.port") != null
                || values.getProperty("http.port") != null;

        int webSocketPort = migratedLegacyPort(values.getProperty("websocket.port"),
                DEFAULT_LIVE_WEBSOCKET_PORT, FORMER_DEFAULT_LIVE_WEBSOCKET_PORT);
        int httpPort = migratedLegacyPort(values.getProperty("http.port"),
                DEFAULT_LIVE_HTTP_PORT, FORMER_DEFAULT_LIVE_HTTP_PORT);

        if (legacy) {
            notices.add("Alte Konfiguration übernommen: LOCAL wurde zum Output Target \"local\".");
            if (webSocketPort != DEFAULT_LIVE_WEBSOCKET_PORT || httpPort != DEFAULT_LIVE_HTTP_PORT) {
                notices.add("Die früheren Webports gehören jetzt zum Live-Server-Prozess. "
                        + "Starte ihn mit: -Dwinlaufen.live.http.port=" + httpPort
                        + " -Dwinlaufen.live.websocket.port=" + webSocketPort);
            }
            String mode = values.getProperty("output.mode");
            if (mode != null && !"LOCAL".equals(mode)) {
                notices.add("Früherer Output-Modus \"" + mode
                        + "\" war in v0.1 nicht aktiv und wurde als LOCAL-Target übernommen.");
            }
        }
        return new OutputTargetConfig("local", OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:" + webSocketPort + "/bridge/v1/channels/local"),
                "local", values.getProperty("local.secret", DEFAULT_LOCAL_SECRET));
    }

    private static PresentationConfig presentation(Properties values) {
        return new PresentationConfig(
                bool(values, "presentation.showClub", bool(values, "public.showClub", true)),
                bool(values, "presentation.showAssociation", bool(values, "public.showAssociation", true)),
                bool(values, "presentation.showNation", bool(values, "public.showNation", false)),
                bool(values, "presentation.showShooting", bool(values, "public.showShooting", true)),
                bool(values, "presentation.showMessages", bool(values, "public.showMessages", false)));
    }

    public void save(BridgeConfig config) throws IOException {
        validateHost(config.sourceHost());
        Properties values = new Properties();
        values.setProperty("config.version", "2");
        values.setProperty("source.type", config.sourceType());
        values.setProperty("source.host", config.sourceHost());
        values.setProperty("bridge.control.bind", config.controlBindAddress());
        values.setProperty("bridge.control.port", Integer.toString(config.controlPort()));
        values.setProperty("outputs.count", Integer.toString(config.targets().size()));
        for (int index = 0; index < config.targets().size(); index++) {
            OutputTargetConfig target = config.targets().get(index);
            String prefix = "outputs." + index + ".";
            values.setProperty(prefix + "id", target.id());
            values.setProperty(prefix + "type", target.type().name());
            values.setProperty(prefix + "enabled", Boolean.toString(target.enabled()));
            values.setProperty(prefix + "endpoint", target.endpoint().toString());
            values.setProperty(prefix + "channelId", target.channelId());
            values.setProperty(prefix + "secret", target.secret());
        }
        PresentationConfig presentation = config.presentation();
        values.setProperty("presentation.showClub", Boolean.toString(presentation.showClub()));
        values.setProperty("presentation.showAssociation", Boolean.toString(presentation.showAssociation()));
        values.setProperty("presentation.showNation", Boolean.toString(presentation.showNation()));
        values.setProperty("presentation.showShooting", Boolean.toString(presentation.showShooting()));
        values.setProperty("presentation.showMessages", Boolean.toString(presentation.showPublicMessages()));
        writeAtomically(values);
    }

    private void writeAtomically(Properties values) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "config", ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            values.store(output, "WinLaufen Web Bridge");
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static OutputTargetConfig target(Properties values, int index, List<String> notices) {
        String prefix = "outputs." + index + ".";
        URI endpoint = URI.create(required(values, prefix + "endpoint"));
        if (FORMER_DEFAULT_LOCAL_ENDPOINT.equals(endpoint)) {
            endpoint = DEFAULT_LOCAL_ENDPOINT;
            notices.add("Früherer lokaler Ingest-Default auf Port 44441 migriert.");
        }
        return new OutputTargetConfig(
                required(values, prefix + "id"),
                OutputTargetType.valueOf(required(values, prefix + "type")),
                bool(values, prefix + "enabled", false),
                endpoint,
                required(values, prefix + "channelId"),
                required(values, prefix + "secret"));
    }

    public static String validateHost(String raw) {
        if (raw == null || raw.isBlank() || !raw.equals(raw.trim())
                || raw.contains("/") || raw.contains("%")) {
            throw new IllegalArgumentException("Ungültiger WinLaufen-Host");
        }
        String ascii = IDN.toASCII(raw);
        if (ascii.length() > MAX_HOST_CHARS || !HOST.matcher(ascii).matches()) {
            throw new IllegalArgumentException("Ungültiger WinLaufen-Host");
        }
        return raw;
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Fehlende Konfiguration: " + key);
        }
        return value;
    }

    private static boolean bool(Properties values, String key, boolean fallback) {
        String value = values.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int integer(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int port(String value, int fallback) {
        int parsed = integer(value, fallback);
        return parsed > 0 && parsed <= 65535 ? parsed : fallback;
    }

    private static int migratedLegacyPort(String value, int currentDefault, int formerDefault) {
        int parsed = port(value, currentDefault);
        return parsed == formerDefault ? currentDefault : parsed;
    }
}
