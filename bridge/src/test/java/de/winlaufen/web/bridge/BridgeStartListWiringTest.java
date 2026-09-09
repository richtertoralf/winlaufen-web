package de.winlaufen.web.bridge;

import de.winlaufen.web.bridge.startlist.StartListImport;
import de.winlaufen.web.bridge.startlist.StartListParser;
import de.winlaufen.web.bridge.startlist.StartListStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the bridge picks up its start list at startup.
 *
 * <p>The start list is the one piece of state the bridge keeps on disk, so these tests pin where
 * it comes from and, above all, that a broken file cannot stop the process. Live results do not
 * depend on a start list and must keep working.
 */
class BridgeStartListWiringTest {

    @TempDir
    Path temp;

    private static final String CSV = """
            StNr,Klasse,Name,Startzeit
            12,U16,MÜLLER,10:00:15
            """;

    private static StartListImport parse(String csv) {
        return StartListParser.parse(csv.getBytes(StandardCharsets.UTF_8), "startliste.csv");
    }

    @Test
    void theStartListLivesNextToTheOrganiserConfiguration() {
        Path config = temp.resolve("bridge.properties");

        StartListStore store = BridgeMain.openStartLists(config);

        assertEquals(temp.resolve("startlist.properties"), store.path());
    }

    @Test
    void aBridgeWithoutAnImportStartsWithNoStartList() {
        StartListStore store = BridgeMain.openStartLists(temp.resolve("bridge.properties"));

        assertEquals(0, store.current().generation());
        assertFalse(store.current().isPresent());
        assertTrue(store.current().entries().isEmpty());
    }

    @Test
    void aRestartAdoptsThePreviouslyImportedStartListWithoutCountingAsAnImport() throws Exception {
        Path config = temp.resolve("bridge.properties");
        StartListStore first = BridgeMain.openStartLists(config);
        first.replace(parse(CSV));

        StartListStore restarted = BridgeMain.openStartLists(config);

        assertEquals(1, restarted.current().generation());
        assertEquals("MÜLLER", restarted.current().find("U16", "12").lastName());
    }

    /**
     * A start list that cannot be read must not take the bridge down with it. The operator is told
     * and can re-import; the unusable file stays untouched until then.
     */
    @Test
    void anUnreadableStartListLeavesTheBridgeStartableWithoutOne() throws Exception {
        Path config = temp.resolve("bridge.properties");
        Path file = temp.resolve("startlist.properties");
        Files.writeString(file, "startlist.version=1\nentries.count=5\n");

        StartListStore store = BridgeMain.openStartLists(config);

        assertEquals(0, store.current().generation());
        assertEquals(file, store.path());
        assertTrue(Files.exists(file), "the unusable file is left for inspection, never deleted");
    }

    @Test
    void anImportAfterThatRecoveryReplacesTheUnusableFile() throws Exception {
        Path config = temp.resolve("bridge.properties");
        Files.writeString(temp.resolve("startlist.properties"), "kaputt\n");
        StartListStore store = BridgeMain.openStartLists(config);

        store.replace(parse(CSV));

        assertEquals(1, store.current().generation());
        assertEquals(1, BridgeMain.openStartLists(config).current().generation(),
                "the repaired file is readable again after the next import");
    }
}
