package de.winlaufen.web.liveserver.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveServerConfigTest {

    @BeforeEach
    void clearPropertiesBeforeTest() {
        clearProperties();
    }

    @AfterEach
    void clearPropertiesAfterTest() {
        clearProperties();
    }

    private void clearProperties() {
        System.clearProperty("winlaufen.live.http.bind");
        System.clearProperty("winlaufen.live.http.port");
        System.clearProperty("winlaufen.live.websocket.bind");
        System.clearProperty("winlaufen.live.websocket.port");
        System.clearProperty("winlaufen.live.channel");
        System.clearProperty("winlaufen.live.secret");
    }

    @Test
    void systemConfigurationUsesTheFixedNetworkContractByDefault() {
        LiveServerConfig config = LiveServerConfig.system();

        assertEquals("0.0.0.0", config.httpBindAddress());
        assertEquals(44440, config.httpPort());
        assertEquals("0.0.0.0", config.webSocketBindAddress());
        assertEquals(44441, config.webSocketPort());
        assertEquals("local", config.channelId());
    }
}
