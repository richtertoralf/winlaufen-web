package de.winlaufen.web.bridge.state;

import de.winlaufen.web.bridge.TestBlocks;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalStateStoreTest {
    private static final List<String> HEADERS = List.of("Rang", "StNr");

    @Test void revisionsAndSnapshotReplacementAreAtomic() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
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
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
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
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        for (String value : List.of("01:23:45", "01:23:45", "08:00:10", "07:15:00",
                "01:00:00", "20:00:00", "23:59:59", "00:00:00", "99:99:99")) {
            store.clock(value);
            assertEquals(value, store.get().state().clock());
            assertEquals(SourceHealth.CONNECTED, store.get().state().sourceHealth());
        }
    }

    @Test void serverMessageRemainsAvailableWithoutChangingCompetitionData() {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        store.result(TestBlocks.block(0, 0, List.of(List.of("1", "101")), HEADERS));
        var competition = store.get().state().competition();
        store.message("Start verschiebt sich");
        assertEquals("Start verschiebt sich", store.get().state().message());
        assertSame(competition, store.get().state().competition());
    }
}
