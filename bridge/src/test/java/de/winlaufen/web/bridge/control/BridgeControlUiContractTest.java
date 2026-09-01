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

        assertTrue(html.contains("<title>Bridge Control · Sprecher-Web</title>"),
                "the browser tab names the surface and the product");
        assertTrue(html.contains("<strong>Sprecher-Web<span>Bridge Control</span></strong>"),
                "the header names the product and the surface");
        assertFalse(html.contains("WinLaufen Sprecher Web"), "the former product name is gone");
        assertFalse(html.contains("WinLaufen Web"), "the former ambiguous product name is gone");
    }

    @Test
    void showsTheMavenProjectVersionAndTheGitBuildIdInADiscreetFooter() throws Exception {
        String html = resource("/bridge-control/index.html");
        String css = resource("/bridge-control/control.css");
        String version = System.getProperty("product.version");
        // Without git metadata the plugin publishes nothing and the POM fallback applies.
        String buildId = System.getProperty("git.commit.id.describe", "unbekannt");

        assertNotNull(version, "the build passes the Maven project version to this test");
        assertTrue(html.contains("<footer class=\"version\">Sprecher-Web · Version " + version
                        + " · Build " + buildId + "</footer>"),
                "the footer shows exactly the built version and commit, never a hand-written copy");
        assertTrue(buildId.matches("unbekannt|[0-9a-f]{7,40}(-dirty)?"),
                "the build id stays an abbreviated commit id and never becomes a release tag");
        assertFalse(html.contains("${"), "no unresolved build placeholder reaches the browser");
        assertTrue(html.indexOf("<footer") > html.indexOf("</main>"),
                "the version stays below the content and never takes header space");
        assertTrue(css.contains("footer.version{"), "the footer keeps its own discreet rule");
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
    void namesTheLocalViewAfterWhatTheUserOpensInTheBrowser() throws Exception {
        String html = resource("/bridge-control/index.html");
        String section = between(html, "<section id=\"browser-view\"", "</section>");

        assertTrue(section.contains("<h2>Live-Ergebnisse im Browser</h2>"),
                "the local view is named after what the user opens, not after the transport");
        assertTrue(html.contains("<section id=\"browser-view\" hidden>"),
                "without a built-in local target the section stays away");
        for (String technical : new String[]{"Ausgabeziel", "Output Target", "LOCAL", "Endpoint",
                "Channel", "Secret"}) {
            assertFalse(section.contains(technical),
                    "the local view must not expose the technical term " + technical);
        }
    }

    @Test
    void separatesTheLocalViewFromAdditionalLiveServers() throws Exception {
        String html = resource("/bridge-control/index.html");

        assertFalse(html.contains("Ausgabeziele"),
                "the internal fan-out term is gone from the surface");
        assertFalse(html.contains("Weiteres Ausgabeziel hinzufügen"), "the old button label is gone");
        assertTrue(html.contains("<h2>Weitere Übertragung</h2>"),
                "additional live servers get their own section");
        assertTrue(html.contains(">Weiteren Live-Server verbinden</button>"),
                "the button says what it does in the user's words");
        assertTrue(html.indexOf("browser-view") < html.indexOf("Weitere Übertragung"),
                "the local view comes before the optional additional transmission");
    }

    @Test
    void showsHowToReachTheLocalViewInABrowser() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("const LIVE_HTTP_PORT = 44440"), "the fixed HTTP port is used");
        assertTrue(script.contains("addAddress('Auf diesem Computer:', `http://localhost:${port}/`)"),
                "the local address is always shown");
        assertTrue(script.contains("addAddress('Im lokalen Netzwerk:', `http://${host}:${port}/`)"),
                "the LAN address is shown when a usable host is known");
        assertTrue(script.contains("link.href = url"), "the addresses are clickable");
        // Lieber ein Hinweis als eine womöglich falsche Adresse.
        assertTrue(script.contains("Number(new URL(endpoint).port) === LIVE_WEBSOCKET_PORT ? LIVE_HTTP_PORT : 0"),
                "an unusual live server port yields no invented address");
        assertTrue(script.contains("addNote("), "an unknown address is explained instead of guessed");
    }

    @Test
    void usesOnlyAUsableLanHostForTheNetworkAddress() throws Exception {
        String script = resource("/bridge-control/control.js");
        String detection = collapse(between(script, "function lanHost", "\n}\n"));

        assertTrue(detection.contains("location.hostname"),
                "the host Bridge Control was opened with is demonstrably reachable");
        assertTrue(detection.contains("isLocalSourceHost(host)"), "loopback is not a LAN address");
        assertTrue(detection.contains("host === '0.0.0.0'"), "the unspecified address is rejected");
        assertTrue(detection.contains("/^169\\.254\\./"), "link-local is rejected");
    }

    @Test
    void keepsTheStoredTargetOrderAcrossTheTwoSections() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("node.dataset.order = order"),
                "every target remembers its position in the stored configuration");
        assertTrue(script.contains("[...localTargets.children, ...targets.children]")
                        && script.contains("Number(left.dataset.order) - Number(right.dataset.order)"),
                "serialisation restores the original order, so outputs.N indices stay stable");
        assertTrue(script.contains("const nodes = targetNodes();"),
                "the request is built from both sections");
    }

    @Test
    void keepsTheBuiltInLocalTargetFreeOfEditableFields() throws Exception {
        String html = resource("/bridge-control/index.html");
        String localTemplate = between(html, "<template id=\"local-target-template\">", "</template>");

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

    @Test
    void asksOnlyForTheAddressWhenConnectingAnotherLiveServer() throws Exception {
        String html = resource("/bridge-control/index.html");
        String technical = between(html, "<template id=\"target-template\">", "</template>");
        String visible = technical.substring(0, technical.indexOf("<details"));

        assertTrue(visible.contains("<label>IP-Adresse des Live-Servers<input data-role=\"address\""),
                "the normal case asks for the address in the user's words");
        assertTrue(visible.contains("required"), "the address is the one field that must be filled");
        assertEquals(2, occurrences(visible, "<input "),
                "outside the advanced block there is only the address and the enabled switch");
        assertTrue(visible.contains("type=\"checkbox\" data-name=\"enabled\""),
                "a target can still be switched off without opening the technical values");
        assertFalse(visible.contains("data-name=\"address\""),
                "the address is a form helper and must never enter the POST contract");
    }

    @Test
    void keepsEveryTechnicalValueUnderAdvancedSettings() throws Exception {
        String html = resource("/bridge-control/index.html");
        String advanced = between(html, "<details data-role=\"advanced\">", "</details>");

        assertTrue(advanced.contains("<summary>Erweiterte Einstellungen</summary>"));
        for (String field : new String[]{"id", "type", "endpoint", "channelId", "secret"}) {
            assertTrue(advanced.contains("data-name=\"" + field + "\""),
                    field + " stays visible and editable for an unusual configuration");
        }
        assertTrue(advanced.contains("<option>SELFHOST</option>")
                        && advanced.contains("<option>RICHTER_PROJECTS</option>"),
                "the target type stays a deliberate choice");
        assertTrue(advanced.contains("Neuer Verbindungsschlüssel (leer = beibehalten)"),
                "the surface says Verbindungsschlüssel, the configuration keeps calling it secret");
        assertFalse(advanced.contains(">Secret<"), "the technical term stays out of the label");
    }

    @Test
    void derivesTheIngestEndpointFromTheAddressOnTheFixedPort() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("const INGEST_PATH_PREFIX = '/bridge/v1/channels/'"),
                "the ingest path is the contract value, not a guess");
        assertTrue(script.contains("const DEFAULT_CHANNEL = 'local'"),
                "the simple case uses the channel the live server serves by default");
        assertTrue(script.contains(
                        "return `ws://${address}:${LIVE_WEBSOCKET_PORT}${INGEST_PATH_PREFIX}${channel}`;"),
                "an IPv4 address yields the plaintext ingest endpoint on port 44441");
        assertTrue(script.contains(
                        "return `ws://[${address}]:${LIVE_WEBSOCKET_PORT}${INGEST_PATH_PREFIX}${channel}`;"),
                "an IPv6 literal is bracketed instead of producing an invalid URL");
        assertTrue(script.contains("return `wss://${address}${INGEST_PATH_PREFIX}${channel}`;"),
                "a hostname has no default ingest port and is connected encrypted");
        assertTrue(script.contains("addTarget({type: 'SELFHOST', channelId: DEFAULT_CHANNEL, enabled: true})"),
                "a new live server starts as an enabled SELFHOST target on the default channel");
    }

    @Test
    void derivesTheTargetIdDeterministicallyFromAddressAndChannel() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("return `selfhost-${slug(address)}-${slug(channel)}`"),
                "the channel belongs in the id so one server can hold several events later");
        assertTrue(script.contains("for (let suffix = 2; suffix <= 99; suffix++)"),
                "a collision is resolved by a readable counter");
        assertFalse(script.contains("crypto.randomUUID") || script.contains("Math.random"),
                "the id stays reproducible, so a stored connection key survives re-entry");
        assertTrue(script.contains("matchesDerivedId"),
                "a stored id with a collision counter is still recognised as derived");
    }

    @Test
    void rendersTheBackendWarningsInsteadOfDecidingThemInJavaScript() throws Exception {
        String html = resource("/bridge-control/index.html");
        String script = resource("/bridge-control/control.js");

        assertTrue(html.contains("data-role=\"transportWarning\" hidden")
                        && html.contains("data-role=\"secretWarning\" hidden"),
                "both warnings have their own permanent place and start empty");
        assertTrue(script.contains("for (const role of ['transportWarning', 'secretWarning'])")
                        && script.contains("element.textContent = value[role] || '';"),
                "the surface shows exactly the texts the API delivers");
        assertFalse(script.contains("Unverschlüsselte Internetverbindung")
                        || script.contains("Standard-Verbindungsschlüssel"),
                "no warning text is duplicated in the browser, so no second policy can drift");
        assertTrue(script.contains("element.hidden = !value[role];"),
                "a target without a warning shows none");
    }

    @Test
    void explainsAPastedUrlOrAnAppendedPortBeforeTheRequestIsSent() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("Bitte nur die Adresse eintragen, ohne http:// und ohne Pfad")
                        && script.contains("Bitte nur die IP-Adresse eintragen, ohne Port"),
                "the two common typing mistakes are explained in the user's words");
        assertTrue(script.contains("address.setCustomValidity(addressProblem(address.value.trim()));"),
                "the hint uses standard form validation, like the WinLaufen host field");
        assertTrue(script.contains("if (address.validity.valid) deriveFromAddress(node);"),
                "an unusable address never produces a derived endpoint");
    }

    @Test
    void neverRecalculatesAConfigurationTheUserMaintainsByHand() throws Exception {
        String script = resource("/bridge-control/control.js");

        assertTrue(script.contains("if (node.dataset.manual === 'true') return;"),
                "a manual target is never overwritten by the derivation");
        assertTrue(script.contains("targetField(node, 'id').oninput = () => setManualTarget(node);")
                        && script.contains("targetField(node, 'endpoint').oninput = () => setManualTarget(node);"),
                "editing id or endpoint switches the target to manual maintenance");
        assertTrue(script.contains("if (!endpoint && !id) return true;"),
                "a target that was just added counts as derived and starts simple");
        assertTrue(script.contains("address.disabled = true;"),
                "a hidden or stale address must not block the form as a required field");
        assertTrue(script.contains("if (event.target.closest('[data-role=advanced]')) advanced(node).open = true;"),
                "an invalid field inside the collapsed block opens it instead of blocking silently");
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
