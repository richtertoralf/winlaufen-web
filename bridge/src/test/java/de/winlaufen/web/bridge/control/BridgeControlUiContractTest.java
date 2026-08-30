package de.winlaufen.web.bridge.control;

import de.winlaufen.web.bridge.output.OutputConnectionState;
import de.winlaufen.web.contract.SourceHealth;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Product semantics of the Bridge Control surface.
 *
 * <p>The project has no JavaScript runtime available and must not add one, so the rules the
 * surface has to keep are pinned by source assertions that fail if they are weakened again. The
 * backend runtime values themselves are asserted as Java enums, so a translation can never be
 * mistaken for a protocol change.
 */
class BridgeControlUiContractTest {

    @Test
    void showsTheSpeakerWebProductNameAndTheSurfaceName() throws Exception {
        String html = resource("/bridge-control/index.html");

        assertTrue(html.contains("<title>Bridge Control · WinLaufen Sprecher Web</title>"),
                "the browser tab names the product and the surface");
        assertTrue(html.contains("<strong>WinLaufen Sprecher Web<span>Bridge Control</span></strong>"),
                "the header names the product and the surface");
        assertFalse(html.contains("WinLaufen Web"), "the former ambiguous product name is gone");
    }

    @Test
    void keepsBackendRuntimeValuesUntouched() {
        assertEquals("DISCONNECTED,CONNECTED,STALE", names(SourceHealth.values()),
                "source health values are a protocol contract and must not be renamed");
        assertEquals("DISABLED,CONNECTING,CONNECTED,STALE,RETRY_WAIT",
                names(OutputConnectionState.values()),
                "output states are a protocol contract and must not be renamed");
    }

    @Test
    void translatesEveryRuntimeValueForNormalUsers() throws Exception {
        String script = resource("/bridge-control/control.js");

        for (SourceHealth health : SourceHealth.values()) {
            assertTrue(script.contains(health.name() + ": '"),
                    "missing source translation for " + health);
        }
        for (OutputConnectionState state : OutputConnectionState.values()) {
            assertTrue(script.contains(state.name() + ": '"),
                    "missing target translation for " + state);
        }
        assertTrue(script.contains("CONNECTED: 'Verbunden'"));
        assertTrue(script.contains("DISCONNECTED: 'Nicht verbunden'"));
        assertTrue(script.contains("STALE: 'Keine Daten – Verbindung wird erneuert'"));
        assertTrue(script.contains("CONNECTING: 'Verbinde …'"));
        assertTrue(script.contains("RETRY_WAIT: 'Nicht erreichbar – neuer Versuch läuft'"));
        assertTrue(script.contains("STALE: 'Verbunden, aber keine Bestätigung'"));
        assertTrue(script.contains("DISABLED: 'Nicht in Verwendung'"));
        assertTrue(script.contains("|| state"), "an unknown state stays visible instead of vanishing");
    }

    @Test
    void hidesTheAckRevisionButKeepsTheLastError() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertFalse(script.contains("ACK "), "the ACK revision is no longer part of the normal view");
        assertFalse(script.contains("lastAckedSourceRevision"),
                "the normal view no longer renders the acknowledged revision");
        assertTrue(script.contains("runtime.lastError"), "the last error stays visible as a hint");
    }

    @Test
    void explainsHowToEnableTheSpeakerInterfaceWhenTheSourceIsDisconnected() throws Exception {
        String html = resource("/bridge-control/index.html");
        String script = resource("/bridge-control/control.js");

        assertTrue(html.contains("Abwicklung → Sprecher-PC… → Verbinden"),
                "the help text names the exact WinLaufen menu path");
        assertTrue(script.contains("hidden = status.sourceHealth !== 'DISCONNECTED'"),
                "the help text appears only while the source is disconnected");
    }

    private static String names(Enum<?>[] values) {
        StringBuilder builder = new StringBuilder();
        for (Enum<?> value : values) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value.name());
        }
        return builder.toString();
    }

    private static String resource(String name) throws Exception {
        try (var input = BridgeControlUiContractTest.class.getResourceAsStream(name)) {
            assertNotNull(input, "missing resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
