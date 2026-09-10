package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.startlist.StartListFormatException;
import de.winlaufen.web.bridge.startlist.StartListParser;
import de.winlaufen.web.bridge.startlist.StartListStore;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.AckEnvelope;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.StartListEnvelope;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the bridge publishes its start list, and just as importantly when it does not.
 *
 * <p>The tests run against real WebSocket connections, because the rules that matter here live in
 * the transport: a fresh connection has to carry the current start list again, and the constant
 * stream of clock telegrams must not carry it at all.
 */
class StartListPublicationTest {

    private static final String PROLOGUE = """
            StNr,Klasse,Name,Vorname,Startzeit
            12,U16,MÜLLER,Anna,10:00:15
            13,U16,MEIER,Ben,10:00:30
            14,U18,SCHMIDT,Cara,10:01:00
            """;

    private static final String HEAT = """
            StNr,Klasse,Name,Vorname,Startzeit
            12,U20,VOBORNÍKOVÁ,Tereza,14:00:00
            """;

    @TempDir
    Path temp;

    private StartListStore startLists() throws Exception {
        return StartListStore.open(temp.resolve("startlist.properties"));
    }

    private static void importInto(StartListStore store, String csv) throws Exception {
        store.replace(StartListParser.parse(csv.getBytes(StandardCharsets.UTF_8), "Startliste.csv"));
    }

    // --- Initial sync --------------------------------------------------------------------------

    @Test
    void aPersistedStartListIsPublishedWhenTheTargetConnects() throws Exception {
        StartListStore startLists = startLists();
        importInto(startLists, PROLOGUE);
        CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);

