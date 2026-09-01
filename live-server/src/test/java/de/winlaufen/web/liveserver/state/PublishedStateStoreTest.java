package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.ClassSnapshot;
import de.winlaufen.web.contract.Competition;
import de.winlaufen.web.contract.CompetitionClass;
import de.winlaufen.web.contract.CurrentFinish;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SnapshotEnvelope;
import de.winlaufen.web.contract.SourceHealth;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublishedStateStoreTest {

    @Test
    void acceptsNewStreamAndMonotonicRevisionsAtomically() {
        var store = new PublishedStateStore("local");

        assertTrue(store.accept(snapshot("a", 2, "one")));
        assertFalse(store.accept(snapshot("a", 1, "old")));
        assertEquals("one", store.get().state().clock());

        assertTrue(store.accept(snapshot("b", 0, "new")));
        assertEquals(2, store.get().publicationRevision());
        assertEquals("new", store.get().state().clock());
    }

    @Test
    void idempotentSnapshotDoesNotIncrementPublicationRevision() {
        var store = new PublishedStateStore("local");
        var value = snapshot("a", 1, "one");

        assertTrue(store.accept(value));
        assertTrue(store.accept(value));
        assertEquals(1, store.get().publicationRevision());
    }

    @Test
    void rejectedSnapshotDoesNotNotifyListeners() {
        var store = new PublishedStateStore("local");
        List<Long> seen = new ArrayList<>();
        store.addListener(state -> seen.add(state.publicationRevision()));

        store.accept(snapshot("a", 5, "one"));
        store.accept(snapshot("a", 4, "older"));
        store.accept(snapshot("a", 5, "same"));

        assertEquals(List.of(1L), seen);
        assertEquals("one", store.get().state().clock());
    }

    /**
     * The competition time is a WinLaufen value. The store carries it through byte for byte and
     * never produces one of its own, so a speaker can read a standing clock as "no fresh data".
     */
    @Test
    void carriesTheWinLaufenCompetitionTimeThroughUnchanged() {
        var store = new PublishedStateStore("local");

        store.accept(snapshot("a", 1, "10:07:41"));
        assertEquals("10:07:41", store.get().state().clock());

        store.accept(snapshot("a", 2, "10:07:42"));
        assertEquals("10:07:42", store.get().state().clock(),
                "exactly the delivered value, never a computed one");

        // Auch ein Wert, den keine lokale Uhr erzeugen wuerde, bleibt unveraendert.
        store.accept(snapshot("a", 3, "27:00:03"));
        assertEquals("27:00:03", store.get().state().clock());
    }

    @Test
    void aVanishedBridgeStopsThePublishedCopyFromClaimingAConnectedSource() {
        var store = new PublishedStateStore("local");
        List<Long> seen = new ArrayList<>();
        store.accept(snapshot("a", 5, "10:07:41"));
        store.addListener(state -> seen.add(state.publicationRevision()));

        store.ingestDisconnected();

        assertEquals(SourceHealth.DISCONNECTED, store.get().state().sourceHealth());
        assertEquals("10:07:41", store.get().state().clock(),
                "the last competition time stays visible and is never advanced");
        assertEquals(2, store.get().publicationRevision(), "browsers learn about it");
        assertEquals(List.of(2L), seen);
    }

    @Test
    void aReturningBridgeIsAcceptedAgainEvenWhenItResendsTheSameRevision() {
        var store = new PublishedStateStore("local");
        store.accept(snapshot("a", 5, "10:07:41"));
        store.ingestDisconnected();

        assertTrue(store.accept(snapshot("a", 5, "10:07:41")),
                "the resync after a reconnect must not be swallowed as a duplicate");
        assertEquals(SourceHealth.CONNECTED, store.get().state().sourceHealth());
        assertEquals(3, store.get().publicationRevision());
    }

    @Test
    void markingAVanishedBridgeTwiceChangesNothing() {
        var store = new PublishedStateStore("local");
        store.accept(snapshot("a", 5, "10:07:41"));
        store.ingestDisconnected();
        long revision = store.get().publicationRevision();

        store.ingestDisconnected();

        assertEquals(revision, store.get().publicationRevision());
        assertEquals(0, new PublishedStateStore("local").get().publicationRevision(),
                "a live server that never had a bridge publishes nothing either");
    }

    /**
     * The real Hetzner finding: restarting the bridge cleared the results on the presentation
     * node, and they only returned when the next athlete finished.
     */
    @Test
    void aRestartedBridgeKeepsTheLastKnownCompetitionUntilWinLaufenResendsIt() {
        var store = new PublishedStateStore("local");
        store.accept(results("stream-1", 7, "10:07:41", "Lauf", List.of("Meier", "Schulz")));
        assertEquals(2, rowsOf(store), "eleven rows in the field test, two here");

        // Bridge weg: Stand bleibt, nur die Quellenlage wird ehrlich.
        store.ingestDisconnected();
        assertEquals(SourceHealth.DISCONNECTED, store.get().state().sourceHealth());
        assertEquals("10:07:41", store.get().state().clock());
        assertEquals(2, rowsOf(store));

        // Neu gestartete Bridge: neue streamId, Uhr und Health wieder da, aber noch kein
        // Klassensnapshot von WinLaufen.
        assertTrue(store.accept(snapshot("stream-2", 0, "10:09:12")));

        assertEquals("10:09:12", store.get().state().clock(), "the new WinLaufen time is adopted");
        assertEquals(SourceHealth.CONNECTED, store.get().state().sourceHealth());
        assertEquals(2, rowsOf(store), "the last known standing must survive the bridge restart");
        assertEquals("Lauf", store.get().state().competition().type(), "metadata survives too");

        // Erst der naechste autoritative Stand aus WinLaufen ersetzt die Liste.
        store.accept(results("stream-2", 1, "10:09:13", "Lauf", List.of("Meier", "Schulz", "Weber")));
        assertEquals(3, rowsOf(store));
    }

    /** The rule must not degenerate into "never adopt an empty list". */
    @Test
    void anAuthoritativeEmptyStandingStillReplacesTheStoredResults() {
        var store = new PublishedStateStore("local");
        store.accept(results("stream-1", 1, "10:07:41", "Lauf", List.of("Meier", "Schulz")));

        store.accept(results("stream-1", 2, "10:07:42", "Lauf", List.of()));

        assertEquals(0, rowsOf(store),
                "WinLaufen reporting a class without rows is authoritative and clears the table");
        assertTrue(store.get().state().competition() != null,
                "an authoritative empty standing is a real competition, not a missing one");
    }

    @Test
    void aRetainedCompetitionKeepsItsMatchingCurrentFinishMarker() {
        var store = new PublishedStateStore("local");
        store.accept(results("stream-1", 4, "10:07:41", "Lauf", List.of("Meier", "Schulz")));
        CurrentFinish before = store.get().state().currentFinish();
        assertNotNull(before);

        store.accept(snapshot("stream-2", 0, "10:09:12"));

        assertEquals(before, store.get().state().currentFinish(),
                "the marker points into the retained snapshot and must stay consistent with it");
    }

    @Test
    void foreignChannelIsRejected() {
        var store = new PublishedStateStore("local");
        var foreign = new SnapshotEnvelope("other", "a", 1,
                new CanonicalState(SourceHealth.CONNECTED, "10:00:00", null, null, null),
                PresentationConfig.defaults());

        assertThrows(IllegalArgumentException.class, () -> store.accept(foreign));
        assertEquals(0, store.get().publicationRevision());
    }

    @Test
    void publicationRevisionOnlyEverIncreases() {
        var store = new PublishedStateStore("local");
        long previous = store.get().publicationRevision();
        for (int index = 0; index < 20; index++) {
            store.accept(snapshot(index % 3 == 0 ? "a" : "b", index, "10:00:0" + (index % 10)));
            long current = store.get().publicationRevision();
            assertTrue(current >= previous, "publicationRevision must never decrease");
            previous = current;
        }
    }

    private static int rowsOf(PublishedStateStore store) {
        Competition competition = store.get().state().competition();
        assertNotNull(competition, "a stored competition is expected here");
        ClassSnapshot snapshot = competition.classes().get(0).snapshot();
        return snapshot == null ? 0 : snapshot.rows().size();
    }

    /** One complete, authoritative class snapshot, exactly as the bridge builds it. */
    public static SnapshotEnvelope results(String stream, long revision, String clock, String type,
                                           List<String> names) {
        List<List<String>> rows = names.stream().map(name -> List.of("1", name)).toList();
        var classes = List.of(new CompetitionClass(0, "Herren", 1,
                new ClassSnapshot(revision, List.of("Rang", "Name, Vorname"), rows)));
        var competition = new Competition(type, 0, 1, 0, 0, classes);
        return new SnapshotEnvelope("local", stream, revision,
                new CanonicalState(SourceHealth.CONNECTED, clock, competition,
                        new CurrentFinish(0, 0, revision), null),
                PresentationConfig.defaults());
    }

    public static SnapshotEnvelope snapshot(String stream, long revision, String clock) {
        return new SnapshotEnvelope("local", stream, revision,
                new CanonicalState(SourceHealth.CONNECTED, clock, null, null, null),
                PresentationConfig.defaults());
    }
}
