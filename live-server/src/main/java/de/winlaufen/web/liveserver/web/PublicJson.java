package de.winlaufen.web.liveserver.web;

import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.ClassSnapshot;
import de.winlaufen.web.contract.Competition;
import de.winlaufen.web.contract.CompetitionClass;
import de.winlaufen.web.contract.CurrentFinish;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.StartListRow;
import de.winlaufen.web.liveserver.state.ChainStatus;
import de.winlaufen.web.liveserver.state.PublishedStartList;
import de.winlaufen.web.liveserver.state.PublishedState;

import java.time.Instant;
import java.util.List;

/**
 * Public browser wire format. It carries exactly the published state and the presentation config;
 * source host, output targets, endpoints and credentials are never part of it.
 *
 * <p>Wire strings are passed through unchanged; only JSON control characters are escaped.
 */
public final class PublicJson {

    private PublicJson() { }

    /**
     * Version of the read API's own envelope. It is not the contract schema version and not the
     * product version: it changes only if this JSON shape ever stops being backwards compatible.
     */
    public static final int API_VERSION = 1;

    /**
     * Generic read API: the current state for external consumers, small enough to poll often.
     *
     * <p>Deliberately a superset of {@link #state}, because the web viewer bootstraps from this
     * same endpoint before its WebSocket is up. Everything added here is additive; the fields the
     * viewer reads keep their names and meaning.
     *
     * <p>The start list appears here only as metadata. Its entries would make a frequently polled
     * endpoint carry thousands of rows that change a few times a day; they are served by
     * {@link #apiStartList} instead.
     */
    public static String apiState(String channelId, PublishedState published,
                                  PublishedStartList startList) {
        return "{\"apiVersion\":" + API_VERSION
                + ",\"type\":\"snapshot\""
                + ",\"channelId\":" + quote(channelId)
                + ",\"streamId\":" + nullable(published.streamId())
                + ",\"sourceRevision\":" + published.sourceRevision()
                + ",\"publicationRevision\":" + published.publicationRevision()
                + "," + clockFields(published)
                + ",\"connection\":" + connection(published)
                + ",\"startList\":" + startListMetadata(startList)
                + ",\"state\":" + canonical(published.state())
                + ",\"presentation\":" + presentation(published.presentation())
                + "}";
    }

    /**
     * Generic read API: the complete current start list.
     *
     * <p>It carries the competition time and the connection status of the moment the request was
     * answered, taken from the live competition state — never from the state that happened to be
     * current when the start list was published. A start list changes a few times a day and the
     * clock runs continuously; freezing the clock into the start list would hand a consumer a time
     * that is hours old without any sign of it.
     *
     * <p>Entry order is the published order, which is the import order. Nothing is sorted and no
     * participant id is invented; the source has none.
     */
    public static String apiStartList(String channelId, PublishedState published,
                                      PublishedStartList startList) {
        return "{\"apiVersion\":" + API_VERSION
                + ",\"type\":\"startlist\""
                + ",\"channelId\":" + quote(channelId)
                + ",\"streamId\":" + nullable(published.streamId())
                + "," + clockFields(published)
                + ",\"connection\":" + connection(published)
                + "," + startListFacts(startList)
                + ",\"entries\":" + startListEntries(startList)
                + "}";
    }

    /**
     * The competition time plus when this live server observed it.
     *
     * <p>{@code clock} is the WinLaufen competition time, carried through as the string WinLaufen
     * sent. It is not a timestamp: it has no date and no time zone, and nothing here turns it into
     * one or derives a finish time from it.
     *
     * <p>{@code clockObservedAt} is an ordinary UTC instant of this machine and answers a different
     * question — how old the value above is. It moves only when the clock value itself changes, so
     * a clock frozen since the source vanished visibly ages instead of looking fresh. Both are null
     * until a clock has ever arrived; inventing one would be worse than saying nothing.
     */
    private static String clockFields(PublishedState published) {
        String observed = published.clockObservedAtEpochMilli() > 0
                ? quote(Instant.ofEpochMilli(published.clockObservedAtEpochMilli()).toString())
                : "null";
        return "\"clock\":" + nullable(published.state().clock())
                + ",\"clockObservedAt\":" + observed;
    }

    /**
     * Where the delivery chain stands. A consumer must never have to infer this from the presence
     * of a competition time or of results: both are the last known copy and stay readable while
     * the source is gone.
     *
     * <p>{@code winlaufen} is what the bridge reported about its own source. While
     * {@code bridge} is not {@code CONNECTED} it is the last known value rather than a current
     * one — without a bridge nobody can observe WinLaufen — and {@code status} says so.
     */
    private static String connection(PublishedState published) {
        return "{\"status\":" + quote(published.chainStatus().name())
                + ",\"winlaufen\":" + quote(published.reportedSourceHealth().name())
                + ",\"bridge\":" + quote(published.bridgeLinkConnected() ? "CONNECTED" : "DISCONNECTED")
                + ",\"fresh\":" + (published.chainStatus() == ChainStatus.CONNECTED)
                + ",\"stateAvailable\":" + published.available()
                + "}";
    }

    private static String startListMetadata(PublishedStartList startList) {
        return "{" + startListFacts(startList) + "}";
    }

    /**
     * What is known about the current stock, used identically by both endpoints so they can never
     * disagree.
     *
     * <p>Without a start list, {@code source} and {@code sourceLabel} are null rather than a
     * value. The bridge has to put something in those fields even when it states "I have no start
     * list", and reporting that filler would tell a consumer a file format and an origin for a
     * stock that does not exist.
     */
    private static String startListFacts(PublishedStartList startList) {
        boolean present = startList.present();
        return "\"present\":" + present
                + ",\"generation\":" + startList.generation()
                + ",\"source\":" + (present ? quote(startList.source()) : "null")
                + ",\"sourceLabel\":" + (present ? quote(startList.sourceLabel()) : "null")
                + ",\"entryCount\":" + startList.entries().size()
                + ",\"classCount\":" + startList.classCount();
    }

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
        return "{\"type\":\"startlist\""
                + ",\"publicationRevision\":" + published.publicationRevision()
                + ",\"generation\":" + published.generation()
                + ",\"source\":" + quote(published.source())
                + ",\"sourceLabel\":" + quote(published.sourceLabel())
                + ",\"entries\":" + startListEntries(published)
                + "}";
    }

    /**
     * The entries themselves, shared by the browser message and the read API so both can never
     * describe a participant differently. The field names are the canonical ones; there is no
     * participant id because the source does not have one.
     */
    private static String startListEntries(PublishedStartList published) {
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
        return entries.append(']').toString();
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
