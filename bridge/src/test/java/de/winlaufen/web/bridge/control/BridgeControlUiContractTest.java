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

    @Test
    void asksWhereWinLaufenRunsInsteadOfShowingTechnicalSourceFields() throws Exception {
        String html = resource("/bridge-control/index.html");

        assertTrue(html.contains("Wo läuft WinLaufen?"), "the source section asks a plain question");
        assertTrue(html.contains("value=\"local\"> Auf diesem Computer"));
        assertTrue(html.contains("value=\"remote\"> Auf einem anderen Computer"));
        assertTrue(html.contains("Hostname oder IPv4-Adresse, z. B. WINLAUFEN-PC oder 192.168.95.20"),
                "the remote field explains what belongs in it");
        assertFalse(html.contains("WINLAUFEN\" disabled"),
                "the source system is not variable and is no longer shown as a field");
        assertTrue(html.contains("Sprecher-PC-Schnittstelle · TCP 4444"),
                "the fixed protocol port stays visible as information");
        assertFalse(html.contains("name=\"sourcePort\""), "the fixed port is never an input");
    }

    @Test
    void showsTheWinLaufenComputerFieldOnlyForAnotherComputer() throws Exception {
        String html = resource("/bridge-control/index.html");
        String css = resource("/bridge-control/control.css");
        String script = resource("/bridge-control/control.js");

        assertTrue(html.contains("<label id=\"source-host-field\" hidden>"),
                "the host block starts hidden and is only shown on demand");
        // Ohne diese Regel wuerde label{display:block} das hidden-Attribut aushebeln.
        assertTrue(collapse(css).contains("[hidden]{display:none!important}"),
                "an author rule must not defeat the hidden attribute");
        assertTrue(script.contains("hostField.hidden = !remote"),
                "the host block follows the choice");
        assertTrue(script.contains("hostInput.required = remote"),
                "a hidden field never blocks the form with an invisible validation error");
    }

    @Test
    void keepsTheUnchangedSourceHostContract() throws Exception {
        String html = resource("/bridge-control/index.html");
        String script = resource("/bridge-control/control.js");

        assertTrue(html.contains("<input type=\"hidden\" name=\"sourceHost\">"),
                "the POST still carries exactly sourceHost");
        assertEquals(1, occurrences(html, "name=\"sourceHost\""),
                "there is exactly one sourceHost field in the request");
        assertTrue(script.contains("body.delete('sourceLocation')"),
                "the radio group is a form helper and must not reach the API");
        assertTrue(script.contains("const LOCAL_SOURCE_HOST = '127.0.0.1'"));
    }

    @Test
    void adoptsAStoredHostVerbatimAndCanonicalisesOnlyOnAnExplicitChoice() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("form.sourceHost.value = host;"),
                "loading keeps the stored host byte for byte");
        assertTrue(script.contains("form.sourceHost.value = remote ? hostInput.value.trim() : LOCAL_SOURCE_HOST;"),
                "only a user choice replaces the host with the loopback address");
        assertTrue(script.contains("radio.onchange = sourceLocationChanged"),
                "canonicalisation is bound to the radio change, not to loading");
        assertTrue(script.contains("value === 'localhost'") && script.contains("/^127\\.\\d+\\.\\d+\\.\\d+$/"),
                "loopback detection covers localhost and the 127.0.0.0/8 range");
        assertTrue(script.contains("value === '::1'"), "IPv6 loopback counts as this computer");
    }

    @Test
    void explainsThatAHostIsNeitherAUrlNorRestrictedToIpv4() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("ohne http:// oder https://"),
                "a pasted URL is explained in the browser before the request is sent");
        assertTrue(script.contains("setCustomValidity"), "the hint uses standard form validation");
        assertFalse(script.contains("hostname only") || script.contains("nur IPv4"),
                "hostnames stay supported because the backend resolves them");
    }

    @Test
    void presentsTheBuiltInLocalTargetAsANamedLineInsteadOfAConfigurationBlock() throws Exception {
        String html = resource("/bridge-control/index.html");
        String localTemplate = between(html, "<template id=\"local-target-template\">", "</template>");

        assertTrue(localTemplate.contains("Web View auf diesem Computer"),
                "the built-in target carries a name a user understands");
        for (String field : new String[]{"id", "type", "enabled", "endpoint", "channelId", "secret"}) {
            assertTrue(localTemplate.contains("<input type=\"hidden\" data-name=\"" + field + "\">"),
                    field + " travels with the request but is not shown");
        }
        assertEquals(6, occurrences(localTemplate, "<input "),
                "the built-in target has no input other than the six hidden ones");
        assertEquals(6, occurrences(localTemplate, "type=\"hidden\""),
                "no field of the built-in target is editable");
        assertFalse(localTemplate.contains("class=\"remove\""),
                "the built-in target cannot be removed in the normal view");
        assertFalse(localTemplate.contains("type=\"checkbox\""),
                "the built-in target cannot be switched off by accident");
    }

    @Test
    void detectsTheBuiltInLocalTargetConservatively() throws Exception {
        String script = resource("/bridge-control/control.js");
        String detection = collapse(between(script, "function isBuiltInLocalTarget", "\n}\n"));

        assertTrue(detection.contains("value.type !== 'LOCAL'"), "type LOCAL is required");
        assertTrue(detection.contains("new URL(value.endpoint).hostname"),
                "the endpoint host decides, not the freely chosen id");
        assertTrue(detection.contains("return isLocalSourceHost(host)"),
                "only a loopback endpoint counts as the built-in target");
        assertTrue(detection.contains("} catch (error) { return false; }"),
                "an unparsable endpoint falls back to the technical rendering");
        assertFalse(script.contains("value.id === 'local'"),
                "the id is not reserved and must not decide this");
    }

    @Test
    void keepsEveryHiddenValueOfTheBuiltInTargetInTheRequest() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("field('id').value = value.id;"), "the stored id is kept as is");
        assertTrue(script.contains("field('endpoint').value = value.endpoint;"),
                "the stored endpoint is kept byte for byte and never recomputed");
        assertTrue(script.contains("field('channelId').value = value.channelId;"),
                "the stored channel is kept as is");
        assertTrue(script.contains("field('enabled').value = value.enabled ? 'on' : '';"),
                "the stored enabled flag is carried over instead of being forced on");
        assertTrue(script.contains("field('secret').value = '';"),
                "an empty secret keeps the stored one, exactly as before");
        assertTrue(script.contains("body.set(`target.${index}.${input.dataset.name}`"),
                "the serialisation still walks every data-name field of every target");
        assertFalse(script.contains("crypto.randomUUID") || script.contains("Math.random"),
                "no new identifier is generated for an existing target");
    }

    @Test
    void leavesForeignTargetsFullyEditable() throws Exception {
        String html = resource("/bridge-control/index.html");
        String technical = between(html, "<template id=\"target-template\">", "</template>");

        assertTrue(technical.contains("<option>SELFHOST</option>"));
        assertTrue(technical.contains("<option>RICHTER_PROJECTS</option>"));
        for (String field : new String[]{"id", "endpoint", "channelId", "secret"}) {
            assertTrue(technical.contains("data-name=\"" + field + "\""),
                    field + " stays editable for a foreign target");
        }
        assertTrue(technical.contains("type=\"checkbox\" data-name=\"enabled\""),
                "a foreign target can still be switched off");
        assertTrue(technical.contains("class=\"remove\""), "a foreign target can still be removed");
    }

    private static String between(String value, String start, String end) {
        int from = value.indexOf(start);
        assertTrue(from >= 0, "missing " + start);
        int to = value.indexOf(end, from);
        assertTrue(to > from, "missing " + end + " after " + start);
        return value.substring(from, to);
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
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

    /** Collapses whitespace runs so an assertion describes structure, not indentation. */
    private static String collapse(String value) {
        return value.replaceAll("\\s+", " ");
    }

    /**
     * Git checks these resources out with the line endings of the running platform, so the
     * assertions must never depend on them.
     */
    private static String resource(String name) throws Exception {
        try (var input = BridgeControlUiContractTest.class.getResourceAsStream(name)) {
            assertNotNull(input, "missing resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n");
        }
    }
}
