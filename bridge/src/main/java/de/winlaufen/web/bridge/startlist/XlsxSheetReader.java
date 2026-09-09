package de.winlaufen.web.bridge.startlist;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Reads the first worksheet of an OOXML workbook as a plain matrix of cell texts.
 *
 * <p>This class knows nothing about start lists. It exists because the bridge must read a real
 * {@code .xlsx} export without adding a spreadsheet framework: an OOXML workbook is a ZIP of XML
 * parts, and {@code java.util.zip} plus StAX from {@code java.base}/{@code java.xml} are enough.
 * Both modules are already part of the reduced {@code jlink} runtime.
 *
 * <p>The reader follows what real exports actually look like rather than a simplified example:
 * the main namespace may be prefixed, the worksheet relationship target may be absolute, and text
 * cells appear as {@code t="str"}, as shared strings or as inline strings. Numbers keep the text
 * the file contains — a bib is never converted into a number here.
 */
final class XlsxSheetReader {

    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String OFFICE_REL_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PACKAGE_REL_NS =
            "http://schemas.openxmlformats.org/package/2006/relationships";

    private static final String WORKBOOK_PART = "xl/workbook.xml";
    private static final String WORKBOOK_RELS_PART = "xl/_rels/workbook.xml.rels";
    private static final String SHARED_STRINGS_PART = "xl/sharedStrings.xml";

    /** Upper bound on everything unpacked from one workbook, so a zip bomb cannot exhaust memory. */
    private static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024;

    /** Upper bound on the rows of one worksheet, including the header row. */
    private static final int MAX_ROWS = CanonicalStartList.MAX_ENTRIES + 1_000;

    private XlsxSheetReader() { }

    /**
     * @return the cells of the first worksheet, row by row; trailing empty cells of a row are not
     *         padded, so a row can be shorter than the header row.
     */
    static List<List<String>> readFirstSheet(byte[] data) {
        Map<String, byte[]> parts = unpack(data);
        String relationshipId = firstSheetRelationshipId(part(parts, WORKBOOK_PART));
        String target = relationshipTarget(part(parts, WORKBOOK_RELS_PART), relationshipId);
        String worksheetPart = resolveWorksheetPart(target);
        byte[] worksheet = parts.get(worksheetPart);
        if (worksheet == null) {
            throw new StartListFormatException("XLSX: Arbeitsblatt " + worksheetPart + " fehlt");
        }
        List<String> sharedStrings = parts.containsKey(SHARED_STRINGS_PART)
                ? sharedStrings(parts.get(SHARED_STRINGS_PART))
                : List.of();
        return rows(worksheet, sharedStrings);
    }

