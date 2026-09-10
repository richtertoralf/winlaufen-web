package de.winlaufen.web.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens when a bridge and a live server do not run the same version.
 *
 * <p>Both can be installed separately — bridge only here, presentation node there — so a mixed pair
 * is a real operational state, not a thought experiment. The parser rejects unknown properties, and
 * that single decision determines which direction survives an additive change. These tests pin the
 * result so the upgrade order documented for operators cannot quietly stop being true.
 *
 * <p>The fixture below is the exact shape a 0.4.0 bridge writes, taken from a snapshot produced by
 * the released contract of tag {@code v0.4.0}. Only the direction "old sender, new receiver" can be
 * tested here; the other one needs the old parser and was verified against that build separately —
 * see {@code docs/RELEASE.md}.
 */
class MixedVersionTest {

    /** A snapshot as the 0.4.0 contract writes it: no {@code clockSample} anywhere. */
    private static final String SNAPSHOT_0_4_0 = """
            {"type":"snapshot","schemaVersion":1,"channelId":"local","streamId":"s1",\
            "sourceRevision":7,"state":{"sourceHealth":"CONNECTED","clock":"10:14:37",\
            "competition":{"type":"Standardwettkampf","evaluationMode":1,"classCount":1,\
            "winSpringenPosition":0,"roundOrHeat":0,"classes":[{"index":0,"name":"H30",\
            "roundsOrTeamSize":1,"snapshot":{"sourceRevision":7,"headers":["Rang","StNr"],\
            "rows":[["1","201"]]}}]},"currentFinish":{"classIndex":0,"rowIndex":0,\
            "snapshotSourceRevision":7},"message":null},"presentation":{"showClub":true,\
            "showAssociation":true,"showNation":false,"showShooting":true,\
            "showPublicMessages":false}}""";

    /** Old bridge, new live server: everything arrives, the measurement is simply absent. */
    @Test
    void aSnapshotFromTheOlderBridgeIsAccepted() throws Exception {
        SnapshotEnvelope value = ContractJson.readSnapshot(SNAPSHOT_0_4_0);

        assertEquals(7, value.sourceRevision());
        assertEquals(SourceHealth.CONNECTED, value.state().sourceHealth());
        assertEquals("10:14:37", value.state().clock());
        assertEquals(1, value.state().competition().classes().size());
        assertEquals("201", value.state().competition().classes().get(0)
                .snapshot().rows().get(0).get(1));
        assertNotNull(value.state().currentFinish());

        // The one thing that older bridge cannot supply.
        assertNull(value.state().clockSample());
    }

    /**
     * A missing measurement must stay a missing measurement. Nothing may substitute a value here —
     * an invented sample would claim an observation that never happened.
     */
    @Test
    void anAbsentClockSampleIsNotInvented() throws Exception {
        String json = ContractJson.snapshot(ContractJson.readSnapshot(SNAPSHOT_0_4_0));
        assertTrue(json.contains("\"clockSample\":null"), json);
        assertNull(ContractJson.readSnapshot(json).state().clockSample());
    }

    /**
     * The reason the other direction cannot work: the parser rejects properties it does not know.
     * A 0.4.0 live server therefore refuses a snapshot carrying {@code clockSample} — which is what
     * makes the upgrade order "live server first, then bridge" binding rather than advisory.
     */
    @Test
    void anUnknownPropertyIsRejectedRatherThanIgnored() {
        String withUnknownField = SNAPSHOT_0_4_0.replace(
                "\"message\":null", "\"message\":null,\"somethingNewer\":{\"a\":1}");

        assertThrows(Exception.class, () -> ContractJson.readSnapshot(withUnknownField));
    }

    /**
     * The schema version stays 1 on purpose. Raising it would make the working direction fail too:
     * the envelope check compares for equality, so a new live server would start rejecting every
     * 0.4.0 bridge — the very pairing the upgrade order relies on.
     */
    @Test
    void theSchemaVersionStaysComparableAcrossThisChange() throws Exception {
        assertEquals(1, SnapshotEnvelope.SCHEMA_VERSION);
        assertEquals(1, ContractJson.readSnapshot(SNAPSHOT_0_4_0).schemaVersion());

        String otherVersion = SNAPSHOT_0_4_0.replace("\"schemaVersion\":1", "\"schemaVersion\":2");
        assertThrows(ContractViolationException.class,
                () -> ContractJson.readSnapshot(otherVersion));
    }

    /** The start list was not touched by this change, so it stays compatible both ways. */
    @Test
    void theStartListMessageIsUnchanged() throws Exception {
        String startList = """
                {"type":"startlist","schemaVersion":1,"channelId":"local","streamId":"s1",\
                "generation":2,"source":"IMPORT_CSV","sourceLabel":"Startliste.csv",\
                "entries":[{"bib":"201","className":"H30","startTime":"09:30:00",\
                "lastName":"Mustermann","firstName":"Max","club":"SV","association":"SVS",\
                "course":"10 km","birthYear":"1990","gender":"M","nation":"GER"}]}""";

        StartListEnvelope value = ContractJson.readStartList(startList);
        assertEquals(2, value.generation());
        assertEquals("201", value.entries().get(0).bib());
    }
}
