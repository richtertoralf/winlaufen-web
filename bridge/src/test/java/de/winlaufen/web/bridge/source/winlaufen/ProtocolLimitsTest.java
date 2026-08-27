package de.winlaufen.web.bridge.source.winlaufen;

import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.ContractLimits;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SnapshotEnvelope;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The source entry boundary must reject wire data that could not be published over the contract
 * afterwards, so a single oversized telegram can never poison the canonical state.
 */
class ProtocolLimitsTest {

    @Test
    void acceptsATableExactlyOnTheContractLimits() throws Exception {
        ResultBlock block = read(resultBlock(ContractLimits.MAX_HEADERS, 1,
                "x".repeat(ContractLimits.MAX_CELL_CHARS)));
        assertNotNull(block);
        assertEquals(ContractLimits.MAX_HEADERS, block.headers().size());
    }

    @Test
    void rejectsMoreColumnsThanTheContractCanPublish() {
        assertThrows(WinLaufenProtocolReader.ProtocolException.class,
                () -> read(resultBlock(ContractLimits.MAX_HEADERS + 1, 1, "v")));
    }

    @Test
    void rejectsCellsLargerThanTheContractCanPublish() {
        assertThrows(WinLaufenProtocolReader.ProtocolException.class,
                () -> read(resultBlock(2, 1, "x".repeat(ContractLimits.MAX_CELL_CHARS + 1))));
    }

    @Test
    void rejectsOversizedClassNamesAndCompetitionType() {
        assertThrows(WinLaufenProtocolReader.ProtocolException.class,
                () -> read(resultBlock(2, 1, "v", "x".repeat(ContractLimits.MAX_NAME_CHARS + 1), "U13 m")));
        assertThrows(WinLaufenProtocolReader.ProtocolException.class,
                () -> read(resultBlock(2, 1, "v", "Standardwettkampf",
                        "x".repeat(ContractLimits.MAX_NAME_CHARS + 1))));
    }

    @Test
    void rejectsOversizedServerMessage() {
        assertThrows(WinLaufenProtocolReader.ProtocolException.class,
                () -> readMessage("x".repeat(ContractLimits.MAX_MESSAGE_CHARS + 1)));
        assertDoesNotThrow(() -> readMessage("x".repeat(ContractLimits.MAX_MESSAGE_CHARS)));
    }

    @Test
    void everyBlockAcceptedByTheReaderStaysPublishable() throws Exception {
        ResultBlock block = read(resultBlock(ContractLimits.MAX_HEADERS, 3, "wert"));
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        store.result(block);
        store.message("x".repeat(ContractLimits.MAX_MESSAGE_CHARS));

        var snapshot = store.get();
        assertDoesNotThrow(() -> ContractJson.snapshot(new SnapshotEnvelope("local", "stream",
                snapshot.sourceRevision(), snapshot.state(), snapshot.presentation())));
    }

    private static ResultBlock read(byte[] wire) throws Exception {
        AtomicReference<ResultBlock> result = new AtomicReference<>();
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(wire))) {
            new WinLaufenProtocolReader(input, clock -> { }, result::set, message -> { }).readNext();
        }
        return result.get();
    }

    private static void readMessage(String text) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            java.util.Vector<Object> message = new java.util.Vector<>();
            message.add(text);
            message.add("nachricht");
            output.writeObject(message);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
            new WinLaufenProtocolReader(input, clock -> { }, block -> { }, value -> { }).readNext();
        }
    }

    private static byte[] resultBlock(int columns, int rowCount, String cell) throws Exception {
        return resultBlock(columns, rowCount, cell, "Standardwettkampf", "U13 m");
    }

    private static byte[] resultBlock(int columns, int rowCount, String cell, String competitionType,
                                      String firstClassName) throws Exception {
        List<String> headers = new ArrayList<>(columns);
        for (int index = 0; index < columns; index++) {
            headers.add("H" + index);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(competitionType);
            output.writeObject(1);
            output.writeObject(2);
            output.writeObject(new String[]{firstClassName, "U13 w"});
            output.writeObject(new int[]{0, 0});
            output.writeObject(0);
            output.writeObject(0);
            output.writeObject(0);
            output.writeObject(0);
            for (int row = 0; row < rowCount; row++) {
                Object[] cells = new Object[columns];
                for (int column = 0; column < columns; column++) {
                    cells[column] = column == 0 ? cell : "v";
                }
                output.writeObject(cells);
            }
            output.writeObject("tabelle");
            output.writeObject(headers.toArray(String[]::new));
            output.writeObject("ende");
        }
        return buffer.toByteArray();
    }
}
