package de.winlaufen.web.bridge.source.winlaufen;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

/** Synthetic serialization of the object structure reported from the 2026-09-13 capture. */
final class LegacyProtocolFixture {
    static final List<String> ROW = List.of("1", "68", "MÜLLER Rebecca-Anna", "SSV-Geyer",
            "SVSAC", "0:14:23.5", "0:00:00.0");

    private LegacyProtocolFixture() { }

    static Vector<Object> result() {
        String[] classes = new String[21];
        // The report does not supply every class name or their full order.
        Arrays.fill(classes, "Klasse (synthetisch)");
        classes[0] = "Schüler U7 m";
        classes[1] = "Schüler U8 m";
        classes[2] = "Schüler U8 w";
        return new Vector<>(List.of("Standardwettkampf", 2, 21, classes, new int[21],
                0, 10, 0, 0,
                new Object[][]{{1, 68, "MÜLLER Rebecca-Anna", "SSV-Geyer", "SVSAC",
                        "0:14:23.5", "0:00:00.0"}},
                new String[]{"Rang", "StNr", "Name, Vorname", "Verein", "Vbd", "Laufzeit", "Rückstand"}));
    }

    static void writeCurrentEquivalent(ObjectOutputStream output) throws IOException {
        Vector<Object> legacy = result();
        for (int index = 0; index < 9; index++) output.writeObject(legacy.get(index));
        output.writeObject(ROW.toArray());
        output.writeObject("tabelle");
        output.writeObject(legacy.get(10));
        output.writeObject("ende");
    }
}
