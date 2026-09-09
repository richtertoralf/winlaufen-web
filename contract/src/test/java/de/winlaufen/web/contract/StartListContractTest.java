package de.winlaufen.web.contract;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The start-list publication as it goes over the wire.
 *
 * <p>The message is its own type next to the snapshot, so these tests pin both that a real list
 * survives the round trip unchanged and that an absent list is expressible at all — a live server
 * must be able to learn that its bridge has no start list.
 */
class StartListContractTest {

    private static StartListRow row(String bib, String className) {
        return new StartListRow(bib, className, "10:00:15", "MÜLLER", "Anna", "SV Größenhain",
                "SVSAC", "5.0", "2010", "w", "GER");
    }

    private static StartListEnvelope envelope(List<StartListRow> rows) {
        return new StartListEnvelope("local", "stream-a", rows.isEmpty() ? 0 : 4, "IMPORT_CSV",
                "Startliste.csv", rows);
    }

    // --- Round trip ----------------------------------------------------------------------------

    @Test
    void everyFieldSurvivesTheRoundTripUnchanged() throws Exception {
        StartListRow row = new StartListRow("0012", "Schüler U12 m", "10:00:15.7", "VOBORNÍKOVÁ",
                "Tereza", "SKP Kornspitz Jablonec", "Český svaz biatlonu", "0.8", "2000", "w",
                "CZE");

        StartListEnvelope back = ContractJson.readStartList(
                ContractJson.startList(envelope(List.of(row))));

        assertEquals(row, back.entries().getFirst());
        assertEquals("startlist", back.type());
        assertEquals(SnapshotEnvelope.SCHEMA_VERSION, back.schemaVersion());
        assertEquals("local", back.channelId());
        assertEquals("stream-a", back.streamId());
        assertEquals(4, back.generation());
        assertEquals("IMPORT_CSV", back.source());
        assertEquals("Startliste.csv", back.sourceLabel());
        assertTrue(back.present());
    }

    @Test
    void aBibKeepsItsTextAndIsNeverANumber() throws Exception {
        List<StartListRow> rows = List.of(row("0012", "U16"), row("12", "U18"), row("A12", "U20"));

        StartListEnvelope back = ContractJson.readStartList(
                ContractJson.startList(envelope(rows)));

        assertEquals(List.of("0012", "12", "A12"),
                back.entries().stream().map(StartListRow::bib).toList());
    }

    @Test
    void theOrderOfTheEntriesIsPreserved() throws Exception {
        List<StartListRow> rows = List.of(row("7", "U18"), row("3", "U16"), row("9", "U18"));

        StartListEnvelope back = ContractJson.readStartList(
                ContractJson.startList(envelope(rows)));

        assertEquals(List.of("7", "3", "9"),
                back.entries().stream().map(StartListRow::bib).toList(),
                "the wire never reorders what the import delivered");
    }

    @Test
    void emptyOptionalValuesStayEmptyStrings() throws Exception {
        StartListRow sparse = new StartListRow("12", "U16", "", "", "", "", "", "", "", "", "");

        StartListEnvelope back = ContractJson.readStartList(
                ContractJson.startList(envelope(List.of(sparse))));

        assertEquals(sparse, back.entries().getFirst());
    }

    // --- Absence -------------------------------------------------------------------------------

    @Test
    void anAbsentStartListIsGenerationZeroWithoutEntries() throws Exception {
        StartListEnvelope back = ContractJson.readStartList(
                ContractJson.startList(envelope(List.of())));

        assertEquals(0, back.generation());
        assertTrue(back.entries().isEmpty());
        assertFalse(back.present(), "this is how a bridge says it has no start list");
    }

