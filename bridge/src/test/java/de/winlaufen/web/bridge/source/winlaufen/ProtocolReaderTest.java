package de.winlaufen.web.bridge.source.winlaufen;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolReaderTest {
    @Test void readsRealRunningFixtureIncludingHandleReuse() throws Exception {
        List<ClockValue> clocks = new ArrayList<>();
        List<ResultBlock> blocks = new ArrayList<>();
        try (var source = getClass().getResourceAsStream("/protocol/running/server-stream.bin");
             var input = new ObjectInputStream(source)) {
            var reader = new WinLaufenProtocolReader(input, clocks::add, blocks::add);
            while (true) try { reader.readNext(); } catch (EOFException done) { break; }
        }
        assertEquals(91, clocks.size());
        assertEquals(7, blocks.size());
        assertEquals(List.of("Rang", "StNr", "Name, Vorname", "Verein", "Vbd", "Laufzeit", "Rückstand"), blocks.get(0).headers());
        assertEquals("TSCHARNKE Tim", blocks.get(6).rows().get(2).get(2));
        assertEquals(2, blocks.get(6).currentFinishIndex());
    }

    @Test void consumesNarrowVectorMessage() throws Exception {
        byte[] bytes = stream(out -> { Vector<String> message = new Vector<>(); message.add("Hallo"); message.add("nachricht"); out.writeObject(message); out.writeObject("Uhr10:00:00"); });
        List<ClockValue> clocks = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            var reader = new WinLaufenProtocolReader(input, clocks::add, ignored -> {}, messages::add); reader.readNext(); reader.readNext();
        }
        assertEquals(List.of("Hallo"), messages);
        assertEquals("10:00:00", clocks.getFirst().wireValue());
    }

    @Test void publishesEveryClockTelegramUnchangedWithoutComparingValues() throws Exception {
        byte[] bytes = stream(out -> {
            out.writeObject("Uhr01:23:45");
            out.writeObject("Uhr01:23:45");
            out.writeObject("Uhr08:00:10");
            out.writeObject("Uhr07:15:00");
            out.writeObject("Uhr01:00:00");
            out.writeObject("Uhr20:00:00");
            out.writeObject("Uhr23:59:59");
            out.writeObject("Uhr00:00:00");
            out.writeObject("Uhr99:99:99");
        });
        List<ClockValue> clocks = new ArrayList<>();
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            var reader = new WinLaufenProtocolReader(input, clocks::add, ignored -> {});
            for (int i = 0; i < 9; i++) reader.readNext();
        }
        assertEquals(List.of("01:23:45", "01:23:45", "08:00:10", "07:15:00", "01:00:00",
                        "20:00:00", "23:59:59", "00:00:00", "99:99:99"),
                clocks.stream().map(ClockValue::wireValue).toList());
    }

    @Test void longLegitimateClockStreamHasNoCumulativeLifetimeLimit() throws Exception {
        byte[] bytes = stream(out -> {
            for (int i = 0; i < 25_000; i++) out.writeObject("Uhr" + String.format("%02d:%02d:%02d", i % 100, (i / 100) % 100, (i / 10_000) % 100));
        });
        int[] count = {0};
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            var reader = new WinLaufenProtocolReader(input, ignored -> count[0]++, ignored -> {});
            for (int i = 0; i < 25_000; i++) reader.readNext();
        }
        assertEquals(25_000, count[0]);
    }

    @Test void invalidBlockNeverPublishes() throws Exception {
        byte[] bytes = stream(out -> { out.writeObject("Standardwettkampf"); out.writeObject(1); out.writeObject(1); out.writeObject(new String[]{"Klasse"}); out.writeObject(new int[]{0}); out.writeObject(0); out.writeObject(0); out.writeObject(0); out.writeObject(0); out.writeObject(new Object[]{"1"}); out.writeObject("falsch"); });
        List<ResultBlock> blocks = new ArrayList<>();
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            var reader = new WinLaufenProtocolReader(input, ignored -> {}, blocks::add);
            assertThrows(WinLaufenProtocolReader.ProtocolException.class, reader::readNext);
        }
        assertTrue(blocks.isEmpty());
    }

    @Test void filterRejectsUndocumentedType() throws Exception {
        byte[] bytes = stream(out -> out.writeObject(new java.util.HashMap<>()));
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            input.setObjectInputFilter(WinLaufenObjectFilter.create());
            assertThrows(InvalidClassException.class, input::readObject);
        }
    }

    @Test void syntheticBiathlonContractUsesValuesDocumentedByMidstreamCaptureEvidence() throws Exception {
        var results = new ArrayList<ResultBlock>();
        byte[] bytes = blockStream(new String[]{"U15"}, new Object[][]{{"1","101","KREISSL Tommy","Verein","Vbd","1 0 2 0 ","3:30:35.1","0:00:00.0"}},
                new String[]{"Rang","StNr","Name, Vorname","Verein","Vbd","Schießen","Gesamtzeit","Rückstand"});
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            new WinLaufenProtocolReader(input, ignored -> {}, results::add).readNext();
        }
        assertEquals("Schießen", results.getFirst().headers().get(5));
        assertEquals("1 0 2 0 ", results.getFirst().rows().getFirst().get(5));
        assertEquals("KREISSL Tommy", results.getFirst().rows().getFirst().get(2));
    }

    @Test void legacyClockUsesLogicalVectorSizeAndSharesStreamWithMessagesAndResults() throws Exception {
        Vector<String> legacy = new Vector<>(10);
        legacy.add("09:33:50");
        assertEquals(1, legacy.size());
        assertEquals(10, legacy.capacity());
        byte[] bytes = stream(out -> {
            out.writeObject(legacy);
            out.writeObject(new Vector<>(List.of("Hallo", "nachricht")));
            out.writeObject("Standardwettkampf");
            out.writeObject(1);
            out.writeObject(1);
            out.writeObject(new String[]{"Klasse"});
            out.writeObject(new int[]{0});
            out.writeObject(0);
            out.writeObject(0);
            out.writeObject(0);
            out.writeObject(0);
            out.writeObject(new Object[]{"1"});
            out.writeObject("tabelle");
            out.writeObject(new String[]{"Rang"});
            out.writeObject("ende");
            out.writeObject("Uhr09:33:51");
            out.writeObject(legacy); // Reused serialization handle on the same stream.
        });
        var store = new de.winlaufen.web.bridge.state.CanonicalStateStore(
                de.winlaufen.web.contract.PresentationConfig.defaults(), null);
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            var reader = new WinLaufenProtocolReader(input,
                    clock -> store.clock(clock.wireValue()), store::result, store::message);
            reader.readNext();
            assertEquals("09:33:50", store.get().state().clock());
            assertEquals(de.winlaufen.web.contract.SourceHealth.CONNECTED,
                    store.get().state().sourceHealth());
            reader.readNext();
            reader.readNext();
            var before = store.get().state();
            reader.readNext();
            assertEquals("09:33:51", store.get().state().clock());
            reader.readNext();
            var after = store.get().state();
            assertEquals("09:33:50", after.clock());
            assertEquals("Hallo", after.message());
            assertEquals(List.of(List.of("1")),
                    after.competition().classes().getFirst().snapshot().rows());
            assertEquals(before.competition(), after.competition());
            assertEquals(before.currentFinish(), after.currentFinish());
        }
    }

    @Test void rejectsVectorsOutsideExactLegacyClockShape() throws Exception {
        List<Vector<?>> invalid = List.of(
                new Vector<>(), new Vector<>(List.of("foo")),
                new Vector<>(List.of("09:33:50", "foo")), new Vector<>(List.of(123)),
                new Vector<>(java.util.Collections.singletonList(null)),
                new Vector<>(List.of("9:33:50")), new Vector<>(List.of("09:3:50")),
                new Vector<>(List.of("09:33:5")), new Vector<>(List.of("09:33:50\n")),
                new Vector<>(List.of(" 09:33:50")), new Vector<>(List.of("09:33:50 ")),
                new Vector<>(List.of("Uhr09:33:50")), new Vector<>(List.of("AA:33:50")));
        for (Vector<?> vector : invalid) {
            byte[] bytes = stream(out -> out.writeObject(vector));
            try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
                var reader = new WinLaufenProtocolReader(input,
                        ignored -> fail("Unexpected clock"), ignored -> fail("Unexpected result"),
                        ignored -> fail("Unexpected message"));
                assertThrows(WinLaufenProtocolReader.ProtocolException.class, reader::readNext);
            }
        }
    }

    private static byte[] blockStream(String[] classes, Object[][] rows, String[] headers) throws Exception {
        return stream(out -> { out.writeObject("Standardwettkampf"); out.writeObject(1); out.writeObject(classes.length); out.writeObject(classes); out.writeObject(new int[classes.length]); out.writeObject(0); out.writeObject(0); out.writeObject(0); out.writeObject(0); for (Object[] row : rows) out.writeObject(row); out.writeObject("tabelle"); out.writeObject(headers); out.writeObject("ende"); });
    }
    private static byte[] stream(Writer writer) throws Exception {
        var bytes = new ByteArrayOutputStream(); try (var output = new ObjectOutputStream(bytes)) { writer.write(output); } return bytes.toByteArray();
    }
    @FunctionalInterface private interface Writer { void write(ObjectOutputStream output) throws Exception; }
}
