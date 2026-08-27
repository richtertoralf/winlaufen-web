package de.winlaufen.web.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleBoundaryTest {
    @Test
    void bridgeClasspathContainsNeitherLiveServerNorViewer() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.winlaufen.web.liveserver.LiveServerMain"));
        assertNull(BridgeMain.class.getResource("/web-viewer/viewer.html"));
    }
}
