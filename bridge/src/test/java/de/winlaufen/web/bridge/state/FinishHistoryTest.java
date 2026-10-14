package de.winlaufen.web.bridge.state;

import de.winlaufen.web.bridge.source.winlaufen.ResultBlock;
import de.winlaufen.web.contract.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FinishHistoryTest {
    static ResultBlock result(String bib, String rank) {
        return new ResultBlock("Standardwettkampf", 1, new String[]{"Test"}, new int[]{0},
                0, 0, 0, 0, List.of(List.of(rank, bib, "0:30:00,1")),
                List.of("Rang", "StNr", "Laufzeit"));
    }

    @Test void originalHintsSurviveClockHealthAndCorrectionWithoutInventedArrivals() throws Exception {
        var store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        store.result(result("12", "1"));
        store.clock("10:00:00");
        store.result(result("A12", "1"));
        store.result(result("0012", "2"));
        store.result(result("12", "9")); // A correction is retained as an original hint, not classified.
        store.message("Test");
        store.health(SourceHealth.DISCONNECTED);
        var history = store.get().state().finishHistory();
        assertEquals(4, history.lastSequence());
        assertEquals(List.of("12", "A12", "0012", "12"),
                history.hints().stream().map(h -> h.row().get(1)).toList());
        assertEquals(List.of(1L, 3L, 4L, 5L),
                history.hints().stream().map(FinishHint::sourceRevision).toList());
        var envelope = new SnapshotEnvelope("local", "stream", store.get().sourceRevision(),
                store.get().state(), store.get().presentation());
        assertEquals(history, ContractJson.readSnapshot(ContractJson.snapshot(envelope)).state().finishHistory());
    }

    @Test void retentionIsBoundedAndMissingPrefixIsDetectable() {
        var store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        for (int i = 0; i < 300; i++) store.result(result("B" + i, "1"));
        var history = store.get().state().finishHistory();
        assertEquals(300, history.lastSequence());
        assertEquals(FinishHistory.MAX_HINTS, history.hints().size());
        assertEquals(45, history.hints().getFirst().sequence());
        assertEquals(300, history.hints().getLast().sequence());
    }

    @Test void oversizedHintDoesNotCreateUnboundedHistory() {
        var history = FinishHistory.empty().append(1, 0, "Test", 0,
                List.of("StNr"), List.of("x".repeat(FinishHistory.MAX_JSON_CHARS)));
        assertEquals(1, history.lastSequence());
        assertTrue(history.hints().isEmpty());
    }

    @Test void reorderedHistoryIsRejected() {
        var hint = new FinishHint(2, 1, 0, "Test", 0, List.of("StNr"), List.of("12"));
        var state = new CanonicalState(SourceHealth.CONNECTED, null, null, null, null, null,
                new FinishHistory(1, List.of(hint)));
        assertThrows(ContractViolationException.class,
                () -> ContractJson.validateState(state, PresentationConfig.defaults()));
    }
}
