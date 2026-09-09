package de.winlaufen.web.bridge.startlist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds OOXML workbooks for the parser tests.
 *
 * <p>The default shape is the one a real WinLaufen start-list export has: the spreadsheet
 * namespace carries an {@code x:} prefix, the worksheet relationship target is absolute, text
 * cells are {@code t="str"} and the start number is a numeric cell. A workbook written by a
 * simplified example would not exercise any of that.
 */
public final class TestWorkbook {

    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String OFFICE_REL_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PACKAGE_REL_NS =
            "http://schemas.openxmlformats.org/package/2006/relationships";

    /** How the worksheet stores its text cells. All three occur in the wild. */
    public enum Cells { STR, SHARED, INLINE }

    private TestWorkbook() { }

    /** A workbook shaped like the observed real export. */
    public static byte[] realShape(List<List<String>> rows) {
        return build(rows, Cells.STR, true, "x:");
    }

    public static byte[] build(List<List<String>> rows, Cells cells, boolean absoluteTarget,
                               String prefix) {
        List<String> shared = new ArrayList<>();
        String sheet = sheet(rows, cells, prefix, shared);
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                </Types>""");
        parts.put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"" + PACKAGE_REL_NS + "\">"
                + "<Relationship Id=\"rIdBook\" Type=\"" + OFFICE_REL_NS + "/officeDocument\""
                + " Target=\"xl/workbook.xml\"/></Relationships>");
        parts.put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<" + prefix + "workbook xmlns:x=\"" + MAIN_NS + "\" xmlns=\"" + MAIN_NS + "\">"
                + "<" + prefix + "sheets><" + prefix + "sheet name=\"Tabelle1\" sheetId=\"1\""
                + " r:id=\"rIdSheet\" xmlns:r=\"" + OFFICE_REL_NS + "\"/>"
                + "</" + prefix + "sheets></" + prefix + "workbook>");
        parts.put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"" + PACKAGE_REL_NS + "\">"
                + "<Relationship Id=\"rIdSheet\" Type=\"" + OFFICE_REL_NS + "/worksheet\""
                + " Target=\"" + (absoluteTarget ? "/xl/worksheets/sheet1.xml" : "worksheets/sheet1.xml")
                + "\"/></Relationships>");
        if (cells == Cells.SHARED) {
            StringBuilder items = new StringBuilder();
            for (String value : shared) {
                items.append("<").append(prefix).append("si><").append(prefix).append("t>")
                        .append(escape(value))
                        .append("</").append(prefix).append("t></").append(prefix).append("si>");
            }
            parts.put("xl/sharedStrings.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<" + prefix + "sst xmlns:x=\"" + MAIN_NS + "\" xmlns=\"" + MAIN_NS + "\">"
                    + items + "</" + prefix + "sst>");
        }
        parts.put("xl/worksheets/sheet1.xml", sheet);
        return zip(parts);
    }

    private static String sheet(List<List<String>> rows, Cells cells, String prefix,
                                List<String> shared) {
        StringBuilder body = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            body.append("<").append(prefix).append("row r=\"").append(rowIndex + 1).append("\">");
            List<String> row = rows.get(rowIndex);
            for (int column = 0; column < row.size(); column++) {
                String value = row.get(column);
                if (value.isEmpty()) {
                    continue;
                }
                String reference = columnName(column) + (rowIndex + 1);
                body.append(cell(prefix, reference, value, cells, shared));
            }
            body.append("</").append(prefix).append("row>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<" + prefix + "worksheet xmlns:x=\"" + MAIN_NS + "\" xmlns=\"" + MAIN_NS + "\">"
                + "<" + prefix + "sheetData>" + body + "</" + prefix + "sheetData>"
                + "</" + prefix + "worksheet>";
    }

    private static String cell(String prefix, String reference, String value, Cells cells,
                               List<String> shared) {
        String open = "<" + prefix + "c r=\"" + reference + "\"";
        if (isNumeric(value)) {
            return open + " t=\"n\"><" + prefix + "v>" + escape(value) + "</" + prefix + "v>"
                    + "</" + prefix + "c>";
        }
        return switch (cells) {
            case STR -> open + " t=\"str\"><" + prefix + "v>" + escape(value) + "</" + prefix + "v>"
                    + "</" + prefix + "c>";
            case SHARED -> {
                int index = shared.indexOf(value);
                if (index < 0) {
                    index = shared.size();
                    shared.add(value);
                }
                yield open + " t=\"s\"><" + prefix + "v>" + index + "</" + prefix + "v>"
                        + "</" + prefix + "c>";
            }
            case INLINE -> open + " t=\"inlineStr\"><" + prefix + "is><" + prefix + "t>"
                    + escape(value) + "</" + prefix + "t></" + prefix + "is></" + prefix + "c>";
        };
    }

    /** Mirrors the export: the start number and the course length are numeric cells. */
    private static boolean isNumeric(String value) {
        return value.matches("\\d+(\\.\\d+)?");
    }

    private static String columnName(int column) {
        StringBuilder name = new StringBuilder();
        int remaining = column;
        do {
            name.insert(0, (char) ('A' + remaining % 26));
            remaining = remaining / 26 - 1;
        } while (remaining >= 0);
        return name.toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static byte[] zip(Map<String, String> parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> part : parts.entrySet()) {
                zip.putNextEntry(new ZipEntry(part.getKey()));
                zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return output.toByteArray();
    }
}
