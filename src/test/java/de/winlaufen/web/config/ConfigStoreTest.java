package de.winlaufen.web.config;

import de.winlaufen.web.model.OutputMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @TempDir Path temp;

    @Test void persistsProperties() throws Exception {
        ConfigStore store = new ConfigStore(temp.resolve("nested/config.properties"));
        var expected = new AppConfig("192.168.1.20", OutputMode.LOCAL, 8090, 8091,
                new PublicDisplayConfig(false, true, true, false, true));
        store.save(expected);
        assertEquals(expected, store.load());
    }

    @Test void publicDisplayDefaultsAreSafeForNewAndExistingConfigurations() throws Exception {
        PublicDisplayConfig expected = new PublicDisplayConfig(true, true, false, true, false);
        assertEquals(expected, new ConfigStore(temp.resolve("missing.properties")).load().publicDisplay());
        Path old = temp.resolve("old.properties");
        Files.writeString(old, "winlaufen.host=localhost\noutput.mode=LOCAL\nhttp.port=8080\nwebsocket.port=8081\n");
        assertEquals(expected, new ConfigStore(old).load().publicDisplay());
    }

    @Test void rejectsUnsafeHostsAndDisabledModes() {
        assertThrows(IllegalArgumentException.class, () -> ConfigStore.validateHost("http://evil.test"));
        assertThrows(IllegalArgumentException.class, () -> ConfigStore.validateHost(" host "));
        ConfigStore store = new ConfigStore(temp.resolve("config.properties"));
        assertThrows(IllegalArgumentException.class, () -> store.save(new AppConfig("localhost", OutputMode.SELFHOST, 8080, 8081)));
        assertFalse(OutputMode.RICHTER_PROJECTS.enabled());
    }
}
