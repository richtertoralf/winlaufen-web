package de.winlaufen.web.bridge.source.winlaufen;

import de.winlaufen.web.contract.ContractLimits;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.function.Consumer;

public final class WinLaufenProtocolReader {
    private final ObjectInputStream input;
    private final Consumer<ClockValue> clocks;
    private final Consumer<ResultBlock> results;
    private final Consumer<String> messages;
    private final Consumer<ProtocolVariant> protocolVariant;
    private Vector<?> pendingLegacyResult;

    public WinLaufenProtocolReader(ObjectInputStream input, Consumer<ClockValue> clocks,
                                   Consumer<ResultBlock> results) {
        this(input, clocks, results, ignored -> { });
    }

    public WinLaufenProtocolReader(ObjectInputStream input, Consumer<ClockValue> clocks,
                                   Consumer<ResultBlock> results, Consumer<String> messages) {
        this(input, clocks, results, messages, ignored -> { });
    }

    public WinLaufenProtocolReader(ObjectInputStream input, Consumer<ClockValue> clocks,
                                   Consumer<ResultBlock> results, Consumer<String> messages,
                                   Consumer<ProtocolVariant> protocolVariant) {
        this.protocolVariant = protocolVariant;
        this.input = input;
        this.clocks = clocks;
        this.results = results;
        this.messages = messages;
        input.setObjectInputFilter(WinLaufenObjectFilter.create());
    }

    public void readNext() throws IOException, ClassNotFoundException {
        Object first = input.readObject();
        if (pendingLegacyResult != null) {
            Vector<?> vector = pendingLegacyResult;
            pendingLegacyResult = null;
            if ("ende".equals(first)) {
                readLegacyResult(vector);
                protocolVariant.accept(ProtocolVariant.LEGACY);
                return;
            }
            // Missing terminator: discard the incomplete block, not the following object.
        }
        if (first instanceof String string) {
            ClockValue clock = ClockValue.parse(string);
            if (clock != null) {
                clocks.accept(clock);
                protocolVariant.accept(ProtocolVariant.CURRENT);
            } else if (!"ende".equals(string)) {
                readResultBlock(string, input::readObject);
                protocolVariant.accept(ProtocolVariant.CURRENT);
            }
        } else if (first instanceof Vector<?> vector) {
            if (vector.size() == 1 && vector.get(0) instanceof String value
                    && value.matches("\\d{2}:\\d{2}:\\d{2}")) {
                clocks.accept(new ClockValue(value));
                protocolVariant.accept(ProtocolVariant.LEGACY);
            } else if (isLegacyResult(vector)) {
                // Keep the block pending across socket timeouts until its separate terminator.
                pendingLegacyResult = vector;
            } else {
                consumeMessage(vector);
            }
        } else {
            throw new ProtocolException("Unexpected top-level object: " + type(first));
        }
    }

    private static boolean isLegacyResult(Vector<?> vector) {
        return vector.size() == 11
                && vector.get(0) instanceof String
                && vector.get(1) instanceof Integer
                && vector.get(2) instanceof Integer
                && vector.get(3) instanceof String[]
                && vector.get(4) instanceof int[]
                && vector.get(5) instanceof Integer
                && vector.get(6) instanceof Integer
                && vector.get(7) instanceof Integer
                && vector.get(8) instanceof Integer
                && vector.get(9) instanceof Object[][]
                && vector.get(10) instanceof String[];
    }

    private void readLegacyResult(Vector<?> vector) throws IOException, ClassNotFoundException {
        // Normalize only the wire envelope. The existing parser validates and publishes it.
        List<Object> fields = new ArrayList<>(vector.subList(1, 9));
        Object[][] table = (Object[][]) vector.get(9);
        if (table.length > ContractLimits.MAX_ROWS) throw new ProtocolException("Too many rows");
        for (Object[] row : table) {
            if (row == null) throw new ProtocolException("Missing result row");
            Object[] cells = row.clone();
            for (int column = 0; column < Math.min(2, cells.length); column++) {
                if (cells[column] instanceof Integer value) cells[column] = value.toString();
            }
            fields.add(cells);
        }
        fields.add("tabelle");
        fields.add(vector.get(10));
        fields.add("ende"); // The real, separate terminator has already been consumed.
        var normalized = fields.iterator();
        readResultBlock((String) vector.get(0), normalized::next);
    }

