package de.winlaufen.web.liveserver.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.ClassSnapshot;
import de.winlaufen.web.contract.Competition;
import de.winlaufen.web.contract.CompetitionClass;
import de.winlaufen.web.contract.CurrentFinish;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;
import de.winlaufen.web.liveserver.state.PublishedState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void preservesWireStringsAndProducesValidJson() throws Exception {
        var table = new ClassSnapshot(7, List.of("Rang", "StNr"), List.of(List.of("01", " 7 ")));
        var competition = new Competition("Lauf", 0, 1, 0, 0,
                List.of(new CompetitionClass(0, "Klasse", 1, table)));
        var state = new CanonicalState(SourceHealth.CONNECTED, "12:34:56", competition,
                new CurrentFinish(0, 0, 7), "a\"\\\n" + (char) 1 + (char) 0x2028);

        String json = PublicJson.state(new PublishedState(3, "stream", 7, state,
                PresentationConfig.defaults()));
        JsonNode parsed = MAPPER.readTree(json);

        JsonNode snapshot = parsed.get("state").get("competition").get("classes").get(0).get("snapshot");
        assertEquals("01", snapshot.get("rows").get(0).get(0).asText());
        assertEquals(" 7 ", snapshot.get("rows").get(0).get(1).asText(), "wire padding is preserved");
        assertEquals("a\"\\\n" + (char) 1 + (char) 0x2028, parsed.get("state").get("message").asText());
        assertTrue(json.contains("\\u0001"));
        assertTrue(json.contains("\\u2028"));
        assertEquals(3, parsed.get("publicationRevision").asLong());
        assertEquals(7, snapshot.get("revision").asLong());
        assertEquals(0, parsed.get("state").get("currentFinish").get("classIndex").asInt());
        assertEquals(7, parsed.get("state").get("currentFinish").get("snapshotRevision").asLong());
    }

    @Test
    void emptyStateIsValidJsonWithExplicitNulls() throws Exception {
        JsonNode parsed = MAPPER.readTree(PublicJson.state(PublishedState.empty()));

        assertEquals("DISCONNECTED", parsed.get("state").get("health").asText());
        assertTrue(parsed.get("state").get("clock").isNull());
        assertTrue(parsed.get("state").get("competition").isNull());
        assertTrue(parsed.get("state").get("currentFinish").isNull());
        assertTrue(parsed.get("state").get("message").isNull());
        assertEquals(0, parsed.get("publicationRevision").asLong());
    }

    @Test
    void classWithoutSnapshotIsValidJson() throws Exception {
        var competition = new Competition("Lauf", 0, 2, 0, 3,
                List.of(new CompetitionClass(0, "A", 1, null),
                        new CompetitionClass(1, "B", 1,
                                new ClassSnapshot(2, List.of("Rang"), List.of(List.of("1"))))));
        var state = new CanonicalState(SourceHealth.STALE, "00:00:00", competition, null, null);

        JsonNode parsed = MAPPER.readTree(PublicJson.state(
                new PublishedState(1, "s", 2, state, PresentationConfig.defaults())));

        assertTrue(parsed.get("state").get("competition").get("classes").get(0).get("snapshot").isNull());
        assertEquals(3, parsed.get("state").get("competition").get("roundOrHeat").asInt());
    }

    @Test
    void publicPayloadNeverCarriesBridgeConfiguration() throws Exception {
        String json = PublicJson.state(PublishedState.empty());
        assertFalse(json.contains("secret"));
        assertFalse(json.contains("endpoint"));
        assertFalse(json.contains("sourceHost"));
        assertFalse(json.contains("targets"));
        assertFalse(json.contains("streamId"));
        MAPPER.readTree(json);
    }

    @Test
    void runtimeHintIsValidJsonAndExposesOnlyTheBrowserEndpoint() throws Exception {
        JsonNode parsed = MAPPER.readTree(PublicJson.runtime(44441));
        assertEquals(44441, parsed.get("webSocketPort").asInt());
        assertEquals("/live/v1", parsed.get("webSocketPath").asText());
        assertEquals(2, parsed.size());
    }
}