    /**
     * Reads every part into memory. The declared entry size of a streamed ZIP is often unknown, so
     * the budget is enforced on the bytes actually read: one byte more than the remaining budget is
     * requested, and receiving it proves the workbook exceeds the limit.
     */
    private static Map<String, byte[]> unpack(byte[] data) {
        Map<String, byte[]> parts = new HashMap<>();
        long remaining = MAX_UNCOMPRESSED_BYTES;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] content = zip.readNBytes((int) Math.min(remaining + 1, Integer.MAX_VALUE));
                if (content.length > remaining) {
                    throw new StartListFormatException("XLSX ist zu groß");
                }
                remaining -= content.length;
                parts.put(entry.getName(), content);
            }
        } catch (ZipException ex) {
            throw new StartListFormatException("Datei ist kein lesbares XLSX", ex);
        } catch (IOException ex) {
            throw new StartListFormatException("XLSX konnte nicht gelesen werden", ex);
        }
        if (parts.isEmpty()) {
            throw new StartListFormatException("Datei ist kein lesbares XLSX");
        }
        return parts;
    }

    private static byte[] part(Map<String, byte[]> parts, String name) {
        byte[] content = parts.get(name);
        if (content == null) {
            throw new StartListFormatException("XLSX: " + name + " fehlt");
        }
        return content;
    }

    private static String firstSheetRelationshipId(byte[] workbook) {
        try (Xml stream = Xml.over(workbook)) {
            while (stream.reader.hasNext()) {
                if (stream.reader.next() == XMLStreamConstants.START_ELEMENT
                        && isMain(stream.reader, "sheet")) {
                    String id = stream.reader.getAttributeValue(OFFICE_REL_NS, "id");
                    if (id == null || id.isBlank()) {
                        throw new StartListFormatException("XLSX: Arbeitsblatt ohne Verknüpfung");
                    }
                    return id;
                }
            }
        } catch (XMLStreamException ex) {
            throw new StartListFormatException("XLSX: workbook.xml ist ungültig", ex);
        }
        throw new StartListFormatException("XLSX enthält kein Arbeitsblatt");
    }

    private static String relationshipTarget(byte[] relationships, String id) {
        try (Xml stream = Xml.over(relationships)) {
            while (stream.reader.hasNext()) {
                if (stream.reader.next() == XMLStreamConstants.START_ELEMENT
                        && is(stream.reader, PACKAGE_REL_NS, "Relationship")
                        && id.equals(stream.reader.getAttributeValue(null, "Id"))) {
                    String target = stream.reader.getAttributeValue(null, "Target");
                    if (target == null || target.isBlank()) {
                        throw new StartListFormatException("XLSX: Verknüpfung ohne Ziel");
                    }
                    return target;
                }
            }
        } catch (XMLStreamException ex) {
            throw new StartListFormatException("XLSX: workbook.xml.rels ist ungültig", ex);
        }
        throw new StartListFormatException("XLSX: Verknüpfung " + id + " fehlt");
    }

    /** Observed real exports use an absolute target; the specification also allows a relative one. */
    private static String resolveWorksheetPart(String target) {
        if (target.startsWith("/")) {
            return target.substring(1);
        }
        List<String> segments = new ArrayList<>(List.of("xl"));
        for (String segment : target.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
                continue;
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    private static List<String> sharedStrings(byte[] content) {
        List<String> strings = new ArrayList<>();
        StringBuilder current = null;
        try (Xml stream = Xml.over(content)) {
            while (stream.reader.hasNext()) {
                int event = stream.reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && isMain(stream.reader, "si")) {
                    current = new StringBuilder();
                } else if (event == XMLStreamConstants.START_ELEMENT
                        && isMain(stream.reader, "t") && current != null) {
                    current.append(stream.reader.getElementText());
                } else if (event == XMLStreamConstants.END_ELEMENT && isMain(stream.reader, "si")
                        && current != null) {
                    strings.add(current.toString());
                    current = null;
                }
            }
        } catch (XMLStreamException ex) {
            throw new StartListFormatException("XLSX: sharedStrings.xml ist ungültig", ex);
        }
        return strings;
    }

    private static List<List<String>> rows(byte[] worksheet, List<String> sharedStrings) {
        List<List<String>> rows = new ArrayList<>();
        try (Xml stream = Xml.over(worksheet)) {
            XMLStreamReader reader = stream.reader;
            List<String> row = null;
            int column = -1;
            String type = null;
            StringBuilder value = null;
            boolean insideValue = false;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && isMain(reader, "row")) {
                    row = new ArrayList<>();
                    column = -1;
                } else if (event == XMLStreamConstants.START_ELEMENT && isMain(reader, "c")
                        && row != null) {
                    column = columnIndex(reader.getAttributeValue(null, "r"), column);
                    type = reader.getAttributeValue(null, "t");
                    value = new StringBuilder();
                } else if (event == XMLStreamConstants.START_ELEMENT && value != null
                        && (isMain(reader, "v") || isMain(reader, "t"))) {
                    insideValue = true;
                } else if (event == XMLStreamConstants.CHARACTERS && insideValue) {
                    value.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT && value != null
                        && (isMain(reader, "v") || isMain(reader, "t"))) {
                    insideValue = false;
                } else if (event == XMLStreamConstants.END_ELEMENT && isMain(reader, "c")
                        && row != null && value != null) {
                    while (row.size() < column) {
                        row.add("");
                    }
                    row.add(cellText(type, value.toString(), sharedStrings));
                    value = null;
                    type = null;
                } else if (event == XMLStreamConstants.END_ELEMENT && isMain(reader, "row")
                        && row != null) {
                    rows.add(List.copyOf(row));
                    if (rows.size() > MAX_ROWS) {
                        throw new StartListFormatException("XLSX hat mehr als " + MAX_ROWS
                                + " Zeilen");
                    }
                    row = null;
                }
            }
        } catch (XMLStreamException ex) {
            throw new StartListFormatException("XLSX: Arbeitsblatt ist ungültig", ex);
        }
        return rows;
    }

    /**
     * Shared strings are resolved; every other type keeps the literal text of the file. A numeric
     * cell therefore stays exactly the digits the export wrote, which is what a bib needs.
     */
    private static String cellText(String type, String raw, List<String> sharedStrings) {
        if (!"s".equals(type)) {
            return raw;
        }
        try {
            return sharedStrings.get(Integer.parseInt(raw.strip()));
        } catch (NumberFormatException | IndexOutOfBoundsException ex) {
            throw new StartListFormatException("XLSX: ungültiger Textverweis " + raw, ex);
        }
    }

    /** {@code "C7"} is column index 2. A cell without a reference follows the previous one. */
    private static int columnIndex(String reference, int previous) {
        if (reference == null || reference.isEmpty()) {
            return previous + 1;
        }
        int index = 0;
        int digits = 0;
        for (int position = 0; position < reference.length(); position++) {
            char character = reference.charAt(position);
            if (character >= 'A' && character <= 'Z') {
                if (digits > 0) {
                    throw new StartListFormatException("XLSX: ungültiger Zellbezug " + reference);
                }
                index = index * 26 + character - 'A' + 1;
            } else if (character >= '0' && character <= '9') {
                digits++;
            } else {
                throw new StartListFormatException("XLSX: ungültiger Zellbezug " + reference);
            }
        }
        if (index == 0 || digits == 0) {
            throw new StartListFormatException("XLSX: ungültiger Zellbezug " + reference);
        }
        return index - 1;
    }

    private static boolean isMain(XMLStreamReader reader, String localName) {
        return is(reader, MAIN_NS, localName);
    }

    private static boolean is(XMLStreamReader reader, String namespace, String localName) {
        return localName.equals(reader.getLocalName()) && namespace.equals(reader.getNamespaceURI());
    }

    /** A closable StAX reader with external entities and DTDs switched off. */
    private record Xml(XMLStreamReader reader) implements AutoCloseable {

        static Xml over(byte[] content) {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.IS_COALESCING, true);
            try {
                return new Xml(factory.createXMLStreamReader(new ByteArrayInputStream(content)));
            } catch (XMLStreamException ex) {
                throw new StartListFormatException("XLSX enthält ungültiges XML", ex);
            }
        }

        @Override
        public void close() throws XMLStreamException {
            reader.close();
        }
    }
}
