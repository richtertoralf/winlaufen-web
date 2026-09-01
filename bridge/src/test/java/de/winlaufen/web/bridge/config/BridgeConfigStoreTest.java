package de.winlaufen.web.bridge.config;

import de.winlaufen.web.contract.PresentationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeConfigStoreTest {

    @TempDir
    Path temp;

    @Test
    void migratesLegacyConfigToLocalTarget() throws Exception {
        Path path = temp.resolve("config.properties");
        Files.writeString(path, """
                winlaufen.host=timing
                output.mode=LOCAL
                public.showClub=false
                public.showMessages=true
                """);
        BridgeConfig config = new BridgeConfigStore(path).load();

        assertEquals("timing", config.sourceHost());
        assertEquals(1, config.targets().size());
        assertEquals(OutputTargetType.LOCAL, config.targets().getFirst().type());
        assertEquals("local", config.targets().getFirst().id());
        assertFalse(config.presentation().showClub());
        assertTrue(config.presentation().showPublicMessages());
    }

    @Test
    void migratesLegacyWebSocketPortIntoTheLocalIngestEndpoint() throws Exception {
        Path path = temp.resolve("config.properties");
        Files.writeString(path, """
                winlaufen.host=timing
                output.mode=LOCAL
                http.port=9080
                websocket.port=9081
                """);
        BridgeConfigStore.LoadResult loaded = new BridgeConfigStore(path).loadWithNotices();

        assertEquals(URI.create("ws://127.0.0.1:9081/bridge/v1/channels/local"),
                loaded.config().targets().getFirst().endpoint());
        assertTrue(loaded.notices().stream().anyMatch(notice -> notice.contains("9080")),
                "the old HTTP port belongs to the live server and must be reported: "
                        + loaded.notices());
        assertTrue(loaded.notices().stream()
                .anyMatch(notice -> notice.contains("winlaufen.live.websocket.port=9081")));
    }

    @Test
    void currentDefaultPortsProduceNoPortNotice() throws Exception {
        Path path = temp.resolve("config.properties");
        Files.writeString(path, "winlaufen.host=timing\noutput.mode=LOCAL\nhttp.port=44440\nwebsocket.port=44441\n");
        BridgeConfigStore.LoadResult loaded = new BridgeConfigStore(path).loadWithNotices();

        assertTrue(loaded.notices().stream().noneMatch(notice -> notice.contains("winlaufen.live.")));
        assertEquals(URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"),
                loaded.config().targets().getFirst().endpoint());
    }

    @Test
    void formerDefaultPortsMigrateToTheFixedNetworkContract() throws Exception {
        Path path = temp.resolve("config.properties");
        Files.writeString(path,
                "winlaufen.host=timing\noutput.mode=LOCAL\nhttp.port=8080\nwebsocket.port=8081\n");
        BridgeConfigStore.LoadResult loaded = new BridgeConfigStore(path).loadWithNotices();

        assertTrue(loaded.notices().stream().noneMatch(notice -> notice.contains("winlaufen.live.")));
        assertEquals(URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"),
                loaded.config().targets().getFirst().endpoint());
    }

    @Test
    void migratesJavaPropertiesEscapedFormerLocalEndpoint() throws Exception {
        Path path = temp.resolve("config.properties");
        Properties properties = installedTargetProperties(
                "ws://127.0.0.1:8081/bridge/v1/channels/local");
        try (var output = Files.newOutputStream(path)) {
            properties.store(output, "realistic Java properties output");
        }
        assertTrue(Files.readString(path).contains("ws\\://127.0.0.1\\:8081"));

        BridgeConfigStore.LoadResult loaded = new BridgeConfigStore(path).loadWithNotices();

        assertEquals(URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"),
                loaded.config().targets().getFirst().endpoint());
        assertTrue(loaded.notices().stream().anyMatch(notice -> notice.contains("44441")));
    }

    @Test
    void leavesNonDefaultInstalledEndpointsUnchanged() throws Exception {
        for (String endpoint : List.of(
                "ws://127.0.0.1:9081/bridge/v1/channels/local",
                "ws://192.168.1.20:8081/bridge/v1/channels/local",
                "ws://127.0.0.1:44441/bridge/v1/channels/local")) {
            Path path = temp.resolve("config-" + Math.abs(endpoint.hashCode()) + ".properties");
            Properties properties = installedTargetProperties(endpoint);
            try (var output = Files.newOutputStream(path)) {
                properties.store(output, "realistic Java properties output");
            }

            BridgeConfigStore.LoadResult loaded = new BridgeConfigStore(path).loadWithNotices();

            assertEquals(URI.create(endpoint), loaded.config().targets().getFirst().endpoint());
            assertTrue(loaded.notices().isEmpty());
        }
    }

    @Test
    void missingFileYieldsSafePresentationDefaultsAndOneLocalTarget() throws Exception {
        BridgeConfigStore.LoadResult loaded =
                new BridgeConfigStore(temp.resolve("missing.properties")).loadWithNotices();

        assertEquals(PresentationConfig.defaults(), loaded.config().presentation());
        assertEquals("localhost", loaded.config().sourceHost());
        assertEquals(1, loaded.config().targets().size());
        assertEquals(BridgeConfigStore.DEFAULT_CONTROL_PORT, loaded.config().controlPort());
        assertEquals(BridgeConfigStore.DEFAULT_CONTROL_BIND, loaded.config().controlBindAddress());
        assertTrue(loaded.notices().isEmpty(), "a fresh installation has nothing to migrate");
    }

    @Test
    void persistsMultipleTargetsAndPresentation() throws Exception {
        var targets = List.of(
                new OutputTargetConfig("local", OutputTargetType.LOCAL, true,
                        URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"), "local", "12345678"),
                new OutputTargetConfig("club", OutputTargetType.SELFHOST, true,
                        URI.create("wss://club.example/bridge/v1/channels/race"), "race", "abcdefgh"));
        var config = new BridgeConfig("WINLAUFEN", "host", "0.0.0.0", 44442, targets,
                new PresentationConfig(false, true, true, false, true));
        var store = new BridgeConfigStore(temp.resolve("nested/config.properties"));

        store.save(config);
        assertEquals(config, store.load());
    }

    /** A temporary presentation node on a rented cloud VM survives a full save/load round trip. */
    @Test
    void persistsASelfHostedTargetAtAPublicIpAddress() throws Exception {
        var targets = List.of(
                new OutputTargetConfig("local", OutputTargetType.LOCAL, true,
                        URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"), "local", "12345678"),
                new OutputTargetConfig("selfhost-203-0-113-7-local", OutputTargetType.SELFHOST, true,
                        URI.create("ws://203.0.113.7:44441/bridge/v1/channels/local"), "local",
                        BridgeConfigStore.DEFAULT_LOCAL_SECRET));
        var config = new BridgeConfig("WINLAUFEN", "host", "0.0.0.0", 44442, targets,
                PresentationConfig.defaults());
        var store = new BridgeConfigStore(temp.resolve("cloud/config.properties"));

        store.save(config);
        var loaded = store.loadWithNotices();

        assertEquals(config, loaded.config());
        assertTrue(loaded.notices().isEmpty(), "an accepted endpoint is not a migration case");
    }

    @Test
    void atomicallyReplacesConfigurationInGroupWritableDirectory() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("protected"));
        if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwxrwx---"));
        }
        Path path = directory.resolve("config.properties");
        var store = new BridgeConfigStore(path);
        BridgeConfig initial = store.load();

        store.save(initial);
        BridgeConfig changed = new BridgeConfig(initial.sourceType(), "timing-pc",
                initial.controlBindAddress(), initial.controlPort(), initial.targets(),
                initial.presentation());
        store.save(changed);

        assertEquals("timing-pc", store.load().sourceHost());
        try (var entries = Files.list(directory)) {
            assertEquals(List.of(path), entries.toList());
        }
    }

    @Test
    void savedFileContainsOnlyTheNewConfigurationModel() throws Exception {
        Path path = temp.resolve("config.properties");
        Files.writeString(path, "winlaufen.host=timing\noutput.mode=LOCAL\nhttp.port=9080\nwebsocket.port=9081\n");
        var store = new BridgeConfigStore(path);
        store.save(store.load());

        String saved = Files.readString(path);
        assertTrue(saved.contains("config.version=2"));
        assertTrue(saved.contains("outputs.count=1"));
        assertFalse(saved.contains("winlaufen.host"));
        assertFalse(saved.contains("output.mode"));
        assertFalse(saved.contains("http.port"));
        assertFalse(saved.contains("websocket.port"));
        assertFalse(saved.contains("public.show"));
    }

    @Test
    void rejectsUnsafeWinLaufenHosts() {
        assertThrows(IllegalArgumentException.class,
                () -> BridgeConfigStore.validateHost("http://evil.test"));
        assertThrows(IllegalArgumentException.class, () -> BridgeConfigStore.validateHost(" host "));
        assertThrows(IllegalArgumentException.class, () -> BridgeConfigStore.validateHost("a/b"));
        assertThrows(IllegalArgumentException.class, () -> BridgeConfigStore.validateHost("host%00"));
        assertThrows(IllegalArgumentException.class, () -> BridgeConfigStore.validateHost(""));
        assertThrows(IllegalArgumentException.class, () -> BridgeConfigStore.validateHost(null));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeConfigStore.validateHost("x".repeat(254)));
        assertEquals("10.77.0.1", BridgeConfigStore.validateHost("10.77.0.1"));
        assertEquals("timing-pc", BridgeConfigStore.validateHost("timing-pc"));
    }

    @Test
    void rejectsDuplicateTargetIdentifiers() {
        var target = new OutputTargetConfig("local", OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"), "local", "12345678");
        assertThrows(IllegalArgumentException.class, () -> new BridgeConfig("WINLAUFEN", "host",
                "0.0.0.0", 44442, List.of(target, target), PresentationConfig.defaults()));
    }

    @Test
    void internetProductRequiresWss() {
        assertThrows(IllegalArgumentException.class, () -> new OutputTargetConfig("remote",
                OutputTargetType.RICHTER_PROJECTS, true, URI.create("ws://example.test/x"),
                "race", "12345678"));
    }

    private static Properties installedTargetProperties(String endpoint) {
        Properties properties = new Properties();
        properties.setProperty("config.version", "2");
        properties.setProperty("source.type", "WINLAUFEN");
        properties.setProperty("source.host", "localhost");
        properties.setProperty("bridge.control.bind", "0.0.0.0");
        properties.setProperty("bridge.control.port", "44442");
        properties.setProperty("outputs.count", "1");
        properties.setProperty("outputs.0.id", "local");
        properties.setProperty("outputs.0.type", "LOCAL");
        properties.setProperty("outputs.0.enabled", "true");
        properties.setProperty("outputs.0.endpoint", endpoint);
        properties.setProperty("outputs.0.channelId", "local");
        properties.setProperty("outputs.0.secret", "local-development-secret");
        return properties;
    }
}
