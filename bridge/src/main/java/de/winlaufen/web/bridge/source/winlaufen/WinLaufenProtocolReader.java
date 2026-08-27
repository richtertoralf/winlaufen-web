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

    public WinLaufenProtocolReader(ObjectInputStream input, Consumer<ClockValue> clocks,
                                   Consumer<ResultBlock> results) {
        this(input, clocks, results, ignored -> { });
    }

    public WinLaufenProtocolReader(ObjectInputStream input, Consumer<ClockValue> clocks,
                                   Consumer<ResultBlock> results, Consumer<String> messages) {
        this.input = input;
        this.clocks = clocks;
        this.results = results;
        this.messages = messages;
        input.setObjectInputFilter(WinLaufenObjectFilter.create());
    }

    public void readNext() throws IOException, ClassNotFoundException {
        Object first = input.readObject();
        if (first instanceof String string) {
            ClockValue clock = ClockValue.parse(string);
            if (clock != null) clocks.accept(clock);
            else readResultBlock(string);
        } else if (first instanceof Vector<?> vector) {
            consumeMessage(vector);
        } else {
            throw new ProtocolException("Unexpected top-level object: " + type(first));
        }
    }

    private void readResultBlock(String competitionType) throws IOException, ClassNotFoundException {
        text(competitionType, ContractLimits.MAX_NAME_CHARS, "competition type");
        int evaluationMode = integer("evaluation mode");
        int classCount = integer("class count");
        if (classCount < 1 || classCount > ContractLimits.MAX_CLASSES) {
            throw new ProtocolException("Invalid class count");
        }
        String[] classNames = object(String[].class, "class names");
        for (String className : classNames) text(className, ContractLimits.MAX_NAME_CHARS, "class name");
        int[] rounds = object(int[].class, "round/team values");
        int winSpringenPosition = integer("WinSpringen position");
        int classIndex = integer("speaker class index");
        int roundOrHeat = integer("round/heat");
        int currentFinish = integer("current finish");
        if (classNames.length != classCount || rounds.length != classCount
                || classIndex < 0 || classIndex >= classCount) {
            throw new ProtocolException("Inconsistent class metadata");
        }

        List<List<String>> rows = new ArrayList<>();
        Object next;
        while ((next = input.readObject()) instanceof Object[] row) {
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
        String[] headers = object(String[].class, "table headers");
        for (String header : headers) text(header, ContractLimits.MAX_CELL_CHARS, "table header");
        if (!"ende".equals(input.readObject())) throw new ProtocolException("Missing ende marker");
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

    private int integer(String name) throws IOException, ClassNotFoundException {
        return object(Integer.class, name);
    }

    private <T> T object(Class<T> expected, String name) throws IOException, ClassNotFoundException {
        Object value = input.readObject();
        if (!expected.isInstance(value)) throw new ProtocolException("Invalid " + name + ": " + type(value));
        return expected.cast(value);
    }

    private static String type(Object object) { return object == null ? "null" : object.getClass().getName(); }

    public static final class ProtocolException extends IOException {
        public ProtocolException(String message) { super(message); }
    }
}
