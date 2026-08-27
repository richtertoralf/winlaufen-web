package de.winlaufen.web.bridge.config;

import de.winlaufen.web.contract.PresentationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    void unchangedDefaultPortsProduceNoPortNotice() throws Exception {
        Path path = temp.resolve("config.properties");
        Files.writeString(path, "winlaufen.host=timing\noutput.mode=LOCAL\nhttp.port=8080\nwebsocket.port=8081\n");
        BridgeConfigStore.LoadResult loaded = new BridgeConfigStore(path).loadWithNotices();

        assertTrue(loaded.notices().stream().noneMatch(notice -> notice.contains("winlaufen.live.")));
        assertEquals(URI.create("ws://127.0.0.1:8081/bridge/v1/channels/local"),
                loaded.config().targets().getFirst().endpoint());
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
                        URI.create("ws://127.0.0.1:8081/bridge/v1/channels/local"), "local", "12345678"),
                new OutputTargetConfig("club", OutputTargetType.SELFHOST, true,
                        URI.create("wss://club.example/bridge/v1/channels/race"), "race", "abcdefgh"));
        var config = new BridgeConfig("WINLAUFEN", "host", "127.0.0.1", 8090, targets,
                new PresentationConfig(false, true, true, false, true));
        var store = new BridgeConfigStore(temp.resolve("nested/config.properties"));

        store.save(config);
        assertEquals(config, store.load());
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
                URI.create("ws://127.0.0.1:8081/bridge/v1/channels/local"), "local", "12345678");
        assertThrows(IllegalArgumentException.class, () -> new BridgeConfig("WINLAUFEN", "host",
                "127.0.0.1", 8090, List.of(target, target), PresentationConfig.defaults()));
    }

    @Test
    void internetProductRequiresWss() {
        assertThrows(IllegalArgumentException.class, () -> new OutputTargetConfig("remote",
                OutputTargetType.RICHTER_PROJECTS, true, URI.create("ws://example.test/x"),
                "race", "12345678"));
    }
}
