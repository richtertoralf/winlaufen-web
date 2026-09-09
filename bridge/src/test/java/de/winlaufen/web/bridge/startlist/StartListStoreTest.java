package de.winlaufen.web.bridge.startlist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartListStoreTest {

    @TempDir
    Path temp;

    /** The prologue list: bib 12 belongs to Anna. */
    private static final String PROLOGUE = """
            StNr,Klasse,Name,Vorname,Startzeit
            12,U16,MÜLLER,Anna,10:00:15
            13,U16,MEIER,Ben,10:00:30
            """;

    /** The heat list: bib 12 is a different participant, and bib 13 no longer exists. */
    private static final String HEAT = """
            StNr,Klasse,Name,Vorname,Startzeit
            12,U18,VOBORNÍKOVÁ,Tereza,14:00:00
            """;

    private StartListStore store() throws IOException {
        return StartListStore.open(temp.resolve("startlist.properties"));
    }

    private static StartListImport parse(String csv) {
        return StartListParser.parse(csv.getBytes(StandardCharsets.UTF_8), "startliste.csv");
    }

    // --- Replace semantics ---------------------------------------------------------------------

    @Test
    void startsWithoutAStartList() throws Exception {
        StartListStore store = store();

        assertEquals(0, store.current().generation());
        assertFalse(store.current().isPresent());
        assertTrue(store.current().entries().isEmpty());
    }

    @Test
    void replaceSwapsTheWholeStock() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));

        CanonicalStartList adopted = store.replace(parse(HEAT));

        assertEquals(1, adopted.entries().size());
        assertEquals(adopted, store.current());
        assertEquals("U18", store.current().entries().getFirst().className());
    }

    @Test
    void previousBibLookupDisappearsCompletely() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        assertEquals("Anna", store.current().find("U16", "12").firstName());

        store.replace(parse(HEAT));

        assertNull(store.current().find("U16", "12"),
                "bib 12 of the prologue class must not survive a new start list");
        assertNull(store.current().find("U16", "13"),
                "a participant missing from the new list must be gone, not merged in");
        assertNotNull(store.current().find("U18", "12"));
        assertEquals("Tereza", store.current().find("U18", "12").firstName());
        assertEquals("14:00:00", store.current().find("U18", "12").startTime());
    }

    @Test
    void generationIncreasesByExactlyOnePerSuccessfulReplace() throws Exception {
        StartListStore store = store();

        assertEquals(1, store.replace(parse(PROLOGUE)).generation());
        assertEquals(2, store.replace(parse(HEAT)).generation());
        assertEquals(3, store.replace(parse(PROLOGUE)).generation());
        assertEquals(3, store.current().generation());
    }

    @Test
    void aRepeatedIdenticalImportIsStillANewGeneration() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));

        CanonicalStartList again = store.replace(parse(PROLOGUE));

        assertEquals(2, again.generation());
        assertEquals(store.current().entries(), again.entries());
    }

    // --- Failures never touch the stock --------------------------------------------------------

    @Test
    void parseFailureLeavesCurrentAndGenerationUntouched() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        CanonicalStartList before = store.current();

        assertThrows(StartListFormatException.class, () -> parse("Foo|Bar\r\n1|2\r\n"));

        assertEquals(before, store.current());
        assertEquals(1, store.current().generation());
    }

    @Test
    void validationFailureLeavesCurrentAndGenerationUntouched() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        CanonicalStartList before = store.current();
        StartListImport duplicate = parse("StNr,Klasse\r\n12,U16\r\n12,U16\r\n");

        assertThrows(StartListFormatException.class, () -> store.replace(duplicate));

        assertEquals(before, store.current());
        assertEquals(1, store.current().generation());
        assertEquals("Anna", store.current().find("U16", "12").firstName());
    }

    @Test
    void aRejectedImportLeavesThePersistedFileUntouched() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        byte[] persisted = Files.readAllBytes(store.path());
        StartListImport duplicate = parse("StNr,Klasse\r\n12,U16\r\n12,U16\r\n");

        assertThrows(StartListFormatException.class, () -> store.replace(duplicate));

        assertArrayEqualsIgnoringComments(persisted, Files.readAllBytes(store.path()));
        assertEquals(1, StartListStore.open(store.path()).current().generation());
    }

    @Test
    void aFailedWriteLeavesTheStockInForce() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        CanonicalStartList before = store.current();
        // Turning the target path into a non-empty directory makes the final move fail after the
        // start list was already built and validated. That is the moment the previously adopted
        // list must stay in force.
        Files.delete(store.path());
        Files.createDirectory(store.path());
        Files.writeString(store.path().resolve("occupied"), "x");

        assertThrows(IOException.class, () -> store.replace(parse(HEAT)));

        assertEquals(before, store.current());
        assertEquals(1, store.current().generation());
        assertEquals("Anna", store.current().find("U16", "12").firstName());
    }

    @Test
    void noTemporaryFileIsLeftBehind() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        store.replace(parse(HEAT));

        try (var files = Files.list(temp)) {
            assertEquals(List.of("startlist.properties"),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    // --- Persistence ---------------------------------------------------------------------------

    @Test
    void aRestartRestoresTheStartListAndItsGeneration() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        store.replace(parse(HEAT));

        StartListStore restarted = store();

        assertEquals(2, restarted.current().generation());
        assertEquals(StartListSource.IMPORT_CSV, restarted.current().source());
        assertEquals("startliste.csv", restarted.current().sourceLabel());
        assertEquals(store.current().entries(), restarted.current().entries());
        assertEquals("Tereza", restarted.current().find("U18", "12").firstName());
    }

    @Test
    void theGenerationContinuesAfterARestart() throws Exception {
        store().replace(parse(PROLOGUE));

        StartListStore restarted = store();

        assertEquals(2, restarted.replace(parse(HEAT)).generation());
        assertEquals(3, store().replace(parse(PROLOGUE)).generation());
    }

    @Test
    void everyFieldSurvivesTheRoundTripIncludingNonLatinNames() throws Exception {
        StartListStore store = store();
        store.replace(parse("""
                Name,Vorname,Verein,Verband,StNr,Klasse,Strecke,Startzeit,Jahrgang,Geschlecht,Nation
                VOBORNÍKOVÁ,Tereza,SKP Kornspitz Jablonec,Český svaz biatlonu,0012,Schüler U12 m,0.8,10:00:15,2000,w,CZE
                """));

        StartListEntry entry = store().current().entries().getFirst();

        assertEquals("0012", entry.bib());
        assertEquals("Schüler U12 m", entry.className());
        assertEquals("10:00:15", entry.startTime());
        assertEquals("VOBORNÍKOVÁ", entry.lastName());
        assertEquals("Tereza", entry.firstName());
        assertEquals("SKP Kornspitz Jablonec", entry.club());
        assertEquals("Český svaz biatlonu", entry.association());
        assertEquals("0.8", entry.course());
        assertEquals("2000", entry.birthYear());
        assertEquals("w", entry.gender());
        assertEquals("CZE", entry.nation());
    }

    /**
     * The label is an operator-supplied file name and may contain anything a file name can. The
     * properties format must carry it back unchanged instead of losing the rest of the file to a
     * line break that looks like a new key.
     */
    @Test
    void aFileNameWithLineBreaksSurvivesThePersistedFormat() throws Exception {
        String nasty = "Start\nliste=x\r\n#kommentar.csv";
        StartListStore store = store();
        store.replace(StartListParser.parse(PROLOGUE.getBytes(StandardCharsets.UTF_8), nasty));

        CanonicalStartList reloaded = store().current();

        assertEquals(nasty, reloaded.sourceLabel());
        assertEquals(1, reloaded.generation());
        assertEquals(2, reloaded.entries().size());
    }

    @Test
    void aTruncatedFileIsRejectedInsteadOfBeingAcceptedAsAShorterStartList() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        String persisted = Files.readString(store.path(), StandardCharsets.ISO_8859_1);
        Files.writeString(store.path(),
                persisted.replace("entries.1.bib=13\n", "").replace("entries.1.bib=13\r\n", ""),
                StandardCharsets.ISO_8859_1);

        IOException ex = assertThrows(IOException.class, () -> store());

        assertTrue(ex.getMessage().contains("entries.1.bib"), ex.getMessage());
    }

    @Test
    void anUnknownFileVersionIsRejected() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        String persisted = Files.readString(store.path(), StandardCharsets.ISO_8859_1);
        Files.writeString(store.path(), persisted.replace("startlist.version=1",
                "startlist.version=99"), StandardCharsets.ISO_8859_1);

        assertThrows(IOException.class, () -> store());
    }

    @Test
    void aPersistedListThatBreaksACanonicalRuleIsRejected() throws Exception {
        StartListStore store = store();
        store.replace(parse(PROLOGUE));
        String persisted = Files.readString(store.path(), StandardCharsets.ISO_8859_1);
        Files.writeString(store.path(), persisted.replace("entries.1.bib=13", "entries.1.bib=12"),
                StandardCharsets.ISO_8859_1);

        assertThrows(IOException.class, () -> store());
    }

    @Test
    void theStoreLivesNextToTheOrganiserConfiguration() throws Exception {
        Path config = temp.resolve("bridge.properties");

        StartListStore store = StartListStore.besideConfig(config);

        assertEquals(temp.resolve("startlist.properties"), store.path());
        assertEquals(0, store.current().generation());
    }

    /** {@code Properties.store} writes a timestamp comment, which is not part of the content. */
    private static void assertArrayEqualsIgnoringComments(byte[] expected, byte[] actual) {
        assertEquals(withoutComments(expected), withoutComments(actual));
    }

    private static String withoutComments(byte[] content) {
        return new String(content, StandardCharsets.ISO_8859_1).lines()
                .filter(line -> !line.startsWith("#"))
                .sorted()
                .reduce("", (all, line) -> all + line + "\n");
    }
}
