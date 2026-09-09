package de.winlaufen.web.bridge.startlist;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a WinLaufen start-list export into a validated {@link StartListImport}.
 *
 * <p>The parser is an input adapter, nothing more. It never touches the stored start list: either
 * it returns a complete, fully validated import, or it throws and the bridge keeps exactly the
 * start list it had. There is no partial adoption.
 *
 * <p>It also stays strictly out of competition semantics. It does not ask whether this is the same
 * competition as before, whether a participant is the same person, whether a class was merely
 * corrected or whether a new bib means a new entry. None of that is decidable from an export, and
 * none of it is this component's job.
 *
 * <p>Supported input, matching the observed real exports: CSV with a comma or a semicolon, UTF-8
 * with or without BOM, Windows-1252, CRLF or LF line endings, and OOXML {@code .xlsx}. The old
 * binary {@code .xls} is rejected with a clear message instead of being misread.
 */
public final class StartListParser {

    /** The known WinLaufen export columns. An unknown column is a rejected file, not a guess. */
    static final List<String> KNOWN_COLUMNS = List.of(
            "Name", "Vorname", "Verein", "Verband", "StNr", "Klasse", "Strecke",
            "Startzeit", "Jahrgang", "Geschlecht", "Nation");

    /**
     * Without these two columns an entry cannot be addressed at all. {@code Startzeit} is
     * deliberately not required: a start list without start times is still a useful participant
     * list, it merely carries no start time.
     */
    static final List<String> REQUIRED_COLUMNS = List.of("StNr", "Klasse");

    /** Defensive bound on the accepted upload size. The largest observed real export is ~260 KB. */
    public static final int MAX_INPUT_BYTES = 16 * 1024 * 1024;

    private static final char[] DELIMITERS = {',', ';'};
    private static final char BOM = '\uFEFF';
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private StartListParser() { }

