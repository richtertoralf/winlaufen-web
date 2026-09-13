package de.winlaufen.web.bridge.source.winlaufen;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolVariantTest {
    @Test
    void clocksAndCompleteResultsProvidePositiveEvidence() throws Exception {
        try (var input = stream(out -> {
            out.writeObject(new Vector<>(List.of("99:99:99")));
            out.writeObject("Uhr99:99:99");
            out.writeObject(LegacyProtocolFixture.result());
            out.writeObject("ende");
            LegacyProtocolFixture.writeCurrentEquivalent(out);
        })) {
            var variants = new ArrayList<ProtocolVariant>();
            var results = new ArrayList<ResultBlock>();
            var reader = new WinLaufenProtocolReader(input, ignored -> { }, results::add,
                    ignored -> { }, variants::add);
            reader.readNext();
            reader.readNext();
            assertEquals(List.of(ProtocolVariant.LEGACY, ProtocolVariant.CURRENT), variants);
            reader.readNext();
            assertEquals(2, variants.size(), "Vector alone is not evidence");
            reader.readNext();
            assertEquals(ProtocolVariant.LEGACY, variants.getLast());
            reader.readNext();
            assertEquals(ProtocolVariant.CURRENT, variants.getLast());
            assertEquals(results.get(0).rows(), results.get(1).rows());
            assertEquals(List.of(LegacyProtocolFixture.ROW), results.getFirst().rows());
        }
    }

    @Test
    void incompleteUnsupportedAndInvalidObjectsNeverClassify() throws Exception {
        for (int scenario = 0; scenario < 6; scenario++) {
            int selected = scenario;
            try (var input = stream(out -> {
                var result = LegacyProtocolFixture.result();
                if (selected == 0) out.writeObject(result); // EOF without ende.
                if (selected == 1) {
                    out.writeObject(result);
                    out.writeObject(new Vector<>(List.of("Info", "nachricht")));
                }
                if (selected == 2 || selected == 3) {
                    if (selected == 2) result.set(2, 20); // Normal parser rejects metadata.
                    else ((Object[][]) result.get(9))[0][2] = 123; // Invalid cell.
                    out.writeObject(result);
                    out.writeObject("ende");
                }
                if (selected == 4) {
                    out.writeObject(123);
                    out.writeObject(new Vector<>(List.of("unknown")));
                    out.writeObject(new Vector<>(List.of("Info", "nachricht")));
                    out.writeObject("ende");
                }
                if (selected == 5) {
                    out.writeObject("Standardwettkampf");
                    out.writeObject("invalid evaluation mode");
                }
            })) {
                var variants = new ArrayList<ProtocolVariant>();
                var reader = new WinLaufenProtocolReader(input, ignored -> fail("Unexpected clock"),
                        ignored -> fail("Unexpected result"), ignored -> { }, variants::add);
                while (true) {
                    try { reader.readNext(); }
                    catch (EOFException done) { break; }
                    catch (WinLaufenProtocolReader.ProtocolException expected) { }
                }
                assertTrue(variants.isEmpty(), "scenario " + scenario);
            }
        }
    }

    private static ObjectInputStream stream(Writer writer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            writer.write(output);
        }
        return new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    @FunctionalInterface
    private interface Writer {
        void write(ObjectOutputStream output) throws Exception;
    }
}
