package de.winlaufen.web.liveserver.web;

import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.ClassSnapshot;
import de.winlaufen.web.contract.Competition;
import de.winlaufen.web.contract.CompetitionClass;
import de.winlaufen.web.contract.CurrentFinish;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.StartListRow;
import de.winlaufen.web.liveserver.state.PublishedStartList;
import de.winlaufen.web.liveserver.state.PublishedState;

import java.util.List;

/**
 * Public browser wire format. It carries exactly the published state and the presentation config;
 * source host, output targets, endpoints and credentials are never part of it.
 *
 * <p>Wire strings are passed through unchanged; only JSON control characters are escaped.
 */
public final class PublicJson {

    private PublicJson() { }

    public static String state(PublishedState published) {
        return "{\"type\":\"snapshot\""
                + ",\"publicationRevision\":" + published.publicationRevision()
                + ",\"state\":" + canonical(published.state())
                + ",\"presentation\":" + presentation(published.presentation())
                + "}";
    }

    /**
     * The published start list as its own browser message.
     *
     * <p>It is not part of {@link #state} on purpose: that message travels with every clock
     * telegram, and a start list has thousands of entries. Browsers receive this one on connect
     * and whenever an import was published.
     *
     * <p>{@code generation} is the bridge's import counter, carried through for display and
     * diagnosis. {@code publicationRevision} is this live server's own ordering of what a browser
     * has already seen; the two are different things and are never compared with each other.
     */
    public static String startList(PublishedStartList published) {
        StringBuilder entries = new StringBuilder("[");
        for (StartListRow row : published.entries()) {
            if (entries.length() > 1) {
                entries.append(',');
            }
            entries.append("{\"bib\":").append(quote(row.bib()))
                    .append(",\"className\":").append(quote(row.className()))
                    .append(",\"startTime\":").append(quote(row.startTime()))
                    .append(",\"lastName\":").append(quote(row.lastName()))
                    .append(",\"firstName\":").append(quote(row.firstName()))
                    .append(",\"club\":").append(quote(row.club()))
                    .append(",\"association\":").append(quote(row.association()))
                    .append(",\"course\":").append(quote(row.course()))
                    .append(",\"birthYear\":").append(quote(row.birthYear()))
                    .append(",\"gender\":").append(quote(row.gender()))
                    .append(",\"nation\":").append(quote(row.nation()))
                    .append('}');
        }
        return "{\"type\":\"startlist\""
                + ",\"publicationRevision\":" + published.publicationRevision()
                + ",\"generation\":" + published.generation()
                + ",\"source\":" + quote(published.source())
                + ",\"sourceLabel\":" + quote(published.sourceLabel())
                + ",\"entries\":" + entries.append(']')
                + "}";
    }

    /**
     * Sign of life for the browser link, deliberately without any state.
     *
     * <p>A browser cannot tell a silent live server from a quiet competition: a dead TCP
     * connection never reports itself, and WebSocket ping/pong is invisible to page scripts. The
     * published state is no substitute either — while the WinLaufen source is down the bridge
     * publishes nothing at all. This message is the only thing the viewer can time out on.
     */
    public static String heartbeat() {
        return "{\"type\":\"heartbeat\"}";
    }

    public static String runtime(int webSocketPort) {
        return "{\"webSocketPort\":" + webSocketPort
                + ",\"webSocketPath\":" + quote(LiveWebSocketServer.BROWSER_PATH) + "}";
    }

    private static String canonical(CanonicalState state) {
        return "{\"health\":" + quote(state.sourceHealth().name())
                + ",\"clock\":" + nullable(state.clock())
                + ",\"competition\":" + competition(state.competition())
                + ",\"currentFinish\":" + finish(state.currentFinish())
                + ",\"message\":" + nullable(state.message())
                + "}";
    }

    private static String presentation(PresentationConfig config) {
        return "{\"showClub\":" + config.showClub()
                + ",\"showAssociation\":" + config.showAssociation()
                + ",\"showNation\":" + config.showNation()
                + ",\"showShooting\":" + config.showShooting()
                + ",\"showPublicMessages\":" + config.showPublicMessages()
                + "}";
    }

    private static String competition(Competition competition) {
        if (competition == null) {
            return "null";
        }
        StringBuilder classes = new StringBuilder("[");
        for (CompetitionClass item : competition.classes()) {
            if (classes.length() > 1) {
                classes.append(',');
            }
            classes.append("{\"index\":").append(item.index())
                    .append(",\"name\":").append(quote(item.name()))
                    .append(",\"roundsOrTeamSize\":").append(item.roundsOrTeamSize())
                    .append(",\"snapshot\":").append(snapshot(item.snapshot()))
                    .append('}');
        }
        return "{\"type\":" + quote(competition.type())
                + ",\"evaluationMode\":" + competition.evaluationMode()
                + ",\"classCount\":" + competition.classCount()
                + ",\"winSpringenPosition\":" + competition.winSpringenPosition()
                + ",\"roundOrHeat\":" + competition.roundOrHeat()
                + ",\"classes\":" + classes.append(']')
                + "}";
    }

    private static String snapshot(ClassSnapshot snapshot) {
        if (snapshot == null) {
            return "null";
        }
        StringBuilder rows = new StringBuilder("[");
        for (List<String> row : snapshot.rows()) {
            if (rows.length() > 1) {
                rows.append(',');
            }
            rows.append(strings(row));
        }
        return "{\"revision\":" + snapshot.sourceRevision()
                + ",\"headers\":" + strings(snapshot.headers())
                + ",\"rows\":" + rows.append(']')
                + "}";
    }

    private static String finish(CurrentFinish finish) {
        if (finish == null) {
            return "null";
        }
        return "{\"classIndex\":" + finish.classIndex()
                + ",\"rowIndex\":" + finish.rowIndex()
                + ",\"snapshotRevision\":" + finish.snapshotSourceRevision()
                + "}";
    }

    private static String strings(List<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (String value : values) {
            if (result.length() > 1) {
                result.append(',');
            }
            result.append(quote(value));
        }
        return result.append(']').toString();
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    // Escape C0 controls and the JS line terminators U+2028/U+2029.
                    if (character < 32 || character == 0x2028 || character == 0x2029) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String nullable(String value) {
        return value == null ? "null" : quote(value);
    }
}
