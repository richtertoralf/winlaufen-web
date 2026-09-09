package de.winlaufen.web.contract;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Codec and strict validation of the versioned bridge/live-server contract.
 *
 * <p>Validation is structural only. Header order, cell values, indices, clock and messages are
 * transported exactly as WinLaufen supplied them.
 */
public final class ContractJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    static {
        MAPPER.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(20)
                .maxStringLength(1_000_000)
                .maxNumberLength(32)
                .build());
    }

    private ContractJson() { }

    public static String snapshot(SnapshotEnvelope value) {
        validate(value);
        String json = write(value);
        if (json.length() > ContractLimits.MAX_JSON_CHARS) {
            throw new ContractViolationException("Snapshot exceeds size limit");
        }
        return json;
    }

    public static SnapshotEnvelope readSnapshot(String json) throws JsonProcessingException {
        if (json == null || json.length() > ContractLimits.MAX_JSON_CHARS) {
            throw new ContractViolationException("Snapshot exceeds size limit");
        }
        SnapshotEnvelope value = MAPPER.readValue(json, SnapshotEnvelope.class);
        validate(value);
        return value;
    }

    public static String startList(StartListEnvelope value) {
        validate(value);
        String json = write(value);
        if (json.length() > ContractLimits.MAX_JSON_CHARS) {
            throw new ContractViolationException("Start list exceeds size limit");
        }
        return json;
    }

    public static StartListEnvelope readStartList(String json) throws JsonProcessingException {
        if (json == null || json.length() > ContractLimits.MAX_JSON_CHARS) {
            throw new ContractViolationException("Start list exceeds size limit");
        }
        StartListEnvelope value = MAPPER.readValue(json, StartListEnvelope.class);
        validate(value);
        return value;
    }

    /**
     * The {@code type} of an ingest message, so a receiver can pick the right envelope.
     *
     * <p>Scanned with the streaming parser instead of parsing the whole document twice: the
     * snapshot path runs on every clock telegram and must not pay for the start list existing.
     * The field is found wherever it stands, so this does not depend on field order.
     */
    public static String typeOf(String json) {
        if (json == null || json.length() > ContractLimits.MAX_JSON_CHARS) {
            throw new ContractViolationException("Message exceeds size limit");
        }
        try (JsonParser parser = MAPPER.getFactory().createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new ContractViolationException("Message is not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("type".equals(field)) {
                    if (value != JsonToken.VALUE_STRING) {
                        throw new ContractViolationException("Message type is not a string");
                    }
                    return parser.getText();
                }
                parser.skipChildren();
            }
        } catch (IOException ex) {
            throw new ContractViolationException("Message is not valid JSON", ex);
        }
        throw new ContractViolationException("Message without type");
    }

    public static String ack(AckEnvelope value) {
        validate(value);
        return write(value);
    }

    public static AckEnvelope readAck(String json) throws JsonProcessingException {
        if (json == null || json.length() > ContractLimits.MAX_ACK_CHARS) {
            throw new ContractViolationException("ACK exceeds size limit");
        }
        AckEnvelope value = MAPPER.readValue(json, AckEnvelope.class);
        validate(value);
        return value;
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ContractViolationException("Cannot encode contract", ex);
        }
    }

    /**
     * Validates the payload a bridge is about to publish, independent of envelope metadata.
     * Source adapters call this at their own entry boundary so that the canonical state never
     * adopts a value that could not be published afterwards.
     */
    public static void validateState(CanonicalState state, PresentationConfig presentation) {
        if (state == null || presentation == null) {
            throw new ContractViolationException("Invalid canonical state");
        }
        if (state.sourceHealth() == null) {
            throw new ContractViolationException("Invalid source health");
        }
        if (state.clock() != null && state.clock().length() > ContractLimits.MAX_NAME_CHARS) {
            throw new ContractViolationException("Clock exceeds size limit");
        }
        if (state.message() != null && state.message().length() > ContractLimits.MAX_MESSAGE_CHARS) {
            throw new ContractViolationException("Message exceeds size limit");
        }
        validateCompetition(state.competition());
        validateCurrentFinish(state.currentFinish(), state.competition());
    }

    private static void validate(SnapshotEnvelope value) {
        if (value == null
                || !"snapshot".equals(value.type())
                || value.schemaVersion() != SnapshotEnvelope.SCHEMA_VERSION
                || blank(value.channelId())
                || blank(value.streamId())
                || value.sourceRevision() < 0) {
            throw new ContractViolationException("Invalid snapshot envelope");
        }
        validateState(value.state(), value.presentation());
    }

    /**
     * Structural validation only, matching the rules the bridge already applies to an import:
     * bib and class are mandatory, {@code (className, bib)} is unique, and every value stays
     * within its bound. An absent start list carries generation 0 and no entries.
     */
    private static void validate(StartListEnvelope value) {
        if (value == null
                || !StartListEnvelope.TYPE.equals(value.type())
                || value.schemaVersion() != SnapshotEnvelope.SCHEMA_VERSION
                || blank(value.channelId())
                || blank(value.streamId())
                || value.generation() < 0
                || value.source() == null
                || value.source().length() > ContractLimits.MAX_IDENTIFIER_CHARS
                || value.sourceLabel() == null
                || value.sourceLabel().length() > ContractLimits.MAX_START_LIST_VALUE_CHARS
                || value.entries().size() > ContractLimits.MAX_START_LIST_ENTRIES) {
            throw new ContractViolationException("Invalid start list envelope");
        }
        if (value.generation() == 0 != value.entries().isEmpty()) {
            throw new ContractViolationException(
                    "A start list is absent exactly when it has generation 0 and no entries");
        }
        Set<List<String>> seen = new HashSet<>();
        for (StartListRow row : value.entries()) {
            validateRow(row);
            if (!seen.add(List.of(row.className(), row.bib()))) {
                throw new ContractViolationException("Duplicate start number in one class");
            }
        }
    }

    private static void validateRow(StartListRow row) {
        if (row == null
                || row.bib() == null || row.bib().isEmpty()
                || row.className() == null || row.className().isEmpty()) {
            throw new ContractViolationException("Start list entry without start number or class");
        }
        startListValue(row.bib());
        startListValue(row.className());
        startListValue(row.startTime());
        startListValue(row.lastName());
        startListValue(row.firstName());
        startListValue(row.club());
        startListValue(row.association());
        startListValue(row.course());
        startListValue(row.birthYear());
        startListValue(row.gender());
        startListValue(row.nation());
    }

    private static void startListValue(String value) {
        if (value == null || value.length() > ContractLimits.MAX_START_LIST_VALUE_CHARS) {
            throw new ContractViolationException("Invalid start list value");
        }
    }

    private static void validateCompetition(Competition competition) {
        if (competition == null) {
            return;
        }
        if (competition.type() == null
                || competition.type().length() > ContractLimits.MAX_NAME_CHARS
                || competition.classCount() != competition.classes().size()
                || competition.classes().size() > ContractLimits.MAX_CLASSES) {
            throw new ContractViolationException("Invalid competition");
        }
        for (CompetitionClass item : competition.classes()) {
            validateClass(item, competition.classCount());
        }
    }

    private static void validateClass(CompetitionClass item, int classCount) {
        if (item.index() < 0
                || item.index() >= classCount
                || item.name() == null
                || item.name().length() > ContractLimits.MAX_NAME_CHARS) {
            throw new ContractViolationException("Invalid class");
        }
        ClassSnapshot snapshot = item.snapshot();
        if (snapshot == null) {
            return;
        }
        if (snapshot.sourceRevision() < 0
                || snapshot.headers().isEmpty()
                || snapshot.headers().size() > ContractLimits.MAX_HEADERS
                || snapshot.rows().size() > ContractLimits.MAX_ROWS) {
            throw new ContractViolationException("Invalid table");
        }
        snapshot.headers().forEach(ContractJson::cell);
        for (List<String> row : snapshot.rows()) {
            if (row.size() != snapshot.headers().size()) {
                throw new ContractViolationException("Invalid row width");
            }
            row.forEach(ContractJson::cell);
        }
    }

    private static void validateCurrentFinish(CurrentFinish finish, Competition competition) {
        if (finish == null) {
            return;
        }
        if (finish.classIndex() < 0 || finish.rowIndex() < 0 || finish.snapshotSourceRevision() < 0) {
            throw new ContractViolationException("Invalid current finish");
        }
        if (competition != null && finish.classIndex() >= competition.classCount()) {
            throw new ContractViolationException("Current finish references an unknown class");
        }
    }

    private static void validate(AckEnvelope value) {
        if (value == null
                || !"ack".equals(value.type())
                || value.schemaVersion() != SnapshotEnvelope.SCHEMA_VERSION
                || blank(value.channelId())
                || blank(value.streamId())
                || value.sourceRevision() < 0) {
            throw new ContractViolationException("Invalid ack envelope");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank() || value.length() > ContractLimits.MAX_IDENTIFIER_CHARS;
    }

    private static void cell(String value) {
        if (value == null || value.length() > ContractLimits.MAX_CELL_CHARS) {
            throw new ContractViolationException("Cell exceeds size limit");
        }
    }
}
