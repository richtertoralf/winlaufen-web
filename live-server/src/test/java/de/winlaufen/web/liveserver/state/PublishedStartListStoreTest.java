package de.winlaufen.web.liveserver.state;

import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.StartListEnvelope;
import de.winlaufen.web.contract.StartListRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the live server does with a published start list.
 *
 * <p>The interesting cases are the ordering ones. Generations count one bridge's imports, so they
 * only mean something inside one stream; across a bridge restart a lower generation is normal and
 * must not be mistaken for a stale message.
 */
public class PublishedStartListStoreTest {

    public static StartListEnvelope envelope(String streamId, long generation, String... bibs) {
        List<StartListRow> rows = new ArrayList<>();
        for (String bib : bibs) {
            rows.add(new StartListRow(bib, "U16", "10:00:15", "MÜLLER", "Anna", "SV Test", "SVSAC",
                    "5.0", "2010", "w", "GER"));
        }
        return new StartListEnvelope("local", streamId, generation, "IMPORT_CSV", "Startliste.csv",
                rows);
    }

    private static StartListEnvelope absent(String streamId) {
        return new StartListEnvelope("local", streamId, 0, "IMPORT_CSV", "", List.of());
    }

    @Test
    void aFreshStoreHasNoStartList() {
        PublishedStartListStore store = new PublishedStartListStore("local");

        assertEquals(0, store.get().generation());
        assertFalse(store.get().present());
        assertTrue(store.get().entries().isEmpty());
    }

    @Test
    void aFullSnapshotIsAdoptedWithEveryField() {
        PublishedStartListStore store = new PublishedStartListStore("local");

        assertTrue(store.accept(envelope("stream-a", 4, "12", "13")));

        PublishedStartList published = store.get();
        assertEquals(4, published.generation());
        assertEquals("stream-a", published.streamId());
        assertEquals("IMPORT_CSV", published.source());
        assertEquals("Startliste.csv", published.sourceLabel());
        assertEquals(2, published.entries().size());
        assertEquals("12", published.entries().getFirst().bib());
        assertTrue(published.present());
        assertEquals(1, published.publicationRevision());
    }

    @Test
    void aNewSnapshotReplacesTheWholeStartList() {
        PublishedStartListStore store = new PublishedStartListStore("local");
        store.accept(envelope("stream-a", 1, "12", "13", "14"));

        store.accept(envelope("stream-a", 2, "99"));

        assertEquals(1, store.get().entries().size(), "no merge: the new stock is the whole stock");
        assertEquals("99", store.get().entries().getFirst().bib());
        assertEquals(2, store.get().generation());
    }

    @Test
    void theSameGenerationOnAReconnectIsIdempotent() {
        PublishedStartListStore store = new PublishedStartListStore("local");
        store.accept(envelope("stream-a", 3, "12"));
        long revision = store.get().publicationRevision();

        assertTrue(store.accept(envelope("stream-a", 3, "12")),
                "a resend after a reconnect is accepted, not treated as an error");

        assertEquals(revision, store.get().publicationRevision(),
                "an unchanged stock must not make browsers redraw");
        assertEquals(3, store.get().generation());
    }

    @Test
    void aLowerGenerationOfTheSameStreamIsRejectedAndChangesNothing() {
        PublishedStartListStore store = new PublishedStartListStore("local");
        store.accept(envelope("stream-a", 5, "12", "13"));

        assertFalse(store.accept(envelope("stream-a", 4, "99")));

        assertEquals(5, store.get().generation());
        assertEquals(2, store.get().entries().size());
    }

    /**
     * A restarted bridge gets a new stream id and starts counting at one again. Comparing that
     * with the previous run's generation would leave the live server showing a start list its
     * source no longer has.
     */
    @Test
    void aLowerGenerationOfANewStreamIsAccepted() {
        PublishedStartListStore store = new PublishedStartListStore("local");
        store.accept(envelope("stream-a", 17, "12", "13"));

        assertTrue(store.accept(envelope("stream-b", 1, "99")));

        assertEquals(1, store.get().generation());
        assertEquals("stream-b", store.get().streamId());
        assertEquals("99", store.get().entries().getFirst().bib());
    }

    @Test
    void theSameGenerationOfANewStreamIsAdoptedRatherThanSwallowed() {
        PublishedStartListStore store = new PublishedStartListStore("local");
        store.accept(envelope("stream-a", 17, "12"));

        assertTrue(store.accept(envelope("stream-b", 17, "99")));

        assertEquals("99", store.get().entries().getFirst().bib(),
                "same number, different bridge run: the content is a different one");
    }

    @Test
    void anAbsentStartListClearsAPreviouslyPublishedOne() {
        PublishedStartListStore store = new PublishedStartListStore("local");
        store.accept(envelope("stream-a", 2, "12", "13"));

        assertTrue(store.accept(absent("stream-b")));

        assertFalse(store.get().present());
        assertTrue(store.get().entries().isEmpty());
        assertEquals(0, store.get().generation());
    }

    @Test
    void aChannelMismatchIsRejected() {
        PublishedStartListStore store = new PublishedStartListStore("local");

        assertThrows(IllegalArgumentException.class, () -> store.accept(
                new StartListEnvelope("other", "stream-a", 1, "IMPORT_CSV", "x",
                        List.of(new StartListRow("1", "U16", "", "", "", "", "", "", "", "", "")))));
    }

    @Test
    void listenersSeeEveryAdoptedStartListButNoIdempotentRepeat() {
        PublishedStartListStore store = new PublishedStartListStore("local");
        List<PublishedStartList> seen = new CopyOnWriteArrayList<>();
        store.addListener(seen::add);

        store.accept(envelope("stream-a", 1, "12"));
        store.accept(envelope("stream-a", 1, "12"));
        store.accept(envelope("stream-a", 2, "13"));

        assertEquals(List.of(1L, 2L), seen.stream().map(PublishedStartList::generation).toList());
        assertEquals(List.of(1L, 2L),
                seen.stream().map(PublishedStartList::publicationRevision).toList());
    }

    @Test
    void aRealisticEventSizeIsAdoptedCompletely() throws Exception {
        PublishedStartListStore store = new PublishedStartListStore("local");
        List<StartListRow> rows = new ArrayList<>();
        for (int index = 0; index < 1999; index++) {
            rows.add(new StartListRow(Integer.toString(index + 1), "Klasse " + (index % 46),
                    "10:00:15", "MUSTERMANN", "Maximilian", "SK Dresden Niedersedlitz", "SVSAC",
                    "10.5", "2010", "m", "GER"));
        }
        String wire = ContractJson.startList(new StartListEnvelope("local", "stream-a", 1,
                "IMPORT_CSV", "Startliste.csv", rows));

        assertTrue(store.accept(ContractJson.readStartList(wire)));

        assertEquals(1999, store.get().entries().size());
        assertEquals(46, store.get().entries().stream()
                .map(StartListRow::className).distinct().count());
        assertEquals("1999", store.get().entries().getLast().bib(),
                "the order of the import is preserved end to end");
    }
}
