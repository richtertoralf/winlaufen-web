package de.winlaufen.web.bridge.startlist;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartListParserTest {

    /** The header of a real export: comma separated and ending on a trailing separator. */
    private static final String HEADER =
            "Name,Vorname,Verein,Verband,StNr,Klasse,Strecke,Startzeit,Jahrgang,Geschlecht,Nation,";

    private static byte[] windows1252(String text) {
        return text.getBytes(Charset.forName("windows-1252"));
    }

    private static StartListImport parseCsv(String text) {
        return StartListParser.parse(text.getBytes(StandardCharsets.UTF_8), "startliste.csv");
    }

    // --- CSV shape -----------------------------------------------------------------------------

    @Test
    void readsCommaSeparatedCsv() {
        StartListImport parsed = parseCsv(HEADER + "\r\n"
                + "MÜLLER,Anna,Verein Eins,SVSAC,7,U16,5.0,10:00:15,2010,w,GER,\r\n");

        assertEquals(StartListSource.IMPORT_CSV, parsed.source());
        assertEquals("startliste.csv", parsed.sourceLabel());
        assertEquals(1, parsed.entries().size());
        StartListEntry entry = parsed.entries().getFirst();
        assertEquals("7", entry.bib());
        assertEquals("U16", entry.className());
        assertEquals("10:00:15", entry.startTime());
        assertEquals("MÜLLER", entry.lastName());
        assertEquals("Anna", entry.firstName());
        assertEquals("Verein Eins", entry.club());
        assertEquals("SVSAC", entry.association());
        assertEquals("5.0", entry.course());
        assertEquals("2010", entry.birthYear());
        assertEquals("w", entry.gender());
        assertEquals("GER", entry.nation());
    }

    @Test
    void readsSemicolonSeparatedCsv() {
        StartListImport parsed = parseCsv(HEADER.replace(',', ';') + "\r\n"
                + "MEIER;Ben;Verein Zwei;BSV;8;U18;7.5;11:02:03;2008;m;AUT;\r\n");

        assertEquals(1, parsed.entries().size());
        assertEquals("8", parsed.entries().getFirst().bib());
        assertEquals("U18", parsed.entries().getFirst().className());
        assertEquals("MEIER", parsed.entries().getFirst().lastName());
    }

    @Test
    void acceptsAUtf8ByteOrderMark() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = (HEADER + "\r\nMÜLLER,Anna,V,LV,7,U16,5,10:00:15,2010,w,GER,\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[bom.length + text.length];
        System.arraycopy(bom, 0, data, 0, bom.length);
        System.arraycopy(text, 0, data, bom.length, text.length);

        StartListImport parsed = StartListParser.parse(data, "startliste.csv");

        assertEquals("MÜLLER", parsed.entries().getFirst().lastName());
        assertEquals("7", parsed.entries().getFirst().bib());
    }

    @Test
    void decodesWindows1252Umlauts() {
        byte[] data = windows1252(HEADER + "\r\n"
                + "BÖRNER,Alwin,Blau Weiß Zwenkau,SBW,5,Schüler U12 m,0.8,10:01:00,2015,m,GER,\r\n");

        StartListImport parsed = StartListParser.parse(data, "startliste.csv");

        assertEquals("BÖRNER", parsed.entries().getFirst().lastName());
        assertEquals("Blau Weiß Zwenkau", parsed.entries().getFirst().club());
        assertEquals("Schüler U12 m", parsed.entries().getFirst().className());
    }

    @Test
    void crlfAndLfProduceTheSameEntries() {
        String rows = HEADER + "\n"
                + "MÜLLER,Anna,V,LV,7,U16,5,10:00:15,2010,w,GER,\n"
                + "MEIER,Ben,V,LV,8,U16,5,10:00:30,2010,m,GER,\n";

        assertEquals(parseCsv(rows.replace("\n", "\r\n")).entries(), parseCsv(rows).entries());
        assertEquals(2, parseCsv(rows).entries().size());
    }

    @Test
    void readsAQuotedFieldContainingTheDelimiter() {
        StartListImport parsed = parseCsv(HEADER + "\r\n"
                + "MÜLLER,Anna,\"SC Test, Abteilung Ski\",LV,7,U16,5,10:00:15,2010,w,GER,\r\n");

        assertEquals("SC Test, Abteilung Ski", parsed.entries().getFirst().club());
    }

    @Test
    void toleratesARowThatEndsEarly() {
        StartListImport parsed = parseCsv(HEADER + "\r\nMÜLLER,Anna,V,LV,7,U16\r\n");

        assertEquals("7", parsed.entries().getFirst().bib());
        assertEquals("", parsed.entries().getFirst().startTime());
        assertFalse(parsed.entries().getFirst().hasStartTime());
    }

    @Test
    void rejectsAValueInAColumnWithoutAHeading() {
        StartListFormatException ex = assertThrows(StartListFormatException.class,
                () -> parseCsv(HEADER + "\r\nMÜLLER,Anna,V,LV,7,U16,5,10:00:15,2010,w,GER,extra\r\n"));

        assertTrue(ex.getMessage().contains("Zeile 2"), ex.getMessage());
    }

    @Test
    void rejectsAnUndeterminableDelimiter() {
        assertThrows(StartListFormatException.class,
                () -> parseCsv("Foo|Bar|Baz\r\n1|U16|10:00:00\r\n"));
    }

    @Test
    void rejectsAnUnknownColumn() {
        StartListFormatException ex = assertThrows(StartListFormatException.class,
                () -> parseCsv("StNr,Klasse,Lieblingsfarbe\r\n7,U16,blau\r\n"));

        assertTrue(ex.getMessage().contains("Lieblingsfarbe"), ex.getMessage());
    }

    @Test
    void rejectsAMissingRequiredColumn() {
        StartListFormatException ex = assertThrows(StartListFormatException.class,
                () -> parseCsv("StNr,Startzeit\r\n7,10:00:15\r\n"));

        assertTrue(ex.getMessage().contains("Klasse"), ex.getMessage());
    }

    @Test
    void acceptsAStartListWithoutStartTimes() {
        StartListImport parsed = parseCsv("StNr,Klasse\r\n7,U16\r\n8,U16\r\n");

        assertEquals(2, parsed.entries().size());
        assertFalse(parsed.entries().getFirst().hasStartTime());
    }

    @Test
    void rejectsAFileWithoutParticipants() {
        assertThrows(StartListFormatException.class, () -> parseCsv(HEADER + "\r\n"));
    }

    @Test
    void rejectsAnInvalidStartTime() {
        StartListFormatException ex = assertThrows(StartListFormatException.class,
                () -> parseCsv(HEADER + "\r\nM,A,V,LV,7,U16,5,25:00:00,2010,w,GER,\r\n"));

        assertTrue(ex.getMessage().contains("Zeile 2"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Startzeit"), ex.getMessage());
    }

    @Test
    void rejectsAnEmptyBibOrClass() {
        assertThrows(StartListFormatException.class,
                () -> parseCsv(HEADER + "\r\nM,A,V,LV,,U16,5,10:00:15,2010,w,GER,\r\n"));
        assertThrows(StartListFormatException.class,
                () -> parseCsv(HEADER + "\r\nM,A,V,LV,7,,5,10:00:15,2010,w,GER,\r\n"));
    }

    // --- Start numbers stay text ---------------------------------------------------------------

    @Test
    void bibKeepsItsLeadingZeros() {
        StartListImport parsed = parseCsv("StNr,Klasse\r\n0012,U16\r\n12,U18\r\nA12,U20\r\n");

        assertEquals(List.of("0012", "12", "A12"),
                parsed.entries().stream().map(StartListEntry::bib).toList());
    }

    @Test
    void sameBibInTwoClassesIsAllowed() {
        StartListImport parsed = parseCsv("StNr,Klasse\r\n12,U16\r\n12,U18\r\n");

        assertEquals(2, parsed.entries().size());
        assertEquals(List.of("U16", "U18"),
                parsed.entries().stream().map(StartListEntry::className).toList());
    }

    @Test
    void duplicateBibInTheSameClassIsRejectedByTheCanonicalList() {
        StartListImport parsed = parseCsv("StNr,Klasse\r\n12,U16\r\n12,U16\r\n");

        StartListFormatException ex = assertThrows(StartListFormatException.class,
                () -> new CanonicalStartList(1, parsed.source(), parsed.sourceLabel(),
                        parsed.entries()));

        assertTrue(ex.getMessage().contains("12"), ex.getMessage());
        assertTrue(ex.getMessage().contains("U16"), ex.getMessage());
    }

    // --- Start time semantics ------------------------------------------------------------------

    @Test
    void startTimeStaysTheDeliveredTimeOfDay() {
        StartListImport parsed = parseCsv("StNr,Klasse,Startzeit\r\n7,U16,10:00:15\r\n"
                + "8,U16,23:59:59\r\n9,U16,10:00\r\n10,U16,10:00:15.7\r\n");

        assertEquals(List.of("10:00:15", "23:59:59", "10:00", "10:00:15.7"),
                parsed.entries().stream().map(StartListEntry::startTime).toList());
    }

    @Test
    void startTimeIsNeverModelledAsAnInstantOrADate() {
        for (RecordComponent component : StartListEntry.class.getRecordComponents()) {
            assertEquals(String.class, component.getType(),
                    "a start list entry carries only source text, but " + component.getName()
                            + " is " + component.getType()
                            + "; a start time is a WinLaufen time of day, not a UTC instant");
        }
    }

    @Test
    void rejectsAnHourBeyondTheDay() {
        assertFalse(StartListEntry.isValidStartTime("24:00:00"));
        assertFalse(StartListEntry.isValidStartTime("10:60:00"));
        assertFalse(StartListEntry.isValidStartTime("2026-09-09T10:00:15Z"));
        assertTrue(StartListEntry.isValidStartTime("0:00"));
        assertTrue(StartListEntry.isValidStartTime("23:59:59,9"));
    }

    // --- No invented identity ------------------------------------------------------------------

    @Test
    void noParticipantIdentifierIsDerived() {
        for (RecordComponent component : StartListEntry.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.equals("id") || name.endsWith("id") || name.contains("uuid")
                            || name.contains("index") || name.contains("row"),
                    "the source supplies no participant identifier, so none may be modelled: "
                            + component.getName());
        }
    }

    @Test
    void theSameParticipantIsEqualRegardlessOfItsRowPosition() {
        String first = "StNr,Klasse,Startzeit\r\n7,U16,10:00:15\r\n8,U16,10:00:30\r\n";
        String second = "StNr,Klasse,Startzeit\r\n8,U16,10:00:30\r\n7,U16,10:00:15\r\n";

        assertEquals(parseCsv(first).entries().getFirst(), parseCsv(second).entries().getLast());
    }

    @Test
    void reorderedRowsProduceTheSameEntryData() {
        String first = HEADER + "\r\n"
                + "MÜLLER,Anna,V,LV,7,U16,5,10:00:15,2010,w,GER,\r\n"
                + "MEIER,Ben,V,LV,8,U16,5,10:00:30,2008,m,AUT,\r\n";
        String second = HEADER + "\r\n"
                + "MEIER,Ben,V,LV,8,U16,5,10:00:30,2008,m,AUT,\r\n"
                + "MÜLLER,Anna,V,LV,7,U16,5,10:00:15,2010,w,GER,\r\n";

        assertEquals(Set.copyOf(parseCsv(first).entries()), Set.copyOf(parseCsv(second).entries()));
    }

    // --- XLSX ----------------------------------------------------------------------------------

    private static final List<List<String>> SHEET = List.of(
            List.of("Name", "Vorname", "Verein", "Verband", "StNr", "Klasse", "Strecke",
                    "Startzeit", "Jahrgang", "Geschlecht", "Nation"),
            List.of("BÖRNER", "Alwin", "Blau Weiß Zwenkau", "SBW", "5", "Schüler U12 m", "0.8",
                    "10:01:00", "2015", "M", "GER"));

    @Test
    void readsAWorkbookInTheShapeOfARealExport() {
        StartListImport parsed = StartListParser.parse(TestWorkbook.realShape(SHEET),
                "01_Einzelstart_15s.xlsx");

        assertEquals(StartListSource.IMPORT_XLSX, parsed.source());
        assertEquals("01_Einzelstart_15s.xlsx", parsed.sourceLabel());
        StartListEntry entry = parsed.entries().getFirst();
        assertEquals("5", entry.bib());
        assertEquals("Schüler U12 m", entry.className());
        assertEquals("10:01:00", entry.startTime());
        assertEquals("Blau Weiß Zwenkau", entry.club());
        assertEquals("0.8", entry.course());
    }

    @Test
    void readsSharedAndInlineStringCells() {
        for (TestWorkbook.Cells cells : TestWorkbook.Cells.values()) {
            byte[] workbook = TestWorkbook.build(SHEET, cells, true, "x:");

            StartListEntry entry = StartListParser.parse(workbook, "s.xlsx").entries().getFirst();

            assertEquals("Blau Weiß Zwenkau", entry.club(), "cell style " + cells);
            assertEquals("5", entry.bib(), "cell style " + cells);
        }
    }

    @Test
    void readsAWorkbookWithoutANamespacePrefixAndARelativeTarget() {
        byte[] workbook = TestWorkbook.build(SHEET, TestWorkbook.Cells.STR, false, "");

        assertEquals("5", StartListParser.parse(workbook, "s.xlsx").entries().getFirst().bib());
    }

    @Test
    void xlsxAndCsvOfTheSameDataAgreeOnEveryField() {
        String csv = "Name,Vorname,Verein,Verband,StNr,Klasse,Strecke,Startzeit,Jahrgang,"
                + "Geschlecht,Nation\r\nBÖRNER,Alwin,Blau Weiß Zwenkau,SBW,5,Schüler U12 m,0.8,"
                + "10:01:00,2015,M,GER\r\n";

        assertEquals(parseCsv(csv).entries(),
                StartListParser.parse(TestWorkbook.realShape(SHEET), "s.xlsx").entries());
    }

    @Test
    void rejectsLegacyXls() {
        StartListFormatException ex = assertThrows(StartListFormatException.class,
                () -> StartListParser.parse(new byte[] {1, 2, 3}, "startliste.xls"));

        assertTrue(ex.getMessage().contains(".xls"), ex.getMessage());
    }

    @Test
    void rejectsAnUnsupportedExtensionAndAMissingName() {
        assertThrows(StartListFormatException.class,
                () -> StartListParser.parse(new byte[] {1}, "startliste.pdf"));
        assertThrows(StartListFormatException.class,
                () -> StartListParser.parse(new byte[] {1}, ""));
    }

    @Test
    void rejectsAnXlsxThatIsNotAZip() {
        assertThrows(StartListFormatException.class,
                () -> StartListParser.parse("kein Zip".getBytes(StandardCharsets.UTF_8), "s.xlsx"));
    }

    @Test
    void rejectsEmptyAndOversizedInput() {
        assertThrows(StartListFormatException.class,
                () -> StartListParser.parse(new byte[0], "s.csv"));
        byte[] huge = new byte[StartListParser.MAX_INPUT_BYTES + 1];
        Arrays.fill(huge, (byte) 'a');
        assertThrows(StartListFormatException.class, () -> StartListParser.parse(huge, "s.csv"));
    }
}
