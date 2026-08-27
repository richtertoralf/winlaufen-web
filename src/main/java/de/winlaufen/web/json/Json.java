package de.winlaufen.web.json;

import de.winlaufen.web.config.AppConfig;
import de.winlaufen.web.model.*;
import de.winlaufen.web.state.StateEvent;

import java.util.List;

public final class Json {
    private Json() { }

    public static String event(StateEvent event) {
        String type = switch (event.type()) {
            case SNAPSHOT -> "snapshot";
            case CLOCK -> "clock";
            case CLASS_SNAPSHOT -> "classSnapshot";
            case MESSAGE -> "message";
        };
        return "{\"type\":" + quote(type) + ",\"state\":" + state(event.state())
                + (event.classIndex() >= 0 ? ",\"classIndex\":" + event.classIndex() : "") + "}";
    }

    public static String state(AppState state) {
        return "{\"revision\":" + state.revision()
                + ",\"health\":" + quote(state.health().name())
                + ",\"clock\":" + nullable(state.clock())
                + ",\"competition\":" + competition(state.competition())
                + ",\"currentFinish\":" + finish(state.currentFinish())
                + ",\"message\":" + nullable(state.message()) + "}";
    }

    public static String config(AppConfig config) {
        StringBuilder modes = new StringBuilder("[");
        for (OutputMode mode : OutputMode.values()) {
            if (modes.length() > 1) modes.append(',');
            modes.append("{\"name\":").append(quote(mode.name())).append(",\"enabled\":").append(mode.enabled()).append('}');
        }
        return "{\"winLaufenHost\":" + quote(config.winLaufenHost())
                + ",\"winLaufenPort\":4444,\"outputMode\":" + quote(config.outputMode().name())
                + ",\"httpPort\":" + config.httpPort() + ",\"webSocketPort\":" + config.webSocketPort()
                + ",\"showClub\":" + config.publicDisplay().showClub()
                + ",\"showAssociation\":" + config.publicDisplay().showAssociation()
                + ",\"showNation\":" + config.publicDisplay().showNation()
                + ",\"showShooting\":" + config.publicDisplay().showShooting()
                + ",\"showPublicMessages\":" + config.publicDisplay().showPublicMessages()
                + ",\"outputModes\":" + modes.append(']') + "}";
    }

    private static String competition(Competition competition) {
        if (competition == null) return "null";
        StringBuilder classes = new StringBuilder("[");
        for (CompetitionClass item : competition.classes()) {
            if (classes.length() > 1) classes.append(',');
            classes.append("{\"index\":").append(item.index()).append(",\"name\":").append(quote(item.name()))
                    .append(",\"roundsOrTeamSize\":").append(item.roundsOrTeamSize())
                    .append(",\"snapshot\":").append(snapshot(item.snapshot())).append('}');
        }
        return "{\"type\":" + quote(competition.type()) + ",\"evaluationMode\":" + competition.evaluationMode()
                + ",\"classCount\":" + competition.classCount() + ",\"winSpringenPosition\":"
                + competition.winSpringenPosition() + ",\"roundOrHeat\":" + competition.roundOrHeat()
                + ",\"classes\":" + classes.append(']') + "}";
    }

    private static String snapshot(ClassSnapshot snapshot) {
        if (snapshot == null) return "null";
        StringBuilder rows = new StringBuilder("[");
        for (List<String> row : snapshot.rows()) {
            if (rows.length() > 1) rows.append(',');
            rows.append(strings(row));
        }
        return "{\"revision\":" + snapshot.revision() + ",\"headers\":" + strings(snapshot.headers())
                + ",\"rows\":" + rows.append(']') + "}";
    }

    private static String finish(CurrentFinish finish) {
        return finish == null ? "null" : "{\"classIndex\":" + finish.classIndex() + ",\"rowIndex\":"
                + finish.rowIndex() + ",\"snapshotRevision\":" + finish.snapshotRevision() + "}";
    }

    private static String strings(List<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (String value : values) {
            if (result.length() > 1) result.append(',');
            result.append(quote(value));
        }
        return result.append(']').toString();
    }

    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20 || c == '\u2028' || c == '\u2029') out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }

    private static String nullable(String value) { return value == null ? "null" : quote(value); }
}
