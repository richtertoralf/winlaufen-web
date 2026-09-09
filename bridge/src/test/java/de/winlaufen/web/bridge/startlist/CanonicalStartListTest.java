package de.winlaufen.web.bridge.startlist;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalStartListTest {

    private static StartListEntry entry(String bib, String className) {
        return new StartListEntry(bib, className, "", "", "", "", "", "", "", "", "");
    }

    private static CanonicalStartList list(StartListEntry... entries) {
        return new CanonicalStartList(1, StartListSource.IMPORT_CSV, "s.csv", List.of(entries));
    }

    @Test
    void anEmptyStartListMeansNoneWasImported() {
        CanonicalStartList empty = CanonicalStartList.empty();

        assertEquals(0, empty.generation());
        assertFalse(empty.isPresent());
        assertTrue(empty.entries().isEmpty());
    }

    @Test
    void theSameBibInDifferentClassesIsAllowed() {
        CanonicalStartList list = list(entry("12", "U16"), entry("12", "U18"));

        assertEquals(2, list.entries().size());
        assertEquals("U16", list.find("U16", "12").className());
        assertEquals("U18", list.find("U18", "12").className());
    }

    @Test
    void theSameBibTwiceInOneClassIsRejected() {
        assertThrows(StartListFormatException.class,
                () -> list(entry("12", "U16"), entry("12", "U16")));
    }

    @Test
    void classAndBibAreComparedAsAPairNotAsJoinedText() {
        // "Schüler U12" + "m 5" and "Schüler U12 m" + "5" are different entries. A uniqueness
        // check that joins both values with a separator would wrongly call them a duplicate.
        CanonicalStartList list = list(entry("m 5", "Schüler U12"), entry("5", "Schüler U12 m"));

        assertEquals(2, list.entries().size());
        assertEquals("m 5", list.find("Schüler U12", "m 5").bib());
        assertEquals("5", list.find("Schüler U12 m", "5").bib());
    }

    @Test
    void bibsThatOnlyLookAlikeStayDifferentEntries() {
        CanonicalStartList list = list(entry("0012", "U16"), entry("12", "U16"),
                entry("A12", "U16"));

        assertEquals(3, list.entries().size());
        assertNull(list.find("U16", "012"));
        assertEquals("0012", list.find("U16", "0012").bib());
        assertEquals("A12", list.find("U16", "A12").bib());
    }

    @Test
    void aLookupNeedsTheClassBecauseABibAloneDoesNotIdentifyAnEntry() {
        CanonicalStartList list = list(entry("12", "U16"));

        assertNull(list.find("U18", "12"));
        assertNull(list.find("U16", "13"));
    }

    @Test
    void theEntryListIsACopyAndCannotBeChangedAfterwards() {
        List<StartListEntry> mutable = new ArrayList<>(List.of(entry("1", "U16")));
        CanonicalStartList list = new CanonicalStartList(1, StartListSource.IMPORT_CSV, "s.csv",
                mutable);

        mutable.add(entry("2", "U16"));

        assertEquals(1, list.entries().size());
        assertThrows(UnsupportedOperationException.class,
                () -> list.entries().add(entry("3", "U16")));
    }

    @Test
    void aGenerationIsNeverNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> new CanonicalStartList(-1, StartListSource.IMPORT_CSV, "s.csv", List.of()));
    }

    @Test
    void anEntryRequiresABibAndAClass() {
        assertThrows(StartListFormatException.class, () -> entry("", "U16"));
        assertThrows(StartListFormatException.class, () -> entry("  ", "U16"));
        assertThrows(StartListFormatException.class, () -> entry("12", ""));
    }

    @Test
    void surroundingWhitespaceIsRemovedButTheValueItselfIsNot() {
        StartListEntry trimmed = new StartListEntry("  0012 ", " Schüler U12 m ", " 10:00:15 ",
                " MÜLLER ", "", "", "", "", "", "", "");

        assertEquals("0012", trimmed.bib());
        assertEquals("Schüler U12 m", trimmed.className());
        assertEquals("10:00:15", trimmed.startTime());
        assertEquals("MÜLLER", trimmed.lastName());
    }

    @Test
    void anOverlongValueIsRejected() {
        String tooLong = "x".repeat(StartListEntry.MAX_VALUE_CHARS + 1);

        assertThrows(StartListFormatException.class, () -> entry(tooLong, "U16"));
        assertThrows(StartListFormatException.class,
                () -> new StartListEntry("12", "U16", "", tooLong, "", "", "", "", "", "", ""));
    }

    @Test
    void tooManyEntriesAreRejected() {
        List<StartListEntry> entries = new ArrayList<>();
        for (int index = 0; index <= CanonicalStartList.MAX_ENTRIES; index++) {
            entries.add(entry(Integer.toString(index), "U16"));
        }

        assertThrows(StartListFormatException.class,
                () -> new CanonicalStartList(1, StartListSource.IMPORT_CSV, "s.csv", entries));
    }

    @Test
    void anImportWithoutEntriesIsRejected() {
        assertThrows(StartListFormatException.class,
                () -> new StartListImport(StartListSource.IMPORT_CSV, "s.csv", List.of()));
    }
}
