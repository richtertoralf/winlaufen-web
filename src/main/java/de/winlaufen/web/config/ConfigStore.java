package de.winlaufen.web.config;

import de.winlaufen.web.model.OutputMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.IDN;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.regex.Pattern;

public final class ConfigStore {
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9._:-]{0,251}[A-Za-z0-9])?");
    private final Path path;

    public ConfigStore(Path path) { this.path = path; }
    public static ConfigStore inUserHome() {
        return new ConfigStore(Path.of(System.getProperty("user.home"), ".winlaufen-web", "config.properties"));
    }

    public AppConfig load() throws IOException {
        AppConfig defaults = AppConfig.defaults();
        if (!Files.exists(path)) return defaults;
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) { values.load(input); }
        String host = validateHost(values.getProperty("winlaufen.host", defaults.winLaufenHost()));
        OutputMode mode;
        try { mode = OutputMode.valueOf(values.getProperty("output.mode", "LOCAL")); }
        catch (IllegalArgumentException ex) { mode = OutputMode.LOCAL; }
        if (!mode.enabled()) mode = OutputMode.LOCAL;
        return new AppConfig(host, mode,
                port(values.getProperty("http.port"), defaults.httpPort()),
                port(values.getProperty("websocket.port"), defaults.webSocketPort()),
                new PublicDisplayConfig(
                        bool(values, "public.showClub", defaults.publicDisplay().showClub()),
                        bool(values, "public.showAssociation", defaults.publicDisplay().showAssociation()),
                        bool(values, "public.showNation", defaults.publicDisplay().showNation()),
                        bool(values, "public.showShooting", defaults.publicDisplay().showShooting()),
                        bool(values, "public.showMessages", defaults.publicDisplay().showPublicMessages())));
    }

    public void save(AppConfig config) throws IOException {
        validateHost(config.winLaufenHost());
        if (!config.outputMode().enabled()) throw new IllegalArgumentException("Output mode is disabled");
        Properties values = new Properties();
        values.setProperty("winlaufen.host", config.winLaufenHost());
        values.setProperty("output.mode", config.outputMode().name());
        values.setProperty("http.port", Integer.toString(config.httpPort()));
        values.setProperty("websocket.port", Integer.toString(config.webSocketPort()));
        values.setProperty("public.showClub", Boolean.toString(config.publicDisplay().showClub()));
        values.setProperty("public.showAssociation", Boolean.toString(config.publicDisplay().showAssociation()));
        values.setProperty("public.showNation", Boolean.toString(config.publicDisplay().showNation()));
        values.setProperty("public.showShooting", Boolean.toString(config.publicDisplay().showShooting()));
        values.setProperty("public.showMessages", Boolean.toString(config.publicDisplay().showPublicMessages()));
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "config", ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            values.store(output, "WinLaufen Web v0.1");
        }
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String validateHost(String raw) {
        if (raw == null || raw.isBlank() || !raw.equals(raw.trim()) || raw.contains("/") || raw.contains("%"))
            throw new IllegalArgumentException("Ungültiger WinLaufen-Host");
        String ascii = IDN.toASCII(raw);
        if (ascii.length() > 253 || !HOST.matcher(ascii).matches())
            throw new IllegalArgumentException("Ungültiger WinLaufen-Host");
        return raw;
    }

    private static int port(String raw, int fallback) {
        if (raw == null) return fallback;
        try { int value = Integer.parseInt(raw); return value >= 1 && value <= 65535 ? value : fallback; }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static boolean bool(Properties values, String key, boolean fallback) {
        String raw = values.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw);
    }
}
