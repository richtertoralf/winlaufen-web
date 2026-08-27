package de.winlaufen.web.contract;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractJsonTest {

    @Test
    void roundTripsAuthoritativeSnapshotAndAck() throws Exception {
        var table = new ClassSnapshot(4, List.of("Rang", "Schießen"), List.of(List.of("01", "1 0 2 0 ")));
        var competition = new Competition("Standardwettkampf", 1, 1, 0, 0,
                List.of(new CompetitionClass(0, "U15", 0, table)));
        var envelope = new SnapshotEnvelope("local", "stream", 5,
                new CanonicalState(SourceHealth.CONNECTED, "99:99:99", competition,
                        new CurrentFinish(0, 0, 4), "Hallo"),
                PresentationConfig.defaults());

        assertEquals(envelope, ContractJson.readSnapshot(ContractJson.snapshot(envelope)));

        var ack = new AckEnvelope("local", "stream", 5);
        assertEquals(ack, ContractJson.readAck(ContractJson.ack(ack)));
    }

    @Test
    void supportsEmptyOptionalState() throws Exception {
        var value = new SnapshotEnvelope("local", "stream", 0, CanonicalState.empty(),
                PresentationConfig.defaults());
        assertEquals(value, ContractJson.readSnapshot(ContractJson.snapshot(value)));
    }

    @Test
    void rejectsUnknownFieldOnAnOtherwiseValidSnapshot() {
        String valid = ContractJson.snapshot(new SnapshotEnvelope("local", "stream", 1,
                CanonicalState.empty(), PresentationConfig.defaults()));
        String withExtraField = valid.replaceFirst("\\{", "{\"unknown\":1,");
        assertThrows(Exception.class, () -> ContractJson.readSnapshot(withExtraField));
    }

    @Test
    void rejectsForeignSchemaVersionWithoutPartialInterpretation() {
        String valid = ContractJson.snapshot(new SnapshotEnvelope("local", "stream", 1,
                CanonicalState.empty(), PresentationConfig.defaults()));
        String otherMajor = valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2");
        assertThrows(ContractViolationException.class, () -> ContractJson.readSnapshot(otherMajor));

        String wrongType = valid.replace("\"type\":\"snapshot\"", "\"type\":\"delta\"");
        assertThrows(ContractViolationException.class, () -> ContractJson.readSnapshot(wrongType));
    }

    @Test
    void rejectsWrongAckVersionAndOversizedPayload() {
        assertThrows(Exception.class, () -> ContractJson.readAck(
                "{\"type\":\"ack\",\"schemaVersion\":2,\"channelId\":\"local\","
                        + "\"streamId\":\"s\",\"sourceRevision\":0}"));
        assertThrows(ContractViolationException.class,
                () -> ContractJson.readSnapshot("x".repeat(ContractLimits.MAX_JSON_CHARS + 1)));
        assertThrows(ContractViolationException.class,
                () -> ContractJson.readAck("x".repeat(ContractLimits.MAX_ACK_CHARS + 1)));
    }

    @Test
    void rejectsForgedAckIdentifiers() {
        assertThrows(ContractViolationException.class,
                () -> ContractJson.ack(new AckEnvelope("", "stream", 1)));
        assertThrows(ContractViolationException.class,
                () -> ContractJson.ack(new AckEnvelope("local", " ", 1)));
        assertThrows(ContractViolationException.class,
                () -> ContractJson.ack(new AckEnvelope("local", "stream", -1)));
        assertThrows(ContractViolationException.class, () -> ContractJson.ack(new AckEnvelope(
                "x".repeat(ContractLimits.MAX_IDENTIFIER_CHARS + 1), "stream", 1)));
    }

    @Test
    void enforcesStructuralTableLimits() {
        assertThrows(ContractViolationException.class,
                () -> validate(table(ContractLimits.MAX_HEADERS + 1, 1)));
        assertThrows(ContractViolationException.class,
                () -> validate(table(2, ContractLimits.MAX_ROWS + 1)));
        assertThrows(ContractViolationException.class, () -> validate(new ClassSnapshot(1,
                List.of("Rang"), List.of(List.of("x".repeat(ContractLimits.MAX_CELL_CHARS + 1))))));
        assertThrows(ContractViolationException.class, () -> validate(new ClassSnapshot(1,
                List.of("Rang", "StNr"), List.of(List.of("1")))));
        assertThrows(ContractViolationException.class,
                () -> validate(new ClassSnapshot(1, List.of(), List.of())));
    }

    @Test
    void acceptsTablesExactlyOnTheLimit() {
        validate(table(ContractLimits.MAX_HEADERS, 1));
        validate(new ClassSnapshot(1, List.of("Rang"),
                List.of(List.of("x".repeat(ContractLimits.MAX_CELL_CHARS)))));
    }

    @Test
    void rejectsInconsistentIndicesAndOversizedMessage() {
        var competition = new Competition("Lauf", 1, 1, 0, 0,
                List.of(new CompetitionClass(0, "U15", 0, null)));
        assertThrows(ContractViolationException.class, () -> ContractJson.validateState(
                new CanonicalState(SourceHealth.CONNECTED, null, competition,
                        new CurrentFinish(1, 0, 1), null), PresentationConfig.defaults()));
        assertThrows(ContractViolationException.class, () -> ContractJson.validateState(
                new CanonicalState(SourceHealth.CONNECTED, null, competition,
                        new CurrentFinish(0, -1, 1), null), PresentationConfig.defaults()));
        assertThrows(ContractViolationException.class, () -> ContractJson.validateState(
                new CanonicalState(SourceHealth.CONNECTED, null, null, null,
                        "x".repeat(ContractLimits.MAX_MESSAGE_CHARS + 1)),
                PresentationConfig.defaults()));
        assertThrows(ContractViolationException.class, () -> ContractJson.validateState(
                new CanonicalState(SourceHealth.CONNECTED, null,
                        new Competition("Lauf", 1, 2, 0, 0,
                                List.of(new CompetitionClass(0, "U15", 0, null))),
                        null, null),
                PresentationConfig.defaults()));
    }

    @Test
    void contractViolationsAreDistinguishableFromTransportProblems() {
        var thrown = assertThrows(ContractViolationException.class,
                () -> ContractJson.readSnapshot("x".repeat(ContractLimits.MAX_JSON_CHARS + 1)));
        assertTrue(thrown instanceof IllegalArgumentException);
    }

    private static void validate(ClassSnapshot snapshot) {
        var competition = new Competition("Lauf", 1, 1, 0, 0,
                List.of(new CompetitionClass(0, "U15", 0, snapshot)));
        ContractJson.validateState(
                new CanonicalState(SourceHealth.CONNECTED, null, competition, null, null),
                PresentationConfig.defaults());
    }

    private static ClassSnapshot table(int columns, int rowCount) {
        List<String> headers = new ArrayList<>(columns);
        for (int index = 0; index < columns; index++) {
            headers.add("H" + index);
        }
        List<List<String>> rows = new ArrayList<>(rowCount);
        for (int index = 0; index < rowCount; index++) {
            rows.add(headers.stream().map(header -> "v").toList());
        }
        return new ClassSnapshot(1, headers, rows);
    }
}