        try (Fake target = new Fake();
             OutputTargetManager manager = new OutputTargetManager(
                     List.of(target("one", target.port())), "stream", state, startLists)) {
            manager.start();

            await(() -> !target.startLists.isEmpty());
            StartListEnvelope published = target.startLists.getFirst();
            assertEquals(1, published.generation());
            assertEquals(3, published.entries().size());
            assertEquals("Startliste.csv", published.sourceLabel());
            assertEquals("IMPORT_CSV", published.source());
            assertEquals("stream", published.streamId());
            assertEquals(List.of("12", "13", "14"),
                    published.entries().stream().map(row -> row.bib()).toList());
        }
    }

    /**
     * A bridge without a start list says so. Otherwise a live server that still holds an older
     * list from a previous bridge would keep showing participants its source no longer has.
     */
    @Test
    void aBridgeWithoutAStartListPublishesItsAbsence() throws Exception {
        StartListStore startLists = startLists();
        CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);

        try (Fake target = new Fake();
             OutputTargetManager manager = new OutputTargetManager(
                     List.of(target("one", target.port())), "stream", state, startLists)) {
            manager.start();

            await(() -> !target.startLists.isEmpty());
            assertEquals(0, target.startLists.getFirst().generation());
            assertTrue(target.startLists.getFirst().entries().isEmpty());
        }
    }

    // --- Import --------------------------------------------------------------------------------

    @Test
    void anImportOnAnOpenConnectionIsPublishedImmediately() throws Exception {
        StartListStore startLists = startLists();
        importInto(startLists, PROLOGUE);
        CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);

        try (Fake target = new Fake();
             OutputTargetManager manager = new OutputTargetManager(
                     List.of(target("one", target.port())), "stream", state, startLists)) {
            manager.start();
            await(() -> !target.startLists.isEmpty());

            importInto(startLists, HEAT);

            await(() -> target.startLists.size() >= 2);
            StartListEnvelope second = target.startLists.get(1);
            assertEquals(2, second.generation());
            assertEquals(1, second.entries().size());
            assertEquals("U20", second.entries().getFirst().className());
        }
    }

    @Test
    void aRejectedImportPublishesNothing() throws Exception {
        StartListStore startLists = startLists();
        importInto(startLists, PROLOGUE);
        CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);

        try (Fake target = new Fake();
             OutputTargetManager manager = new OutputTargetManager(
                     List.of(target("one", target.port())), "stream", state, startLists)) {
            manager.start();
            await(() -> !target.startLists.isEmpty());

            assertThrows(StartListFormatException.class, () -> importInto(startLists,
                    "StNr,Klasse\n12,U16\n12,U16\n"));

            Thread.sleep(300);
            assertEquals(1, target.startLists.size(),
                    "a rejected import must not reach a live server at all");
            assertEquals(1, target.startLists.getFirst().generation());
        }
    }

    // --- No spam -------------------------------------------------------------------------------

    /**
     * The decisive property of the whole design: a clock telegram raises the canonical revision
     * roughly once a second, and none of them may carry the participant list again.
     */
    @Test
    void clockAndResultUpdatesNeverResendTheStartList() throws Exception {
        StartListStore startLists = startLists();
        importInto(startLists, PROLOGUE);
        CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);

        try (Fake target = new Fake();
             OutputTargetManager manager = new OutputTargetManager(
                     List.of(target("one", target.port())), "stream", state, startLists)) {
            manager.start();
            await(() -> !target.startLists.isEmpty());

            for (int second = 0; second < 12; second++) {
                state.clock("10:00:" + String.format("%02d", second));
                // A real clock arrives about once a second; the pause lets the sender thread
                // deliver them individually instead of coalescing everything into one snapshot.
                Thread.sleep(30);
            }
            // Latest-only may skip intermediate revisions, but the newest one always arrives.
            await(() -> target.snapshots.contains(12L));

            assertTrue(target.snapshots.size() >= 2,
                    "the state itself must keep flowing, got " + target.snapshots.size());
            assertEquals(1, target.startLists.size(),
                    "twelve state updates carried the start list " + target.startLists.size()
                            + " times; it belongs to the import, not to the clock");
        }
    }

    // --- Reconnect -----------------------------------------------------------------------------

    @Test
    void aReconnectingTargetReceivesTheCurrentStartListAgain() throws Exception {
        StartListStore startLists = startLists();
        importInto(startLists, PROLOGUE);
        importInto(startLists, HEAT);
        CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);
        int port = freePort();

        Fake first = new Fake(port);
        try (OutputTargetManager manager = new OutputTargetManager(
                List.of(target("one", port)), "stream", state, startLists)) {
            manager.start();
            await(() -> !first.startLists.isEmpty());
            assertEquals(2, first.startLists.getFirst().generation());

            first.close();
            // The live server comes back on the same port, as a restarted service would.
            try (Fake restarted = new Fake(port)) {
                state.clock("10:00:00");
                await(() -> !restarted.startLists.isEmpty());

                StartListEnvelope again = restarted.startLists.getFirst();
                assertEquals(2, again.generation(), "no new import is needed after a reconnect");
                assertEquals(1, again.entries().size());
                assertEquals("Tereza", again.entries().getFirst().firstName());
            }
        }
    }

    // --- Fan-out -------------------------------------------------------------------------------

    @Test
    void everyConfiguredTargetGetsItsOwnStartListSync() throws Exception {
        StartListStore startLists = startLists();
        importInto(startLists, PROLOGUE);
        CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);

        try (Fake one = new Fake(); Fake two = new Fake(); Fake three = new Fake();
             OutputTargetManager manager = new OutputTargetManager(
                     List.of(target("one", one.port()), target("two", two.port()),
                             target("three", three.port())), "stream", state, startLists)) {
            manager.start();

            await(() -> !one.startLists.isEmpty() && !two.startLists.isEmpty()
                    && !three.startLists.isEmpty());
            for (Fake target : List.of(one, two, three)) {
                assertEquals(1, target.startLists.getFirst().generation());
                assertEquals(3, target.startLists.getFirst().entries().size());
            }

            importInto(startLists, HEAT);

            await(() -> one.startLists.size() >= 2 && two.startLists.size() >= 2
                    && three.startLists.size() >= 2);
            for (Fake target : List.of(one, two, three)) {
                assertEquals(2, target.startLists.get(1).generation());
            }
        }
    }

    @Test
    void aTargetAddedLaterStillReceivesTheCurrentStartList() throws Exception {
        StartListStore startLists = startLists();
        importInto(startLists, PROLOGUE);
        CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);

        try (Fake one = new Fake(); Fake later = new Fake();
             OutputTargetManager manager = new OutputTargetManager(
                     List.of(target("one", one.port())), "stream", state, startLists)) {
            manager.start();
            await(() -> !one.startLists.isEmpty());

            manager.reconfigure(List.of(target("one", one.port()), target("later", later.port())));

            await(() -> !later.startLists.isEmpty());
            assertEquals(1, later.startLists.getFirst().generation());
            assertEquals(3, later.startLists.getFirst().entries().size());
        }
    }

    // --- Helpers -------------------------------------------------------------------------------

    private static OutputTargetConfig target(String id, int port) {
        return new OutputTargetConfig(id, OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"), "local",
                "12345678");
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("condition not reached within 5 s");
    }

    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    /** A live server that records what it receives and routes by type, like the real one. */
    private static final class Fake extends WebSocketServer implements AutoCloseable {

        final List<StartListEnvelope> startLists = new CopyOnWriteArrayList<>();
        final List<Long> snapshots = new CopyOnWriteArrayList<>();
        private final CountDownLatch ready = new CountDownLatch(1);

        Fake() throws Exception {
            this(freePort());
        }

        Fake(int port) throws Exception {
            super(new InetSocketAddress("127.0.0.1", port));
            setReuseAddr(true);
            start();
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("fake live server did not start");
            }
        }

        int port() {
            return getPort();
        }

        @Override
        public void onOpen(WebSocket connection, ClientHandshake handshake) { }

        @Override
        public void onClose(WebSocket connection, int code, String reason, boolean remote) { }

        @Override
        public void onMessage(WebSocket connection, String text) {
            try {
                if (StartListEnvelope.TYPE.equals(ContractJson.typeOf(text))) {
                    startLists.add(ContractJson.readStartList(text));
                    return;
                }
                var snapshot = ContractJson.readSnapshot(text);
                snapshots.add(snapshot.sourceRevision());
                connection.send(ContractJson.ack(new AckEnvelope(snapshot.channelId(),
                        snapshot.streamId(), snapshot.sourceRevision())));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public void onError(WebSocket connection, Exception ex) { }

        @Override
        public void onStart() {
            ready.countDown();
        }

        @Override
        public void close() throws InterruptedException {
            stop(1_000);
        }
    }
}
