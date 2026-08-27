package de.winlaufen.web.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

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