    /**
     * @param data     the raw file content
     * @param fileName the original file name; its extension selects the format
     * @throws StartListFormatException if the input is not a usable start list
     */
    public static StartListImport parse(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            throw new StartListFormatException("Startlistendatei ist leer");
        }
        if (data.length > MAX_INPUT_BYTES) {
            throw new StartListFormatException("Startlistendatei ist größer als "
                    + MAX_INPUT_BYTES + " Bytes");
        }
        String label = label(fileName);
        return switch (extension(label)) {
            case "csv", "txt" -> new StartListImport(StartListSource.IMPORT_CSV, label,
                    entries(csvRows(decode(data))));
            case "xlsx" -> new StartListImport(StartListSource.IMPORT_XLSX, label,
                    entries(XlsxSheetReader.readFirstSheet(data)));
            case "xls" -> throw new StartListFormatException(
                    "Das alte Format .xls wird nicht unterstützt; bitte als CSV oder XLSX "
                            + "exportieren");
            default -> throw new StartListFormatException(
                    "Nicht unterstütztes Startlistenformat: " + label);
        };
    }

    // --- Encoding ------------------------------------------------------------------------------

    /**
     * A UTF-8 BOM is removed, then UTF-8 is tried strictly; only a byte sequence that is not valid
     * UTF-8 is read as Windows-1252. That order matters because the observed real CSV exports are
     * Windows-1252 while hand-made files are usually UTF-8, and a wrong guess would corrupt umlauts
     * in club and class names.
     */
    private static String decode(byte[] data) {
        byte[] content = data;
        if (content.length >= UTF8_BOM.length
                && content[0] == UTF8_BOM[0] && content[1] == UTF8_BOM[1]
                && content[2] == UTF8_BOM[2]) {
            content = Arrays.copyOfRange(content, UTF8_BOM.length, content.length);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(content, Charset.forName("windows-1252"));
        }
    }

    // --- CSV -----------------------------------------------------------------------------------

    /**
     * Picks the delimiter by how many known column names the first row yields. Sniffing characters
     * alone would misread a semicolon file whose club names contain commas, and the header of a
     * WinLaufen export is a reliable fingerprint.
     */
    private static List<List<String>> csvRows(String text) {
        List<List<String>> best = null;
        int bestScore = -1;
        boolean ambiguous = false;
        for (char delimiter : DELIMITERS) {
            List<List<String>> rows = tokenize(text, delimiter);
            int score = rows.isEmpty() ? 0 : knownColumns(rows.getFirst());
            if (score > bestScore) {
                bestScore = score;
                best = rows;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (bestScore < 2 || ambiguous) {
            throw new StartListFormatException(
                    "Trennzeichen der CSV-Datei ist aus der Kopfzeile nicht bestimmbar");
        }
        return best;
    }

    private static int knownColumns(List<String> header) {
        Set<String> found = new HashSet<>();
        for (String cell : header) {
            String name = normalize(cell);
            if (KNOWN_COLUMNS.contains(name)) {
                found.add(name);
            }
        }
        return found.size();
    }

    /**
     * Splits the text into rows of fields following RFC 4180: a field may be quoted, a doubled
     * quote inside a quoted field is a literal quote, and a quoted field may contain the delimiter
     * and line breaks. CRLF, LF and a lone CR all end a row.
     */
    private static List<List<String>> tokenize(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (quoted) {
                if (character != '"') {
                    field.append(character);
                } else if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = false;
                }
            } else if (character == '"' && field.isEmpty()) {
                quoted = true;
            } else if (character == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (character == '\n' || character == '\r') {
                if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(character);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    // --- Rows to entries -----------------------------------------------------------------------

    private static List<StartListEntry> entries(List<List<String>> rows) {
        if (rows.isEmpty()) {
            throw new StartListFormatException("Startlistendatei ist leer");
        }
        Map<String, Integer> columns = header(rows.getFirst());
        Set<Integer> named = new HashSet<>(columns.values());

        List<StartListEntry> entries = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            int line = index + 1;
            if (row.stream().allMatch(cell -> cell == null || cell.isBlank())) {
                continue;
            }
            for (int column = 0; column < row.size(); column++) {
                if (!named.contains(column) && !row.get(column).isBlank()) {
                    throw new StartListFormatException(
                            "Zeile " + line + " hat einen Wert in einer Spalte ohne Überschrift");
                }
            }
            try {
                entries.add(entry(columns, row));
            } catch (StartListFormatException ex) {
                throw new StartListFormatException("Zeile " + line + ": " + ex.getMessage(), ex);
            }
        }
        if (entries.isEmpty()) {
            throw new StartListFormatException("Startliste enthält keine Teilnehmer");
        }
        return entries;
    }

    /**
     * Validates the header row and maps every named column to its position.
     *
     * <p>Empty header cells are tolerated and their columns must stay empty: the real WinLaufen CSV
     * export ends every line with a trailing separator, so the header carries one empty column.
     */
    private static Map<String, Integer> header(List<String> cells) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int index = 0; index < cells.size(); index++) {
            String name = normalize(cells.get(index));
            if (name.isEmpty()) {
                continue;
            }
            if (!KNOWN_COLUMNS.contains(name)) {
                throw new StartListFormatException("Unbekannte Spalte: " + name);
            }
            if (columns.putIfAbsent(name, index) != null) {
                throw new StartListFormatException("Spalte kommt mehrfach vor: " + name);
            }
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!columns.containsKey(required)) {
                throw new StartListFormatException("Pflichtspalte fehlt: " + required);
            }
        }
        return columns;
    }

    private static StartListEntry entry(Map<String, Integer> columns, List<String> row) {
        return new StartListEntry(
                cell(columns, row, "StNr"),
                cell(columns, row, "Klasse"),
                cell(columns, row, "Startzeit"),
                cell(columns, row, "Name"),
                cell(columns, row, "Vorname"),
                cell(columns, row, "Verein"),
                cell(columns, row, "Verband"),
                cell(columns, row, "Strecke"),
                cell(columns, row, "Jahrgang"),
                cell(columns, row, "Geschlecht"),
                cell(columns, row, "Nation"));
    }

    /** A row may be shorter than the header; the missing trailing columns are simply empty. */
    private static String cell(Map<String, Integer> columns, List<String> row, String name) {
        Integer index = columns.get(name);
        return index == null || index >= row.size() ? "" : row.get(index);
    }

    // --- File name -----------------------------------------------------------------------------

    private static String label(String fileName) {
        String name = fileName == null ? "" : fileName.strip();
        if (name.isEmpty()) {
            throw new StartListFormatException("Dateiname fehlt");
        }
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(separator + 1);
        if (name.isEmpty() || name.length() > CanonicalStartList.MAX_SOURCE_LABEL_CHARS) {
            throw new StartListFormatException("Ungültiger Dateiname");
        }
        return name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String text = value;
        while (!text.isEmpty() && text.charAt(0) == BOM) {
            text = text.substring(1);
        }
        return text.strip();
    }
}
