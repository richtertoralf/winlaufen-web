package de.winlaufen.web.liveserver.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.contract.CanonicalState;
import de.winlaufen.web.contract.ClassSnapshot;
import de.winlaufen.web.contract.ClockSample;
import de.winlaufen.web.contract.CompetitionTimeOffset;
import de.winlaufen.web.contract.Competition;
import de.winlaufen.web.contract.CompetitionClass;
import de.winlaufen.web.contract.CurrentFinish;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SnapshotEnvelope;
import de.winlaufen.web.contract.SourceHealth;
import de.winlaufen.web.contract.StartListEnvelope;
import de.winlaufen.web.contract.StartListRow;
import de.winlaufen.web.contract.TimeReference;
import de.winlaufen.web.contract.TimeReferenceSource;
import de.winlaufen.web.contract.TimeReferenceStatus;
import de.winlaufen.web.liveserver.state.PublishedStartListStore;
import de.winlaufen.web.liveserver.state.PublishedStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generic read API as an external consumer sees it: over real HTTP, against the real stores.
 *
 * <p>The point of most of these tests is not the shape of the JSON but two promises a consumer
 * depends on: every answer carries the competition time of that request, and every answer says
 * where the chain WinLaufen → bridge → live server currently stands.
 */
class ReadApiTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STREAM = "stream-a";

    /** Freezable wall clock, so the observation timestamps are exact instead of approximately now. */
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-10T08:00:00Z"));

    private static final String ZONE = "Europe/Berlin";

    /** The bridge is a different machine and gets its own clock, on purpose. */
    private final AtomicReference<Instant> bridgeNow =
            new AtomicReference<>(Instant.parse("2026-09-10T08:00:00Z"));

    private PublishedStateStore states;
    private PublishedStartListStore startLists;
    private PublicHttpServer server;
    private long revision;
    private long sampleRevision;
    private ClockSample currentSample;

    @BeforeEach
    void start() throws Exception {
        states = new PublishedStateStore("local", new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        });
        startLists = new PublishedStartListStore("local");
        server = new PublicHttpServer("127.0.0.1", 0, 44441, states, startLists,
                TimeReference.systemClock(java.time.Clock.fixed(
                        Instant.parse("2026-09-10T09:99:00Z".replace("99", "30")), ZoneOffset.UTC)));
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    // ---------------------------------------------------------------- /api/v1/state

    @Test
    void stateCarriesClockObservationResultsHealthAndStartListMetadata() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(3));

        JsonNode state = json("/api/v1/state");
        assertEquals(1, state.get("apiVersion").asInt());
        assertEquals("local", state.get("channelId").asText());
        assertEquals(STREAM, state.get("streamId").asText());
        assertEquals("10:00:01", state.get("time").get("competitionTime").asText());
        assertEquals("2026-09-10T08:00:00Z", state.get("time").get("clockChangedAt").asText());

        assertEquals("CONNECTED", state.get("connection").get("status").asText());
        assertEquals("CONNECTED", state.get("connection").get("winlaufen").asText());
        assertEquals("CONNECTED", state.get("connection").get("bridge").asText());
        assertTrue(state.get("connection").get("fresh").asBoolean());
        assertTrue(state.get("connection").get("stateAvailable").asBoolean());
        assertEquals("2026-09-10T08:00:00Z", state.get("connection").get("lastUpdateAt").asText());

        JsonNode rows = state.get("state").get("competition").get("classes").get(0)
                .get("snapshot").get("rows");
        assertEquals("201", rows.get(0).get(1).asText());
        assertEquals("CONNECTED", state.get("state").get("health").asText());

        JsonNode meta = state.get("startList");
        assertTrue(meta.get("present").asBoolean());
        assertEquals(1, meta.get("generation").asInt());
        assertEquals("IMPORT_CSV", meta.get("source").asText());
        assertEquals("Startliste.csv", meta.get("sourceLabel").asText());
        assertEquals(3, meta.get("entryCount").asInt());
        assertEquals(2, meta.get("classCount").asInt());
    }

    @Test
    void stateNeverCarriesTheStartListEntries() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(500));

        JsonNode state = json("/api/v1/state");
        assertEquals(500, state.get("startList").get("entryCount").asInt());
        assertFalse(state.get("startList").has("entries"));
        assertFalse(state.toString().contains("Nachname1"));
    }

    @Test
    void stateFollowsTheClockOfEveryNewPublication() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        assertEquals("10:00:01", json("/api/v1/state").get("time").get("competitionTime").asText());

        now.set(Instant.parse("2026-09-10T08:00:01Z"));
        publishClockAndResults("10:00:02");

        JsonNode second = json("/api/v1/state");
        assertEquals("10:00:02", second.get("time").get("competitionTime").asText());
        assertEquals("2026-09-10T08:00:01Z", second.get("time").get("clockChangedAt").asText());
    }

    @Test
    void stateAlwaysReportsTheCurrentResultSnapshotRatherThanAHistory() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01", "201");
        assertEquals("201", firstBib(json("/api/v1/state")));

        publishClockAndResults("10:00:02", "202");
        assertEquals("202", firstBib(json("/api/v1/state")));
    }

    // ---------------------------------------------------------------- /api/v1/startlist

    @Test
    void startListReturnsEveryEntryInPublishedOrder() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(4));

        JsonNode body = json("/api/v1/startlist");
        assertTrue(body.get("present").asBoolean());
        assertEquals(1, body.get("generation").asInt());
        assertEquals(4, body.get("entryCount").asInt());
        JsonNode list = body.get("entries");
        assertEquals(4, list.size());
        for (int index = 0; index < 4; index++) {
            assertEquals("Nachname" + index, list.get(index).get("lastName").asText());
        }
        JsonNode first = list.get(0);
        assertEquals("1", first.get("bib").asText());
        assertEquals("09:30:00", first.get("startTime").asText());
        assertEquals("Verein", first.get("club").asText());
        assertFalse(first.has("id"));
        assertFalse(first.has("participantId"));
    }

    /**
     * The reason this endpoint exists in this shape. The start list stays untouched for the whole
     * test while the competition time moves on; every answer has to carry the new time, not the one
     * that happened to be current when the list was published.
     */
    @Test
    void startListCarriesTheCurrentClockNotTheOneFromItsPublication() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(2));
        assertEquals("10:00:01", json("/api/v1/startlist").get("time").get("competitionTime").asText());

        now.set(Instant.parse("2026-09-10T08:00:01Z"));
        publishClockAndResults("10:00:02");
        JsonNode second = json("/api/v1/startlist");
        assertEquals("10:00:02", second.get("time").get("competitionTime").asText());
        assertEquals("2026-09-10T08:00:01Z", second.get("time").get("clockChangedAt").asText());
        assertEquals(1, second.get("generation").asInt());

        now.set(Instant.parse("2026-09-10T08:00:02Z"));
        publishClockAndResults("10:00:03");
        JsonNode third = json("/api/v1/startlist");
        assertEquals("10:00:03", third.get("time").get("competitionTime").asText());
        assertEquals("2026-09-10T08:00:02Z", third.get("time").get("clockChangedAt").asText());
        assertEquals(1, third.get("generation").asInt());
    }

    @Test
    void startListFollowsANewImport() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(2));
        assertEquals(2, json("/api/v1/startlist").get("entryCount").asInt());

        publishStartList(2, entries(5));
        JsonNode second = json("/api/v1/startlist");
        assertEquals(2, second.get("generation").asInt());
        assertEquals(5, second.get("entryCount").asInt());
    }

    @Test
    void startListWithoutAnImportIsAnExplicitEmptyAnswer() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");

        JsonNode body = json("/api/v1/startlist");
        assertFalse(body.get("present").asBoolean());
        assertEquals(0, body.get("generation").asInt());
        assertEquals(0, body.get("entryCount").asInt());
        assertEquals(0, body.get("classCount").asInt());
        assertEquals(0, body.get("entries").size());
        assertEquals("10:00:01", body.get("time").get("competitionTime").asText());

        // The bridge has to fill source and label even when stating "no start list"; that filler
        // must not reach a consumer as if it described a real stock.
        assertTrue(body.get("source").isNull());
        assertTrue(body.get("sourceLabel").isNull());
        JsonNode meta = json("/api/v1/state").get("startList");
        assertTrue(meta.get("source").isNull());
        assertTrue(meta.get("sourceLabel").isNull());
    }

    @Test
    void startListHandlesARealSizedList() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(1999));

        JsonNode body = json("/api/v1/startlist");
        assertEquals(1999, body.get("entryCount").asInt());
        assertEquals(1999, body.get("entries").size());
        assertEquals(2, body.get("classCount").asInt());
        assertEquals("Nachname1998", body.get("entries").get(1998).get("lastName").asText());
        assertEquals(1999, json("/api/v1/state").get("startList").get("entryCount").asInt());
    }

    @Test
    void startListHandlesTheLargestObservedExport() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(3121));

        JsonNode body = json("/api/v1/startlist");
        assertEquals(3121, body.get("entryCount").asInt());
        assertEquals(3121, body.get("entries").size());
    }

    // ---------------------------------------------------------------- chain status

    @Test
    void aHealthyChainReportsConnectedOnBothEndpoints() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(2));

        for (String path : List.of("/api/v1/state", "/api/v1/startlist")) {
            JsonNode connection = json(path).get("connection");
            assertEquals("CONNECTED", connection.get("status").asText(), path);
            assertEquals("CONNECTED", connection.get("winlaufen").asText(), path);
            assertEquals("CONNECTED", connection.get("bridge").asText(), path);
            assertTrue(connection.get("fresh").asBoolean(), path);
        }
    }

    @Test
    void aDisconnectedWinLaufenKeepsTheDataAndSaysSo() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:23:17");
        publishStartList(1, entries(2));

        publish(SourceHealth.DISCONNECTED, "10:23:17");

        JsonNode state = json("/api/v1/state");
        assertEquals("WINLAUFEN_DISCONNECTED", state.get("connection").get("status").asText());
        assertEquals("DISCONNECTED", state.get("connection").get("winlaufen").asText());
        assertEquals("CONNECTED", state.get("connection").get("bridge").asText());
        assertFalse(state.get("connection").get("fresh").asBoolean());

        assertEquals("10:23:17", state.get("time").get("competitionTime").asText());
        assertEquals("201", firstBib(state));
        assertEquals(2, json("/api/v1/startlist").get("entries").size());
    }

    @Test
    void aStaleSourceIsReportedAsStaleRatherThanDisconnected() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");

        publish(SourceHealth.STALE, "10:00:01");

        JsonNode connection = json("/api/v1/state").get("connection");
        assertEquals("STALE", connection.get("status").asText());
        assertEquals("STALE", connection.get("winlaufen").asText());
        assertEquals("CONNECTED", connection.get("bridge").asText());
        assertFalse(connection.get("fresh").asBoolean());
    }

    /**
     * A vanished bridge and a vanished WinLaufen look identical in the browser-facing health — both
     * end up {@code DISCONNECTED}. A consumer diagnosing the chain has to be able to tell them
     * apart, which is what the separate bridge link is for.
     */
    @Test
    void aVanishedBridgeIsDistinguishableFromAVanishedWinLaufen() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:23:17");
        publishStartList(1, entries(2));

        states.ingestDisconnected();

        for (String path : List.of("/api/v1/state", "/api/v1/startlist")) {
            JsonNode connection = json(path).get("connection");
            assertEquals("BRIDGE_DISCONNECTED", connection.get("status").asText(), path);
            assertEquals("DISCONNECTED", connection.get("bridge").asText(), path);
            assertFalse(connection.get("fresh").asBoolean(), path);
            assertTrue(connection.get("stateAvailable").asBoolean(), path);
        }
        assertNotEquals("WINLAUFEN_DISCONNECTED",
                json("/api/v1/state").get("connection").get("status").asText());

        assertEquals("10:23:17", json("/api/v1/state").get("time").get("competitionTime").asText());
        assertEquals(2, json("/api/v1/startlist").get("entries").size());
    }

    @Test
    void aLiveServerWithoutAnyBridgeInventsNoCompetitionTime() throws Exception {
        for (String path : List.of("/api/v1/state", "/api/v1/startlist")) {
            JsonNode body = json(path);
            assertTrue(body.get("time").get("competitionTime").isNull(), path);
            assertTrue(body.get("time").get("clockChangedAt").isNull(), path);
            assertEquals("NO_STATE", body.get("connection").get("status").asText(), path);
            assertFalse(body.get("connection").get("fresh").asBoolean(), path);
            assertFalse(body.get("connection").get("stateAvailable").asBoolean(), path);
            assertTrue(body.get("connection").get("lastUpdateAt").isNull(), path);
        }
        assertTrue(json("/api/v1/state").get("streamId").isNull());
    }

    @Test
    void aReconnectMakesTheChainHealthyAgain() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        states.ingestDisconnected();
        assertEquals("BRIDGE_DISCONNECTED",
                json("/api/v1/state").get("connection").get("status").asText());

        states.ingestConnected();
        now.set(Instant.parse("2026-09-10T08:05:00Z"));
        publishClockAndResults("10:05:00");

        JsonNode state = json("/api/v1/state");
        assertEquals("CONNECTED", state.get("connection").get("status").asText());
        assertEquals("10:05:00", state.get("time").get("competitionTime").asText());
        assertEquals("2026-09-10T08:05:00Z", state.get("time").get("clockChangedAt").asText());
    }

    @Test
    void bothEndpointsAgreeOnConnectionAndClock() throws Exception {
        states.ingestConnected();
        publishClockAndResults("11:11:11");
        publishStartList(1, entries(2));

        JsonNode state = json("/api/v1/state");
        JsonNode startList = json("/api/v1/startlist");
        assertEquals(state.get("time"), startList.get("time"));
        assertEquals(state.get("time").get("clockChangedAt"), startList.get("time").get("clockChangedAt"));
        assertEquals(state.get("connection"), startList.get("connection"));
        assertEquals(state.get("streamId"), startList.get("streamId"));
        assertEquals(state.get("startList").get("generation"), startList.get("generation"));
    }

    /**
     * The trap this guards against: WinLaufen goes quiet, the clock freezes, and minutes later an
     * unrelated publication — a presentation change — makes the frozen clock look freshly observed.
     */
    @Test
    void aRepeatedClockKeepsItsOriginalChangeTime() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");
        assertEquals("2026-09-10T08:00:00Z", json("/api/v1/state").get("time").get("clockChangedAt").asText());

        now.set(Instant.parse("2026-09-10T08:05:00Z"));
        publish(SourceHealth.DISCONNECTED, "10:00:01");

        JsonNode frozen = json("/api/v1/state");
        assertEquals("10:00:01", frozen.get("time").get("competitionTime").asText());
        assertEquals("2026-09-10T08:00:00Z", frozen.get("time").get("clockChangedAt").asText());
        assertEquals("2026-09-10T08:00:00Z", json("/api/v1/startlist").get("time").get("clockChangedAt").asText());
        // The publication itself did happen, and lastUpdateAt is allowed to say so.
        assertEquals("2026-09-10T08:05:00Z", frozen.get("connection").get("lastUpdateAt").asText());

        now.set(Instant.parse("2026-09-10T08:06:00Z"));
        publishClockAndResults("10:06:00");
        assertEquals("2026-09-10T08:06:00Z", json("/api/v1/state").get("time").get("clockChangedAt").asText());
    }

    /**
     * A bridge reports health and presentation as soon as it starts, long before WinLaufen sends a
     * clock telegram. That is a real state without a competition time, not "no state".
     */
    @Test
    void aStateWithoutAClockYetIsAvailableButHasNoTime() throws Exception {
        states.ingestConnected();
        states.accept(new SnapshotEnvelope("local", STREAM, ++revision,
                new CanonicalState(SourceHealth.DISCONNECTED, null, null, null, null),
                PresentationConfig.defaults()));

        JsonNode state = json("/api/v1/state");
        assertTrue(state.get("time").get("competitionTime").isNull());
        assertTrue(state.get("time").get("clockChangedAt").isNull());
        assertTrue(state.get("connection").get("stateAvailable").asBoolean());
        assertEquals("WINLAUFEN_DISCONNECTED", state.get("connection").get("status").asText());
    }

    /**
     * A standing competition time is not a broken chain. WinLaufen may legitimately send the same
     * value again — the protocol says identical values are accepted unchanged and every telegram
     * renews liveness — so a consumer must not read a standing clock as a stale source.
     */
    @Test
    void aStandingClockOnAHealthySourceStaysConnectedAndKeepsUpdating() throws Exception {
        states.ingestConnected();
        publishClockAndResults("00:42:17");
        assertEquals("2026-09-10T08:00:00Z", json("/api/v1/state").get("time").get("clockChangedAt").asText());

        for (int second = 1; second <= 30; second++) {
            now.set(Instant.parse("2026-09-10T08:00:00Z").plusSeconds(second));
            publishClockAndResults("00:42:17");
        }

        JsonNode state = json("/api/v1/state");
        assertEquals("00:42:17", state.get("time").get("competitionTime").asText());
        assertEquals("CONNECTED", state.get("connection").get("status").asText());
        assertTrue(state.get("connection").get("fresh").asBoolean());

        // The value has not changed for 30 s, and the field says exactly that and nothing more.
        assertEquals("2026-09-10T08:00:00Z", state.get("time").get("clockChangedAt").asText());
        // Data is demonstrably still arriving, which is what lastUpdateAt is for.
        assertEquals("2026-09-10T08:00:30Z", state.get("connection").get("lastUpdateAt").asText());
        assertEquals(state.get("connection"), json("/api/v1/startlist").get("connection"));
    }

    /**
     * The opposite trap: an organiser changing a viewer setting is not new source data, so it must
     * not make a long-frozen competition time look as if it had just moved.
     */
    @Test
    void aPresentationChangeDoesNotTouchTheClockTimestamp() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");

        now.set(Instant.parse("2026-09-10T08:05:00Z"));
        states.accept(new SnapshotEnvelope("local", STREAM, ++revision,
                new CanonicalState(SourceHealth.CONNECTED, "10:00:01", null, null, null),
                new PresentationConfig(true, true, true, true, true)));

        JsonNode state = json("/api/v1/state");
        assertEquals("2026-09-10T08:00:00Z", state.get("time").get("clockChangedAt").asText());
        assertTrue(state.get("presentation").get("showNation").asBoolean());
        assertEquals("2026-09-10T08:05:00Z", state.get("connection").get("lastUpdateAt").asText());
    }

    @Test
    void aLostBridgeFreezesBothTimestampsAtTheirLastRealValues() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");

        now.set(Instant.parse("2026-09-10T08:09:00Z"));
        states.ingestDisconnected();

        JsonNode state = json("/api/v1/state");
        assertEquals("2026-09-10T08:00:00Z", state.get("time").get("clockChangedAt").asText());
        // Losing the link is not something the bridge published, so it is not an update either.
        assertEquals("2026-09-10T08:00:00Z", state.get("connection").get("lastUpdateAt").asText());
    }

    // ---------------------------------------------------------------- Zeitmodell

    @Test
    void bothMeasuringPointsAppearSideBySideWithTheirOwnReference() throws Exception {
        states.ingestConnected();
        bridgeNow.set(Instant.parse("2026-09-10T12:30:00.100Z"));
        now.set(Instant.parse("2026-09-10T12:30:00.150Z"));
        publishClockAndResults("14:31:40");

        JsonNode time = json("/api/v1/state").get("time");
        assertEquals("14:31:40", time.get("competitionTime").asText());
        assertEquals("Europe/Berlin", time.get("competitionTimeZone").asText());
        assertEquals(1, time.get("clockSampleRevision").asInt());

        JsonNode bridge = time.get("bridge");
        assertEquals("2026-09-10T12:30:00.100Z", bridge.get("systemTimeAtReceipt").asText());
        assertEquals(99_900, bridge.get("competitionMinusReferenceMs").asLong());
        assertEquals("UNVERIFIED", bridge.get("referenceStatus").asText());
        assertEquals("SYSTEM_CLOCK", bridge.get("referenceSource").asText());

        JsonNode liveServer = time.get("liveServer");
        assertEquals("2026-09-10T12:30:00.150Z", liveServer.get("systemTimeAtReceipt").asText());
        assertEquals(99_850, liveServer.get("competitionMinusReferenceMs").asLong());
        assertEquals("UNVERIFIED", liveServer.get("referenceStatus").asText());

        // Two measurements, no verdict: nothing here picks one or blends them.
        assertFalse(time.has("effectiveOffset"));
        assertFalse(time.has("selectedOffset"));
        assertFalse(time.has("correctedCompetitionTime"));
        assertFalse(time.has("bestReference"));
        assertNotEquals(bridge.get("competitionMinusReferenceMs"),
                liveServer.get("competitionMinusReferenceMs"));
    }

    /**
     * Machines that are simply wrong. Both readings are off from world time by about 100 s, both
     * are reported, and the status says the accuracy is unknown — which is the honest answer, not a
     * fault.
     */
    @Test
    void wrongSystemClocksAreReportedAsMeasurementsNotAsErrors() throws Exception {
        states.ingestConnected();
        bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        now.set(Instant.parse("2026-09-10T12:30:00.050Z"));
        publishClockAndResults("14:31:40");

        JsonNode time = json("/api/v1/state").get("time");
        assertEquals(100_000, time.get("bridge").get("competitionMinusReferenceMs").asLong());
        assertEquals(99_950, time.get("liveServer").get("competitionMinusReferenceMs").asLong());
        assertEquals("UNVERIFIED", time.get("bridge").get("referenceStatus").asText());
        assertEquals("UNVERIFIED", time.get("liveServer").get("referenceStatus").asText());
        // The chain itself is perfectly healthy; reference quality and connection are separate.
        assertEquals("CONNECTED", json("/api/v1/state").get("connection").get("status").asText());
    }

    /**
     * The acceptance case for the whole model: WinLaufen keeps sending, the competition time does
     * not move. The sample revision and both measuring points must keep advancing while
     * {@code clockChangedAt} stands still.
     */
    @Test
    void aStandingClockKeepsProducingSamplesAtBothMeasuringPoints() throws Exception {
        states.ingestConnected();
        bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        now.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        publishClockAndResults("10:42:17");

        JsonNode first = json("/api/v1/state").get("time");
        assertEquals(1, first.get("clockSampleRevision").asInt());

        for (int second = 1; second <= 3; second++) {
            bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z").plusSeconds(second));
            now.set(Instant.parse("2026-09-10T12:30:00.000Z").plusSeconds(second));
            publishClockAndResults("10:42:17");
        }

        JsonNode time = json("/api/v1/state").get("time");
        assertEquals("10:42:17", time.get("competitionTime").asText());
        assertEquals(4, time.get("clockSampleRevision").asInt());
        assertEquals("2026-09-10T12:30:03Z", time.get("bridge").get("systemTimeAtReceipt").asText());
        assertEquals("2026-09-10T12:30:03Z", time.get("liveServer").get("systemTimeAtReceipt").asText());
        // The value never changed, so this one must not move.
        assertEquals(first.get("clockChangedAt"), time.get("clockChangedAt"));
        assertEquals("CONNECTED", json("/api/v1/state").get("connection").get("status").asText());
    }

    @Test
    void aPresentationChangeMovesNoMeasurement() throws Exception {
        states.ingestConnected();
        bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        publishClockAndResults("10:42:17");
        JsonNode before = json("/api/v1/state").get("time");

        bridgeNow.set(Instant.parse("2026-09-10T12:35:00.000Z"));
        now.set(Instant.parse("2026-09-10T12:35:00.000Z"));
        publishPresentationChange(new PresentationConfig(true, true, true, true, true));

        JsonNode after = json("/api/v1/state").get("time");
        assertEquals(before.get("clockSampleRevision"), after.get("clockSampleRevision"));
        assertEquals(before.get("bridge"), after.get("bridge"));
        assertEquals(before.get("liveServer"), after.get("liveServer"));
        assertEquals(before.get("clockChangedAt"), after.get("clockChangedAt"));
        assertTrue(json("/api/v1/state").get("presentation").get("showNation").asBoolean());
    }

    @Test
    void aNewSampleWithAnUnchangedValueStillMovesTheLiveServerMeasurement() throws Exception {
        states.ingestConnected();
        bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        now.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        publishClockAndResults("10:42:17");

        bridgeNow.set(Instant.parse("2026-09-10T12:30:01.000Z"));
        now.set(Instant.parse("2026-09-10T12:30:01.000Z"));
        publishClockAndResults("10:42:17");

        JsonNode time = json("/api/v1/state").get("time");
        assertEquals(2, time.get("clockSampleRevision").asInt());
        assertEquals("2026-09-10T12:30:01Z", time.get("liveServer").get("systemTimeAtReceipt").asText());
    }

    @Test
    void aLostWinLaufenKeepsTheLastSampleVisible() throws Exception {
        states.ingestConnected();
        bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        publishClockAndResults("10:42:17");

        publish(SourceHealth.DISCONNECTED, "10:42:17");

        JsonNode body = json("/api/v1/state");
        assertEquals("WINLAUFEN_DISCONNECTED", body.get("connection").get("status").asText());
        assertEquals(1, body.get("time").get("clockSampleRevision").asInt());
        assertEquals("2026-09-10T12:30:00Z",
                body.get("time").get("bridge").get("systemTimeAtReceipt").asText());
    }

    @Test
    void aLostBridgeKeepsTheLastSampleVisible() throws Exception {
        states.ingestConnected();
        bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        publishClockAndResults("10:42:17");

        states.ingestDisconnected();

        JsonNode body = json("/api/v1/state");
        assertEquals("BRIDGE_DISCONNECTED", body.get("connection").get("status").asText());
        assertEquals(1, body.get("time").get("clockSampleRevision").asInt());
        assertEquals("2026-09-10T12:30:00Z",
                body.get("time").get("bridge").get("systemTimeAtReceipt").asText());
    }

    @Test
    void withoutAnyStateNoMeasurementIsInvented() throws Exception {
        for (String path : List.of("/api/v1/state", "/api/v1/startlist")) {
            JsonNode time = json(path).get("time");
            assertTrue(time.get("competitionTime").isNull(), path);
            assertTrue(time.get("competitionTimeZone").isNull(), path);
            assertTrue(time.get("clockSampleRevision").isNull(), path);
            assertTrue(time.get("clockChangedAt").isNull(), path);
            assertTrue(time.get("bridge").isNull(), path);
            assertTrue(time.get("liveServer").isNull(), path);
            // Only this one is always answerable: it is this server's own reading, right now.
            assertEquals("2026-09-10T09:30:00Z", time.get("apiGeneratedAt").asText(), path);
        }
    }

    /**
     * The start list is unchanged for the whole test while three telegrams arrive. Every answer has
     * to carry the new measurements, not the ones current when the list was published.
     */
    @Test
    void startListAnswersCarryEveryNewMeasurement() throws Exception {
        states.ingestConnected();
        bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z"));
        publishClockAndResults("10:00:01");
        publishStartList(1, entries(3));

        for (int second = 1; second <= 3; second++) {
            bridgeNow.set(Instant.parse("2026-09-10T12:30:00.000Z").plusSeconds(second));
            now.set(Instant.parse("2026-09-10T12:30:00.000Z").plusSeconds(second));
            publishClockAndResults("10:00:0" + (1 + second));

            JsonNode body = json("/api/v1/startlist");
            assertEquals(1, body.get("generation").asInt());
            assertEquals(3, body.get("entries").size());
            assertEquals(1 + second, body.get("time").get("clockSampleRevision").asInt());
            assertEquals("10:00:0" + (1 + second), body.get("time").get("competitionTime").asText());
            assertEquals("2026-09-10T12:30:0" + second + "Z",
                    body.get("time").get("bridge").get("systemTimeAtReceipt").asText());
        }
    }

    @Test
    void bothEndpointsCarryTheIdenticalTimeBlock() throws Exception {
        states.ingestConnected();
        publishClockAndResults("11:11:11");
        publishStartList(1, entries(2));

        assertEquals(json("/api/v1/state").get("time"), json("/api/v1/startlist").get("time"));
    }

    // ---------------------------------------------------------------- transport

    @Test
    void answersCarryingARunningClockAreNotCacheable() throws Exception {
        for (String path : List.of("/api/v1/state", "/api/v1/startlist")) {
            HttpResponse<String> response = get(path);
            assertEquals(200, response.statusCode(), path);
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow(), path);
            assertEquals("application/json; charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow(), path);
            assertTrue(response.headers().firstValue("ETag").isEmpty(), path);
        }
    }

    @Test
    void theApiIsReadOnly() throws Exception {
        HttpResponse<String> posted = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base() + "/api/v1/startlist"))
                        .POST(HttpRequest.BodyPublishers.ofString("x")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, posted.statusCode());
    }

    /** The viewer bootstraps from this endpoint; the fields it reads must keep working. */
    @Test
    void stateStaysCompatibleWithTheWebViewer() throws Exception {
        states.ingestConnected();
        publishClockAndResults("10:00:01");

        JsonNode state = json("/api/v1/state");
        assertEquals("snapshot", state.get("type").asText());
        assertTrue(state.get("publicationRevision").isNumber());
        assertTrue(state.get("state").get("competition").isObject());
        assertTrue(state.get("presentation").get("showClub").isBoolean());
    }

    // ---------------------------------------------------------------- helpers

    /** A clock telegram: the bridge measures, so a new sample travels with the snapshot. */
    private void publishClockAndResults(String clock) {
        publishClockAndResults(clock, "201");
    }

    private void publishClockAndResults(String clock, String bib) {
        publishSnapshot(SourceHealth.CONNECTED, clock, newSample(clock), competition(bib),
                PresentationConfig.defaults());
    }

    /**
     * A snapshot that is not a clock telegram — a health change, a message, a result block. It
     * carries the sample already in force, exactly as the bridge would.
     */
    private void publish(SourceHealth health, String clock) {
        publishSnapshot(health, clock, currentSample, null, PresentationConfig.defaults());
    }

    private void publishPresentationChange(PresentationConfig presentation) {
        publishSnapshot(SourceHealth.CONNECTED, currentSample.competitionTime(), currentSample,
                null, presentation);
    }

    private void publishSnapshot(SourceHealth health, String clock, ClockSample sample,
                                 Competition competition, PresentationConfig presentation) {
        states.accept(new SnapshotEnvelope("local", STREAM, ++revision,
                new CanonicalState(health, clock, competition,
                        competition == null ? null : new CurrentFinish(0, 0, revision), null, sample),
                presentation));
    }

    private Competition competition(String bib) {
        ClassSnapshot snapshot = new ClassSnapshot(revision + 1, List.of("Rang", "StNr"),
                List.of(List.of("1", bib)));
        return new Competition("Standardwettkampf", 1, 1, 0, 0,
                List.of(new CompetitionClass(0, "H30", 1, snapshot)));
    }

    /** What the bridge would have measured for this telegram, with its own clock. */
    private ClockSample newSample(String clock) {
        Instant at = bridgeNow.get();
        currentSample = new ClockSample(++sampleRevision, clock, ZONE, at.toString(),
                CompetitionTimeOffset.differenceMillis(clock, ZONE, at),
                TimeReferenceStatus.UNVERIFIED, TimeReferenceSource.SYSTEM_CLOCK);
        return currentSample;
    }

    private void publishStartList(long generation, List<StartListRow> rows) {
        startLists.accept(new StartListEnvelope("local", STREAM, generation, "IMPORT_CSV",
                "Startliste.csv", rows));
    }

    private static List<StartListRow> entries(int count) {
        List<StartListRow> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rows.add(new StartListRow(String.valueOf(index + 1), index % 2 == 0 ? "H30" : "D30",
                    "09:30:00", "Nachname" + index, "Vorname" + index, "Verein", "Verband",
                    "Strecke", "1990", "M", "GER"));
        }
        return rows;
    }

    private static String firstBib(JsonNode state) {
        return state.get("state").get("competition").get("classes").get(0)
                .get("snapshot").get("rows").get(0).get(1).asText();
    }

    private JsonNode json(String path) throws Exception {
        HttpResponse<String> response = get(path);
        assertEquals(200, response.statusCode(), path);
        return MAPPER.readTree(response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String base() {
        return "http://127.0.0.1:" + server.port();
    }
}
