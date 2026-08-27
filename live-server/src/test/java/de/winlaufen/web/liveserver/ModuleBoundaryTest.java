package de.winlaufen.web.liveserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleBoundaryTest {
    @Test
    void liveServerClasspathContainsNoBridgeOrWinLaufenProtocolClasses() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.winlaufen.web.bridge.BridgeMain"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.winlaufen.web.bridge.source.winlaufen.WinLaufenProtocolReader"));
    }
}
