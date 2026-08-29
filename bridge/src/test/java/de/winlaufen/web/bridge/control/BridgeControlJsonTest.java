package de.winlaufen.web.bridge.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.output.OutputConnectionState;
import de.winlaufen.web.bridge.output.OutputTargetRuntime;
import de.winlaufen.web.bridge.state.CanonicalSnapshot;
import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeControlJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void configViewIsValidJsonAndNeverContainsASecret() throws Exception {
        String json = BridgeControlJson.config(config());
        JsonNode parsed = MAPPER.readTree(json);

        assertEquals("WINLAUFEN", parsed.get("sourceType").asText());
        assertEquals("timing-pc", parsed.get("sourceHost").asText());
        assertEquals(4444, parsed.get("sourcePort").asInt());
        assertEquals(2, parsed.get("targets").size());
        assertTrue(parsed.get("targets").get(0).get("secretConfigured").asBoolean());
        assertFalse(json.contains("super-secret-value"));
        assertFalse(json.contains("another-secret-value"));
        assertFalse(parsed.get("targets").get(0).has("secret"));
        assertTrue(parsed.get("presentation").get("showShooting").asBoolean());
        assertFalse(parsed.get("presentation").get("showNation").asBoolean());
    }

    @Test
    void statusViewStaysValidJsonForControlCharactersInAnErrorMessage() throws Exception {
        var snapshot = new CanonicalSnapshot(7,
                new CanonicalState(SourceHealth.STALE, "12:34:56", null, null, null),
                PresentationConfig.defaults());
        var runtime = new OutputTargetRuntime("local", OutputConnectionState.RETRY_WAIT, null, -1, 3,
                "Zeile1\r\n\tTab \"quote\" \\ backslash " + (char) 1 + " control");

        String json = BridgeControlJson.status(snapshot, List.of(runtime));
        JsonNode parsed = MAPPER.readTree(json);

        assertEquals(7, parsed.get("sourceRevision").asLong());
        assertEquals("STALE", parsed.get("sourceHealth").asText());
        assertEquals("12:34:56", parsed.get("clock").asText());
        JsonNode output = parsed.get("outputs").get(0);
        assertEquals("RETRY_WAIT", output.get("state").asText());
        assertEquals(3, output.get("retryAttempt").asInt());
        // Round-trips unchanged, which is exactly what the hand-written escaper could not do.
        assertEquals("Zeile1\r\n\tTab \"quote\" \\ backslash " + (char) 1 + " control",
                output.get("lastError").asText());
    }

    @Test
    void nullValuesAreEncodedAsJsonNull() throws Exception {
        var snapshot = new CanonicalSnapshot(0, CanonicalState.empty(), PresentationConfig.defaults());
        var runtime = OutputTargetRuntime.initial("local", true);

        JsonNode parsed = MAPPER.readTree(BridgeControlJson.status(snapshot, List.of(runtime)));
        assertTrue(parsed.get("clock").isNull());
        assertTrue(parsed.get("outputs").get(0).get("lastError").isNull());
    }

    @Test
    void errorViewIsValidJson() throws Exception {
        JsonNode parsed = MAPPER.readTree(BridgeControlJson.error("Ungültiger \"Host\"\n"));
        assertEquals("Ungültiger \"Host\"\n", parsed.get("error").asText());
        assertTrue(MAPPER.readTree(BridgeControlJson.error(null)).get("error").isTextual());
    }

    private static BridgeConfig config() {
        return new BridgeConfig("WINLAUFEN", "timing-pc", "0.0.0.0", 44442,
                List.of(
                        new OutputTargetConfig("local", OutputTargetType.LOCAL, true,
                                URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"),
                                "local", "super-secret-value"),
                        new OutputTargetConfig("club", OutputTargetType.SELFHOST, false,
                                URI.create("wss://club.example/bridge/v1/channels/race"),
                                "race", "another-secret-value")),
                PresentationConfig.defaults());
    }
}