    @Test
    void generationAndEntriesMustAgreeOnWhetherAStartListExists() {
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(
                new StartListEnvelope("local", "s", 0, "IMPORT_CSV", "x.csv",
                        List.of(row("1", "U16")))),
                "entries without a generation would be a start list nobody can version");
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(
                new StartListEnvelope("local", "s", 3, "IMPORT_CSV", "x.csv", List.of())),
                "an empty import does not exist; only absence has no entries");
    }

    // --- Message type --------------------------------------------------------------------------

    @Test
    void theMessageTypeIsReadableWithoutParsingTheWholeDocument() {
        assertEquals("startlist", ContractJson.typeOf(ContractJson.startList(
                envelope(List.of(row("1", "U16"))))));
        assertEquals("snapshot", ContractJson.typeOf(ContractJson.snapshot(
                new SnapshotEnvelope("local", "stream-a", 1, CanonicalState.empty(),
                        PresentationConfig.defaults()))));
        assertEquals("ack", ContractJson.typeOf(ContractJson.ack(
                new AckEnvelope("local", "stream-a", 1))));
    }

    @Test
    void theTypeIsFoundRegardlessOfItsPositionAndRejectedWhenUnusable() {
        assertEquals("startlist", ContractJson.typeOf(
                "{\"schemaVersion\":1,\"entries\":[],\"type\":\"startlist\"}"));
        assertThrows(ContractViolationException.class, () -> ContractJson.typeOf("[]"));
        assertThrows(ContractViolationException.class, () -> ContractJson.typeOf("{\"type\":7}"));
        assertThrows(ContractViolationException.class, () -> ContractJson.typeOf("{\"a\":1}"));
        assertThrows(ContractViolationException.class, () -> ContractJson.typeOf("kaputt"));
    }

    // --- Rejected payloads ---------------------------------------------------------------------

    @Test
    void aSnapshotIsNotAcceptedAsAStartListAndViceVersa() {
        String snapshot = ContractJson.snapshot(new SnapshotEnvelope("local", "stream-a", 1,
                CanonicalState.empty(), PresentationConfig.defaults()));
        String startList = ContractJson.startList(envelope(List.of(row("1", "U16"))));

        assertThrows(Exception.class, () -> ContractJson.readStartList(snapshot));
        assertThrows(Exception.class, () -> ContractJson.readSnapshot(startList));
    }

    @Test
    void anEntryWithoutBibOrClassIsRejected() {
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(envelope(
                List.of(new StartListRow("", "U16", "", "", "", "", "", "", "", "", "")))));
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(envelope(
                List.of(new StartListRow("12", "", "", "", "", "", "", "", "", "", "")))));
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(envelope(
                List.of(new StartListRow("12", "U16", null, "", "", "", "", "", "", "", "")))));
    }

    @Test
    void theSameBibTwiceInOneClassIsRejectedButTwoClassesAreFine() {
        assertThrows(ContractViolationException.class,
                () -> ContractJson.startList(envelope(List.of(row("12", "U16"), row("12", "U16")))));

        assertNotNull(ContractJson.startList(envelope(List.of(row("12", "U16"), row("12", "U18")))));
    }

    @Test
    void envelopeMetadataIsValidated() {
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(
                new StartListEnvelope("", "stream-a", 1, "IMPORT_CSV", "x", List.of(row("1", "U16")))));
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(
                new StartListEnvelope("local", "", 1, "IMPORT_CSV", "x", List.of(row("1", "U16")))));
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(
                new StartListEnvelope("local", "stream-a", -1, "IMPORT_CSV", "x", List.of())));
        assertThrows(ContractViolationException.class, () -> ContractJson.startList(
                new StartListEnvelope("local", "stream-a", 1, null, "x", List.of(row("1", "U16")))));
    }

    @Test
    void anOverlongValueIsRejected() {
        String tooLong = "x".repeat(ContractLimits.MAX_START_LIST_VALUE_CHARS + 1);

        assertThrows(ContractViolationException.class, () -> ContractJson.startList(envelope(
                List.of(new StartListRow("12", "U16", "", tooLong, "", "", "", "", "", "", "")))));
    }

    @Test
    void unknownFieldsAreRejectedLikeEverywhereElseInTheContract() {
        assertThrows(Exception.class, () -> ContractJson.readStartList(
                "{\"type\":\"startlist\",\"schemaVersion\":1,\"channelId\":\"local\","
                        + "\"streamId\":\"s\",\"generation\":0,\"source\":\"IMPORT_CSV\","
                        + "\"sourceLabel\":\"\",\"entries\":[],\"extra\":1}"));
    }

    // --- Realistic size ------------------------------------------------------------------------

    @Test
    void aRealisticEventSizeSurvivesTheRoundTrip() throws Exception {
        List<StartListRow> rows = realisticRows(1999, 46);

        String json = ContractJson.startList(envelope(rows));
        StartListEnvelope back = ContractJson.readStartList(json);

        assertEquals(1999, back.entries().size());
        assertEquals(rows, back.entries());
        assertEquals(46, back.entries().stream().map(StartListRow::className).distinct().count());
    }

    /**
     * A start list the bridge is allowed to import must always be publishable. Two separate
     * bounds would let an import succeed that the transport could never carry, and the operator
     * would see a successful import whose data never arrives.
     */
    @Test
    void theLargestImportableStartListStillFitsTheMessageLimit() {
        List<StartListRow> rows = realisticRows(ContractLimits.MAX_START_LIST_ENTRIES, 200);

        String json = ContractJson.startList(envelope(rows));

        assertTrue(json.length() < ContractLimits.MAX_JSON_CHARS,
                "the maximum start list encodes to " + json.length() + " characters, over the "
                        + ContractLimits.MAX_JSON_CHARS + " character message limit");
        assertTrue(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        < ContractLimits.MAX_INGEST_MESSAGE_BYTES,
                "it must also fit the ingest frame limit");
    }

    /** Field lengths in the order of a real WinLaufen export, deliberately on the long side. */
    private static List<StartListRow> realisticRows(int count, int classes) {
        List<StartListRow> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rows.add(new StartListRow(Integer.toString(index + 1),
                    "Schüler U12 männlich Gruppe " + (index % classes),
                    "10:" + String.format("%02d", index % 60) + ":15",
                    "MUSTERMANN-SCHNEIDER", "Maximilian Alexander",
                    "SK Dresden Niedersedlitz / SGO`th", "Bayerischer Skiverband", "10.5",
                    "2010", "m", "GER"));
        }
        return rows;
    }
}
