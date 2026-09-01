package de.winlaufen.web.bridge.control;

import de.winlaufen.web.bridge.config.BridgeConfig;
import de.winlaufen.web.bridge.config.BridgeConfigStore;
import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.contract.PresentationConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the configuration form turns into output targets.
 *
 * <p>The connection key is the part a normal organiser cannot supply, so a brand new self-hosted
 * target may be created from its address alone. That convenience is deliberately narrow, and these
 * tests pin its edges rather than its happy path only.
 */
class BridgeControlTargetFormTest {

    @Test
    void aNewSelfhostTargetMayBeCreatedWithoutAConnectionKey() {
        Map<String, String> form = form(1);
        put(form, 0, "selfhost-203-0-113-7-local", "SELFHOST",
                "ws://203.0.113.7:44441/bridge/v1/channels/local", "local", "");

        List<OutputTargetConfig> targets = BridgeControlServer.targets(form, existing());

        assertEquals(1, targets.size());
        assertEquals(BridgeConfigStore.DEFAULT_LOCAL_SECRET, targets.get(0).secret());
        assertEquals(OutputTargetType.SELFHOST, targets.get(0).type());
    }

    @Test
    void anExistingTargetKeepsItsStoredConnectionKeyWhenTheFieldStaysEmpty() {
        Map<String, String> form = form(1);
        put(form, 0, "club", "SELFHOST", "wss://club.example/bridge/v1/channels/race", "race", "");

        List<OutputTargetConfig> targets = BridgeControlServer.targets(form, existing());

        assertEquals("stored-club-secret", targets.get(0).secret(),
                "an empty field keeps the stored key instead of falling back to the default");
    }

    @Test
    void anExplicitConnectionKeyAlwaysWins() {
        Map<String, String> form = form(1);
        put(form, 0, "club", "SELFHOST", "wss://club.example/bridge/v1/channels/race", "race",
                "eigenes-secret");

        assertEquals("eigenes-secret", BridgeControlServer.targets(form, existing()).get(0).secret());
    }

    @Test
    void theFallbackNeverAppliesToTheLocalTargetOrToRichterProjects() {
        Map<String, String> local = form(1);
        put(local, 0, "neu-lokal", "LOCAL", "ws://127.0.0.1:44441/bridge/v1/channels/local",
                "local", "");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BridgeControlServer.targets(local, existing())).getMessage()
                .contains("Secret fehlt"));

        Map<String, String> hosted = form(1);
        put(hosted, 0, "hosted", "RICHTER_PROJECTS", "wss://live.example/bridge/v1/channels/race",
                "race", "");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BridgeControlServer.targets(hosted, existing())).getMessage()
                .contains("Secret fehlt"));
    }

    @Test
    void theStoredOrderAndTheStoredValuesOfExistingTargetsSurvive() {
        Map<String, String> form = form(2);
        put(form, 0, "local", "LOCAL", "ws://127.0.0.1:44441/bridge/v1/channels/local", "local", "");
        put(form, 1, "club", "SELFHOST", "wss://club.example/bridge/v1/channels/race", "race", "");

        List<OutputTargetConfig> targets = BridgeControlServer.targets(form, existing());

        assertEquals(List.of("local", "club"), targets.stream().map(OutputTargetConfig::id).toList());
        assertEquals("stored-local-secret", targets.get(0).secret());
        assertEquals("race", targets.get(1).channelId());
        assertEquals(URI.create("wss://club.example/bridge/v1/channels/race"),
                targets.get(1).endpoint());
    }

    @Test
    void anEndpointRejectedByThePolicyStillFails() {
        Map<String, String> form = form(1);
        put(form, 0, "neu", "SELFHOST", "ws://liveserver.example.com/bridge/v1/channels/local",
                "local", "");

        assertThrows(IllegalArgumentException.class,
                () -> BridgeControlServer.targets(form, existing()));
    }

    private static Map<String, String> form(int count) {
        Map<String, String> form = new HashMap<>();
        form.put("targetCount", Integer.toString(count));
        return form;
    }

    private static void put(Map<String, String> form, int index, String id, String type,
                            String endpoint, String channel, String secret) {
        String prefix = "target." + index + ".";
        form.put(prefix + "id", id);
        form.put(prefix + "type", type);
        form.put(prefix + "enabled", "on");
        form.put(prefix + "endpoint", endpoint);
        form.put(prefix + "channelId", channel);
        form.put(prefix + "secret", secret);
    }

    private static BridgeConfig existing() {
        return new BridgeConfig("WINLAUFEN", "timing-pc", "0.0.0.0", 44442,
                List.of(
                        new OutputTargetConfig("local", OutputTargetType.LOCAL, true,
                                URI.create("ws://127.0.0.1:44441/bridge/v1/channels/local"),
                                "local", "stored-local-secret"),
                        new OutputTargetConfig("club", OutputTargetType.SELFHOST, true,
                                URI.create("wss://club.example/bridge/v1/channels/race"),
                                "race", "stored-club-secret")),
                PresentationConfig.defaults());
    }
}
