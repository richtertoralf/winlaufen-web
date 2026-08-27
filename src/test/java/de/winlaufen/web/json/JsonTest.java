package de.winlaufen.web.json;

import de.winlaufen.web.TestBlocks;
import de.winlaufen.web.config.AppConfig;
import de.winlaufen.web.config.PublicDisplayConfig;
import de.winlaufen.web.model.OutputMode;
import de.winlaufen.web.state.StateEvent;
import de.winlaufen.web.state.StateStore;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test void escapesJsonControlAndScriptSeparatorCharacters() {
        assertEquals("\"a\\\"\\\\\\n\\u0001\\u2028\"", Json.quote("a\"\\\n\u0001\u2028"));
    }

    @Test void stateContainsWireValuesWithoutNormalization() {
        StateStore store = new StateStore();
        store.result(TestBlocks.block(0, 0, List.of(List.of("01", " 7 ")), List.of("Rang", "StNr")));
        String json = Json.state(store.get());
        assertTrue(json.contains("\"01\""));
        assertTrue(json.contains("\" 7 \""));
    }

    @Test void configWithAllOutputModesIsValidJson() {
        String json = Json.config(new AppConfig("localhost", OutputMode.LOCAL, 8080, 8081,
                new PublicDisplayConfig(false, true, true, false, true)));
        JsonSyntax.parse(json);
        assertEquals(3, json.split("\"enabled\":", -1).length - 1);
        assertTrue(json.contains("\"showClub\":false"));
        assertTrue(json.contains("\"showAssociation\":true"));
        assertTrue(json.contains("\"showNation\":true"));
        assertTrue(json.contains("\"showShooting\":false"));
        assertTrue(json.contains("\"showPublicMessages\":true"));
    }

    @Test void stateWithoutCompetitionIsValidJson() {
        JsonSyntax.parse(Json.state(new StateStore().get()));
    }

    @Test void stateWithCompetitionAndClassSnapshotIsValidJson() {
        StateStore store = populatedStore();
        JsonSyntax.parse(Json.state(store.get()));
    }

    @Test void allWebSocketMessageTypesAreValidJson() {
        StateStore store = new StateStore();
        JsonSyntax.parse(Json.event(new StateEvent(StateEvent.Type.SNAPSHOT, store.get(), -1)));
        store.clock("12:34:56");
        JsonSyntax.parse(Json.event(new StateEvent(StateEvent.Type.CLOCK, store.get(), -1)));
        store = populatedStore();
        JsonSyntax.parse(Json.event(new StateEvent(StateEvent.Type.CLASS_SNAPSHOT, store.get(), 0)));
        store.message("Start um 10:45");
        String message = Json.event(new StateEvent(StateEvent.Type.MESSAGE, store.get(), -1));
        JsonSyntax.parse(message);
        assertTrue(message.contains("Start um 10:45"));
    }

    private static StateStore populatedStore() {
        StateStore store = new StateStore();
        store.result(TestBlocks.block(0, 0, List.of(List.of("01", "7")), List.of("Rang", "StNr")));
        return store;
    }
}
