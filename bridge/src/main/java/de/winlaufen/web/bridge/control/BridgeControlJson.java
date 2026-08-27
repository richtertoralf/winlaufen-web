package de.winlaufen.web.bridge.control;

import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.output.OutputTargetRuntime;
import de.winlaufen.web.bridge.state.CanonicalSnapshot;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.PresentationConfig;

import java.util.List;

/**
 * JSON views of the Bridge Control API.
 *
 * <p>Encoding is delegated to the contract codec instead of a separate hand-written escaper, so
 * control characters in values such as {@code lastError} cannot produce invalid JSON.
 *
 * <p>Target secrets are never part of any view; only {@code secretConfigured} is exposed.
 */
public final class BridgeControlJson {

    private BridgeControlJson() { }

    public record TargetView(String id, String type, boolean enabled, String endpoint,
                             String channelId, boolean secretConfigured) { }

    public record ConfigView(String sourceType, String sourceHost, int sourcePort,
                             List<TargetView> targets, PresentationConfig presentation) { }

    public record OutputView(String targetId, String state, long lastAckedSourceRevision,
                             int retryAttempt, String lastError) { }

    public record StatusView(long sourceRevision, String sourceHealth, String clock,
                             List<OutputView> outputs) { }

    public record ErrorView(String error) { }

    public static String config(BridgeConfig config) {
        List<TargetView> targets = config.targets().stream()
                .map(BridgeControlJson::view)
                .toList();
        return ContractJson.write(new ConfigView("WINLAUFEN", config.sourceHost(),
                BridgeConfig.WINLAUFEN_PORT, targets, config.presentation()));
    }

    public static String status(CanonicalSnapshot snapshot, List<OutputTargetRuntime> runtimes) {
        List<OutputView> outputs = runtimes.stream()
                .map(runtime -> new OutputView(runtime.targetId(), runtime.state().name(),
                        runtime.lastAckedSourceRevision(), runtime.retryAttempt(), runtime.lastError()))
                .toList();
        return ContractJson.write(new StatusView(snapshot.sourceRevision(),
                snapshot.state().sourceHealth().name(), snapshot.state().clock(), outputs));
    }

    public static String error(String message) {
        return ContractJson.write(new ErrorView(message == null ? "Unbekannter Fehler" : message));
    }

    private static TargetView view(OutputTargetConfig target) {
        return new TargetView(target.id(), target.type().name(), target.enabled(),
                target.endpoint().toString(), target.channelId(), true);
    }
}
