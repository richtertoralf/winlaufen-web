package de.winlaufen.web.bridge.control;

import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.BridgeConfigStore;
import de.winlaufen.web.bridge.config.EndpointPolicy;
import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.output.OutputTargetRuntime;
import de.winlaufen.web.bridge.startlist.CanonicalStartList;
import de.winlaufen.web.bridge.startlist.StartListEntry;
import de.winlaufen.web.bridge.state.CanonicalSnapshot;
import de.winlaufen.web.contract.CompetitionTimeZoneSource;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.PresentationConfig;

import java.util.List;

/**
 * JSON views of the Bridge Control API.
 *
 * <p>Encoding is delegated to the contract codec instead of a separate hand-written escaper, so
 * control characters in values such as {@code lastError} cannot produce invalid JSON.
 *
 * <p>Target secrets are never part of any view; only {@code secretConfigured} is exposed. The two
 * warning texts are produced here from the backend policy, so Bridge Control renders them instead
 * of deciding security questions in JavaScript.
 */
public final class BridgeControlJson {

    /**
     * Known prototype connection key of a target that transmits off this computer. It names the
     * accepted risk without repeating the value; see README.md, "Known prototype security
     * limitation".
     */
    public static final String DEFAULT_SECRET_WARNING =
            "Bekannter Standard-Verbindungsschlüssel wird verwendet. Für temporäre "
                    + "Selfhost-/Testserver vorgesehen.";

    private BridgeControlJson() { }

    public record TargetView(String id, String type, boolean enabled, String endpoint,
                             String channelId, boolean secretConfigured,
                             String transportWarning, String secretWarning) { }

    public record ConfigView(String sourceType, String sourceHost, int sourcePort,
                             List<TargetView> targets, PresentationConfig presentation) { }

    public record OutputView(String targetId, String state, long lastAckedSourceRevision,
                             int retryAttempt, String lastError) { }

    /**
     * The imported start list at a glance. {@code generation == 0} means none was imported yet;
     * that is the same distinction {@link CanonicalStartList#isPresent()} makes, so the surface
     * never has to guess from an empty participant count.
     *
     * <p>{@code classCount} is derived from the entries on the way out and is deliberately not
     * part of the stored model.
     */
    public record StartListView(long generation, String source, String sourceLabel,
                                int entryCount, int classCount) { }

    public record StatusView(long sourceRevision, String sourceHealth, String clock,
                             List<OutputView> outputs, StartListView startList,
                             TimeZoneView competitionTimeZone, List<String> notices) { }

    /**
     * The zone the bridge actually reads the competition time in, and where it came from.
     *
     * <p>Shown so a mistyped {@code competition.timezone} cannot hide: it falls back to the
     * machine's zone, and then this says {@code SYSTEM_DEFAULT} while {@code notices} names the
     * unusable value.
     */
    public record TimeZoneView(String zone, String source) { }

    public record ErrorView(String error) { }

    public static String config(BridgeConfig config) {
        return ContractJson.write(new ConfigView("WINLAUFEN", config.sourceHost(),
                BridgeConfig.WINLAUFEN_PORT, views(config), config.presentation()));
    }

    /** The same views the API returns, so the bridge log cannot disagree with the surface. */
    public static List<TargetView> views(BridgeConfig config) {
        return config.targets().stream()
                .map(BridgeControlJson::view)
                .toList();
    }

    public static String status(CanonicalSnapshot snapshot, List<OutputTargetRuntime> runtimes,
                                CanonicalStartList startList, String competitionTimeZone,
                                CompetitionTimeZoneSource competitionTimeZoneSource,
                                List<String> notices) {
        List<OutputView> outputs = runtimes.stream()
                .map(runtime -> new OutputView(runtime.targetId(), runtime.state().name(),
                        runtime.lastAckedSourceRevision(), runtime.retryAttempt(), runtime.lastError()))
                .toList();
        return ContractJson.write(new StatusView(snapshot.sourceRevision(),
                snapshot.state().sourceHealth().name(), snapshot.state().clock(), outputs,
                startList(startList),
                new TimeZoneView(competitionTimeZone, competitionTimeZoneSource.name()),
                List.copyOf(notices)));
    }

    /** The result of one accepted import, shown to the operator right after the upload. */
    public static String startListResult(CanonicalStartList startList) {
        return ContractJson.write(startList(startList));
    }

    public static StartListView startList(CanonicalStartList startList) {
        return new StartListView(startList.generation(), startList.source().name(),
                startList.sourceLabel(), startList.entries().size(), classCount(startList));
    }

    private static int classCount(CanonicalStartList startList) {
        return (int) startList.entries().stream()
                .map(StartListEntry::className)
                .distinct()
                .count();
    }

    public static String error(String message) {
        return ContractJson.write(new ErrorView(message == null ? "Unbekannter Fehler" : message));
    }

    private static TargetView view(OutputTargetConfig target) {
        return new TargetView(target.id(), target.type().name(), target.enabled(),
                target.endpoint().toString(), target.channelId(), true,
                EndpointPolicy.transportWarning(target.type(), target.endpoint()),
                secretWarning(target));
    }

    /**
     * The local loopback target is excluded on purpose: it never leaves this computer, and the
     * live server already warns about the known ingest secret when it starts.
     */
    private static String secretWarning(OutputTargetConfig target) {
        boolean known = BridgeConfigStore.DEFAULT_LOCAL_SECRET.equals(target.secret());
        return known && target.type() != OutputTargetType.LOCAL ? DEFAULT_SECRET_WARNING : null;
    }
}
