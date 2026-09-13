package de.winlaufen.web.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleBoundaryTest {
    @Test
    void protocolDiagnosisCannotEnterCanonicalOutputOrLiveServerCode() throws Exception {
        var root = java.nio.file.Path.of("").toAbsolutePath();
        if (!java.nio.file.Files.isDirectory(root.resolve("bridge"))) root = root.getParent();
        for (String directory : new String[]{"contract/src/main", "live-server/src/main",
                "bridge/src/main/java/de/winlaufen/web/bridge/state",
                "bridge/src/main/java/de/winlaufen/web/bridge/output",
                "bridge/src/main/java/de/winlaufen/web/bridge/startlist"}) {
            try (var paths = java.nio.file.Files.walk(root.resolve(directory))) {
                for (var path : paths.filter(java.nio.file.Files::isRegularFile).toList()) {
                    String source = java.nio.file.Files.readString(path);
                    org.junit.jupiter.api.Assertions.assertFalse(
                            source.contains("ProtocolVariant") || source.contains("protocolVariant")
                                    || source.contains("protocolWarning"), path.toString());
                }
            }
        }
    }

    @Test
    void bridgeClasspathContainsNeitherLiveServerNorViewer() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.winlaufen.web.liveserver.LiveServerMain"));
        assertNull(BridgeMain.class.getResource("/web-viewer/viewer.html"));
    }
}
