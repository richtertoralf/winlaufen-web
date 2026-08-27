package de.winlaufen.web.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleBoundaryTest {
    @Test
    void contractClasspathContainsNoRuntimeImplementation() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.winlaufen.web.bridge.BridgeMain"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.winlaufen.web.liveserver.LiveServerMain"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.java_websocket.WebSocket"));
    }
}
