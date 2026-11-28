package de.winlaufen.web.bridge.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.bridge.TestBlocks;
import de.winlaufen.web.bridge.source.winlaufen.ResultBlock;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalStateStoreTest {
    private static final List<String> HEADERS = List.of("Rang", "StNr");

    @Test void revisionsAndSnapshotReplacementAreAtomic() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        store.clock("12:00:00");
        assertEquals(1, store.get().sourceRevision());
        store.result(TestBlocks.block(0, 0, List.of(List.of("1", "101")), HEADERS));
        long first = store.get().state().competition().classes().get(0).snapshot().sourceRevision();
        store.result(TestBlocks.block(0, 0, List.of(List.of("2", "101")), HEADERS));
        var state = store.get();
        assertEquals(3, state.sourceRevision());
        assertTrue(state.state().competition().classes().get(0).snapshot().sourceRevision() > first);
        assertEquals("2", state.state().competition().classes().get(0).snapshot().rows().get(0).get(0));
        assertEquals(state.sourceRevision(), state.state().currentFinish().snapshotSourceRevision());
        assertEquals(0, state.state().currentFinish().rowIndex());
    }

    @Test void disconnectedStatePreservesCompetitionAndClock() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        store.clock("12:00:00");
        store.result(TestBlocks.block(1, 0, List.of(List.of("1", "120")), HEADERS));
        store.health(SourceHealth.STALE);
        assertEquals(SourceHealth.STALE, store.get().state().sourceHealth());
        assertEquals("12:00:00", store.get().state().clock());
        assertNotNull(store.get().state().competition());
        store.health(SourceHealth.DISCONNECTED);
        assertEquals(SourceHealth.DISCONNECTED, store.get().state().sourceHealth());
    }

    @Test void clockValuesRemainAuthoritativeAcrossEqualBackwardLargeAndMidnightSequences() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        for (String value : List.of("01:23:45", "01:23:45", "08:00:10", "07:15:00",
                "01:00:00", "20:00:00", "23:59:59", "00:00:00", "99:99:99")) {
            store.clock(value);
            assertEquals(value, store.get().state().clock());
            assertEquals(SourceHealth.CONNECTED, store.get().state().sourceHealth());
        }
    }

    @Test void serverMessageRemainsAvailableWithoutChangingCompetitionData() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        store.result(TestBlocks.block(0, 0, List.of(List.of("1", "101")), HEADERS));
        var competition = store.get().state().competition();
        store.message("Start verschiebt sich");
        assertEquals("Start verschiebt sich", store.get().state().message());
        assertSame(competition, store.get().state().competition());
    }

    /**
     * Each class table keeps the round value of the block that delivered it; the competition field
     * keeps the value of the latest block for existing consumers. Values are only passed on.
     */
    @Test void eachClassTableAndHintCarriesTheRoundOfItsOwnBlock() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        store.result(roundBlock(0, 2));
        store.result(roundBlock(1, 0));
        var competition = store.get().state().competition();
        assertEquals(2, competition.classes().get(0).snapshot().roundOrHeat());
        assertEquals(0, competition.classes().get(1).snapshot().roundOrHeat());
        assertEquals(0, competition.roundOrHeat());
        var hints = store.get().state().finishHistory().hints();
        assertEquals(2, hints.get(0).roundOrHeat());
        assertEquals(0, hints.get(1).roundOrHeat());
    }

    /**
     * Known limit, deliberately pinned: there is one table per class. A later block of another
     * round replaces it; the table then names that round. Nothing decides which one is a finish.
     */
    @Test void aBlockOfAnotherRoundReplacesTheClassTableAndNamesItsRound() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        store.result(roundBlock(0, 3));
        store.result(roundBlock(0, 1));
        assertEquals(1, store.get().state().competition().classes().get(0).snapshot().roundOrHeat());
        assertEquals(List.of(3, 1), store.get().state().finishHistory().hints().stream()
                .map(hint -> hint.roundOrHeat()).toList());
    }

    /** The block order of the recorded running competition, replayed with its original values. */
    @Test void recordedRunningCaptureKeepsItsDeliveredRoundOnEveryTable() throws Exception {
        JsonNode capture = new ObjectMapper().readTree(
                Path.of("..", "testdata", "protocol", "running", "decoded.json").toFile());
        JsonNode meta = capture.get("competition");
        String[] classes = new ObjectMapper().treeToValue(meta.get("classes"), String[].class);
        int[] rounds = new ObjectMapper().treeToValue(meta.get("round_count_or_team_size"), int[].class);
        List<String> headers = List.of(new ObjectMapper().treeToValue(meta.get("headers"), String[].class));

        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        for (JsonNode block : capture.get("blocks")) {
            List<List<String>> rows = new ArrayList<>();
            block.get("rows").forEach(row -> rows.add(List.of(
                    new ObjectMapper().convertValue(row, String[].class))));
            store.result(new ResultBlock(meta.get("type").asText(), meta.get("evaluation_mode").asInt(),
                    classes, rounds, meta.get("winspringen_position").asInt(),
                    block.get("class_index").asInt(), meta.get("round_or_heat").asInt(),
                    block.get("current_finish_index").asInt(), rows, headers));
        }

        var competition = store.get().state().competition();
        for (var item : competition.classes()) {
            assertEquals(meta.get("round_or_heat").asInt(), item.snapshot().roundOrHeat(), item.name());
            assertEquals(0, item.roundsOrTeamSize());
        }
        assertEquals(capture.get("blocks").size(), store.get().state().finishHistory().hints().size());
        store.get().state().finishHistory().hints()
                .forEach(hint -> assertEquals(0, hint.roundOrHeat()));
    }

    private static ResultBlock roundBlock(int classIndex, int roundOrHeat) {
        return new ResultBlock("Standardwettkampf", 1, new String[]{"U13 m", "U13 w"},
                new int[]{0, 0}, 0, classIndex, roundOrHeat, 0, List.of(List.of("1", "101")), HEADERS);
    }
}
