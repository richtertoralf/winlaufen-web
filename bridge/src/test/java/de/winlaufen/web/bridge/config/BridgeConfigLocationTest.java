package de.winlaufen.web.bridge.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A system service installation keeps the organiser configuration outside the user home, for
 * example in {@code /etc/winlaufen-web/bridge.properties}. The file format stays identical.
 */
class BridgeConfigLocationTest {

    @TempDir
    Path temp;

    @AfterEach
    void clearProperty() {
        System.clearProperty(BridgeConfigStore.CONFIG_PATH_PROPERTY);
    }

    @Test
    void withoutTheSystemPropertyTheUserHomeLocationIsUsed() {
        System.clearProperty(BridgeConfigStore.CONFIG_PATH_PROPERTY);
        Path expected = Path.of(System.getProperty("user.home"), ".winlaufen-web", "config.properties");
        assertEquals(expected, BridgeConfigStore.fromSystemProperties().path());
    }

    @Test
    void blankSystemPropertyFallsBackToTheUserHomeLocation() {
        System.setProperty(BridgeConfigStore.CONFIG_PATH_PROPERTY, "   ");
        assertEquals(BridgeConfigStore.inUserHome().path(),
                BridgeConfigStore.fromSystemProperties().path());
    }

    @Test
    void systemPropertyMovesTheConfigurationToAMachineWideFile() throws Exception {
        Path configured = temp.resolve("etc/winlaufen-web/bridge.properties");
        System.setProperty(BridgeConfigStore.CONFIG_PATH_PROPERTY, configured.toString());

        BridgeConfigStore store = BridgeConfigStore.fromSystemProperties();
        assertEquals(configured, store.path());

        // A missing file must still yield the documented first-installation defaults.
        BridgeConfig defaults = store.load();
        assertEquals("localhost", defaults.sourceHost());
        assertEquals(BridgeConfigStore.DEFAULT_CONTROL_PORT, defaults.controlPort());

        store.save(defaults);
        assertTrue(Files.exists(configured), "the store must create the configured location");
        assertEquals(defaults, store.load());
    }

    @Test
    void anInstallerGeneratedAllInOneConfigurationIsReadBackUnchanged() throws Exception {
        Path configured = temp.resolve("bridge.properties");
        // Exactly the file the Linux and Windows installers write for All-in-One.
        Files.writeString(configured, """
                config.version=2
                source.type=WINLAUFEN
                source.host=127.0.0.1
                bridge.control.bind=127.0.0.1
                bridge.control.port=8090
                outputs.count=1
                outputs.0.id=local
                outputs.0.type=LOCAL
                outputs.0.enabled=true
                outputs.0.endpoint=ws://127.0.0.1:8081/bridge/v1/channels/local
                outputs.0.channelId=local
                outputs.0.secret=local-development-secret
                presentation.showClub=true
                presentation.showAssociation=true
                presentation.showNation=false
                presentation.showShooting=true
                presentation.showMessages=false
                """);
        System.setProperty(BridgeConfigStore.CONFIG_PATH_PROPERTY, configured.toString());

        BridgeConfigStore.LoadResult loaded = BridgeConfigStore.fromSystemProperties().loadWithNotices();
        BridgeConfig config = loaded.config();

        assertEquals("127.0.0.1", config.sourceHost());
        assertEquals(1, config.targets().size());
        assertEquals("local", config.targets().getFirst().id());
        assertEquals(OutputTargetType.LOCAL, config.targets().getFirst().type());
        assertTrue(config.targets().getFirst().enabled());
        assertEquals("ws://127.0.0.1:8081/bridge/v1/channels/local",
                config.targets().getFirst().endpoint().toString());
        assertTrue(loaded.notices().isEmpty(), "a freshly installed configuration has nothing to migrate");
    }

    @Test
    void anInstallerGeneratedBridgeOnlyConfigurationIsValidWithoutAnyTarget() throws Exception {
        Path configured = temp.resolve("bridge.properties");
        // Exactly the file the installers write for Bridge only: no output target yet.
        Files.writeString(configured, """
                config.version=2
                source.type=WINLAUFEN
                source.host=127.0.0.1
                bridge.control.bind=127.0.0.1
                bridge.control.port=8090
                outputs.count=0
                presentation.showClub=true
                presentation.showAssociation=true
                presentation.showNation=false
                presentation.showShooting=true
                presentation.showMessages=false
                """);
        System.setProperty(BridgeConfigStore.CONFIG_PATH_PROPERTY, configured.toString());

        BridgeConfig config = BridgeConfigStore.fromSystemProperties().load();
        assertTrue(config.targets().isEmpty(),
                "Bridge only is a valid installation without any output target");
        assertEquals("127.0.0.1", config.sourceHost());
    }
}
