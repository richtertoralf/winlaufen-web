package de.winlaufen.web.state;

import de.winlaufen.web.TestBlocks;
import de.winlaufen.web.model.ConnectionHealth;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StateStoreTest {
    private static final List<String> HEADERS = List.of("Rang", "StNr");

    @Test void revisionsAndSnapshotReplacementAreAtomic() {
        StateStore store = new StateStore();
        store.clock("12:00:00");
        assertEquals(1, store.get().revision());
        store.result(TestBlocks.block(0, 0, List.of(List.of("1", "101")), HEADERS));
        long first = store.get().competition().classes().get(0).snapshot().revision();
        store.result(TestBlocks.block(0, 0, List.of(List.of("2", "101")), HEADERS));
        var state = store.get();
        assertEquals(3, state.revision());
        assertTrue(state.competition().classes().get(0).snapshot().revision() > first);
        assertEquals("2", state.competition().classes().get(0).snapshot().rows().get(0).get(0));
        assertEquals(state.revision(), state.currentFinish().snapshotRevision());
        assertEquals(0, state.currentFinish().rowIndex());
    }

    @Test void disconnectedStatePreservesCompetitionAndClock() {
        StateStore store = new StateStore();
        store.clock("12:00:00");
        store.result(TestBlocks.block(1, 0, List.of(List.of("1", "120")), HEADERS));
        store.health(ConnectionHealth.STALE);
        assertEquals(ConnectionHealth.STALE, store.get().health());
        assertEquals("12:00:00", store.get().clock());
        assertNotNull(store.get().competition());
        store.health(ConnectionHealth.DISCONNECTED);
        assertEquals(ConnectionHealth.DISCONNECTED, store.get().health());
    }

    @Test void clockValuesRemainAuthoritativeAcrossEqualBackwardLargeAndMidnightSequences() {
        StateStore store = new StateStore();
        for (String value : List.of("01:23:45", "01:23:45", "08:00:10", "07:15:00",
                "01:00:00", "20:00:00", "23:59:59", "00:00:00", "99:99:99")) {
            store.clock(value);
            assertEquals(value, store.get().clock());
            assertEquals(ConnectionHealth.CONNECTED, store.get().health());
        }
    }

    @Test void serverMessageRemainsAvailableWithoutChangingCompetitionData() {
        StateStore store = new StateStore();
        store.result(TestBlocks.block(0, 0, List.of(List.of("1", "101")), HEADERS));
        var competition = store.get().competition();
        store.message("Start verschiebt sich");
        assertEquals("Start verschiebt sich", store.get().message());
        assertSame(competition, store.get().competition());
    }
}