    @FunctionalInterface
    private interface WireFields {
        Object read() throws IOException, ClassNotFoundException;
    }

    private void readResultBlock(String competitionType, WireFields fields)
            throws IOException, ClassNotFoundException {
        text(competitionType, ContractLimits.MAX_NAME_CHARS, "competition type");
        int evaluationMode = integer(fields, "evaluation mode");
        int classCount = integer(fields, "class count");
        if (classCount < 1 || classCount > ContractLimits.MAX_CLASSES) {
            throw new ProtocolException("Invalid class count");
        }
        String[] classNames = object(fields, String[].class, "class names");
        for (String className : classNames) text(className, ContractLimits.MAX_NAME_CHARS, "class name");
        int[] rounds = object(fields, int[].class, "round/team values");
        int winSpringenPosition = integer(fields, "WinSpringen position");
        int classIndex = integer(fields, "speaker class index");
        int roundOrHeat = integer(fields, "round/heat");
        int currentFinish = integer(fields, "current finish");
        if (classNames.length != classCount || rounds.length != classCount
                || classIndex < 0 || classIndex >= classCount) {
            throw new ProtocolException("Inconsistent class metadata");
        }

        List<List<String>> rows = new ArrayList<>();
        Object next;
        while ((next = fields.read()) instanceof Object[] row) {
            List<String> cells = new ArrayList<>(row.length);
            for (Object cell : row) {
                if (!(cell instanceof String)) throw new ProtocolException("Non-string table cell");
                text((String) cell, ContractLimits.MAX_CELL_CHARS, "table cell");
                cells.add((String) cell);
            }
            rows.add(cells);
            if (rows.size() > ContractLimits.MAX_ROWS) throw new ProtocolException("Too many rows");
        }
        if (!"tabelle".equals(next)) throw new ProtocolException("Missing tabelle marker");
        String[] headers = object(fields, String[].class, "table headers");
        for (String header : headers) text(header, ContractLimits.MAX_CELL_CHARS, "table header");
        if (!"ende".equals(fields.read())) throw new ProtocolException("Missing ende marker");
        if (headers.length == 0 || headers.length > ContractLimits.MAX_HEADERS
                || rows.stream().anyMatch(row -> row.size() != headers.length)
                || currentFinish < 0 || currentFinish >= rows.size()) {
            throw new ProtocolException("Invalid result table");
        }
        results.accept(new ResultBlock(competitionType, evaluationMode, classNames, rounds,
                winSpringenPosition, classIndex, roundOrHeat, currentFinish, rows, Arrays.asList(headers)));
    }

    private void consumeMessage(Vector<?> vector) throws ProtocolException {
        if (vector.size() != 2 || !(vector.get(0) instanceof String)
                || !"nachricht".equals(vector.get(1))) {
            throw new ProtocolException("Invalid Vector message");
        }
        text((String) vector.get(0), ContractLimits.MAX_MESSAGE_CHARS, "message");
        messages.accept((String) vector.get(0));
    }

    /**
     * Structural size guard at the source entry boundary. Values that exceed a contract limit are
     * rejected here, so the canonical state can never adopt data that is unpublishable later.
     * This bounds resources only; it never judges whether a competition value is plausible.
     */
    private static void text(String value, int limit, String name) throws ProtocolException {
        if (value == null) throw new ProtocolException("Missing " + name);
        if (value.length() > limit) throw new ProtocolException(name + " exceeds size limit");
    }

    private int integer(WireFields fields, String name) throws IOException, ClassNotFoundException {
        return object(fields, Integer.class, name);
    }

    private <T> T object(WireFields fields, Class<T> expected, String name) throws IOException, ClassNotFoundException {
        Object value = fields.read();
        if (!expected.isInstance(value)) throw new ProtocolException("Invalid " + name + ": " + type(value));
        return expected.cast(value);
    }

    private static String type(Object object) { return object == null ? "null" : object.getClass().getName(); }

    public static final class ProtocolException extends IOException {
        public ProtocolException(String message) { super(message); }
    }
}
