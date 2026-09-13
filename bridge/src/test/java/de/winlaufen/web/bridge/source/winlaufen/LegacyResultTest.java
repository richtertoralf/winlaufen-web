package de.winlaufen.web.bridge.source.winlaufen;

import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.PresentationConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

class LegacyResultTest {
    @Test void normalizesLegacyIntoTheSameCanonicalResultsAsCurrentWireFormat() throws Exception {
        byte[] wire = stream(output -> {
            output.writeObject(new Vector<>(List.of("10:29:27")));
            output.writeObject(LegacyProtocolFixture.result());
            output.writeObject("ende");
            output.writeObject(new Vector<>(List.of("10:29:28")));
            output.writeObject(new Vector<>(List.of("10:29:29")));
        });
        var store = store();
        try (var input = new ObjectInputStream(new ByteArrayInputStream(wire))) {
            var reader = reader(input, store);
            reader.readNext();
            assertEquals("10:29:27", store.get().state().clock());
            reader.readNext();
            assertNull(store.get().state().competition(), "Wait for separate ende before publication");
            reader.readNext();
            var state = store.get().state();
            assertEquals(2, state.competition().evaluationMode());
            assertEquals(21, state.competition().classes().size());
            assertEquals(10, state.currentFinish().classIndex());
            assertEquals(List.of(LegacyProtocolFixture.ROW),
                    state.competition().classes().get(10).snapshot().rows());
            reader.readNext();
            assertEquals("10:29:28", store.get().state().clock());
            reader.readNext();
            assertEquals("10:29:29", store.get().state().clock());
            assertEquals(state.competition(), store.get().state().competition());
        }
        var current = store();
        byte[] currentWire = stream(output -> {
            output.writeObject("Uhr10:29:27");
            LegacyProtocolFixture.writeCurrentEquivalent(output);
        });
        try (var input = new ObjectInputStream(new ByteArrayInputStream(currentWire))) {
            var reader = reader(input, current);
            reader.readNext();
            reader.readNext();
        }
        assertEquals(current.get().state().competition(), store.get().state().competition());
        assertEquals(current.get().state().currentFinish(), store.get().state().currentFinish());
    }

    @Test void missingTerminatorDoesNotPublishOrSwallowFollowingClock() throws Exception {
        byte[] wire = stream(output -> {
            output.writeObject(LegacyProtocolFixture.result());
            output.writeObject(new Vector<>(List.of("10:29:28")));
        });
        var store = store();
        try (var input = new ObjectInputStream(new ByteArrayInputStream(wire))) {
            var reader = reader(input, store);
            reader.readNext();
            reader.readNext();
            assertNull(store.get().state().competition());
            assertEquals("10:29:28", store.get().state().clock());
        }
    }

    @Test void validatesLegacyWithExistingResultGuardsAndOnlyConvertsRankAndBibIntegers() throws Exception {
        for (int variant = 0; variant < 5; variant++) {
            Vector<Object> vector = LegacyProtocolFixture.result();
            switch (variant) {
                case 0 -> vector.set(2, 20); // Inconsistent class count.
                case 1 -> vector.set(6, 21); // Class index outside array.
                case 2 -> vector.set(8, 1); // Finish index outside table.
                case 3 -> vector.set(10, new String[]{"Rang"}); // Header/row mismatch.
                case 4 -> ((Object[][]) vector.get(9))[0][2] = 123; // No general toString conversion.
                default -> throw new AssertionError();
            }
            byte[] wire = stream(output -> {
                output.writeObject(vector);
                output.writeObject("ende");
                output.writeObject(new Vector<>(List.of("10:29:29")));
            });
            var store = store();
            try (var input = new ObjectInputStream(new ByteArrayInputStream(wire))) {
                var reader = reader(input, store);
                reader.readNext();
                assertThrows(WinLaufenProtocolReader.ProtocolException.class, reader::readNext);
                assertNull(store.get().state().competition());
                reader.readNext();
                assertEquals("10:29:29", store.get().state().clock());
            }
        }
    }

    private static CanonicalStateStore store() {
        return new CanonicalStateStore(PresentationConfig.defaults(), null);
    }

    private static WinLaufenProtocolReader reader(ObjectInputStream input, CanonicalStateStore store) {
        return new WinLaufenProtocolReader(input, clock -> store.clock(clock.wireValue()),
                store::result, store::message);
    }

    private static byte[] stream(Writer writer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            writer.write(output);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface private interface Writer {
        void write(ObjectOutputStream output) throws Exception;
    }
}
