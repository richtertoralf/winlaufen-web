package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.source.winlaufen.WinLaufenProtocolReader;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.ContractJson;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SnapshotEnvelope;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Original protocol -> canonical history -> delayed real WebSocket delivery. */
class FinishHistoryOutputTest {
    static final String[] HEADERS = {"Rang", "StNr", "Laufzeit"};
    static final String[][][] TABLES = {
        {{"1", "12", "0:30:00,1"}},
        {{"1", "A12", "0:29:00,0"}, {"2", "12", "0:30:00,1"}},
        {{"1", "A12", "0:29:00,0"}, {"2", "0012", "0:29:30,0"}, {"3", "12", "0:30:00,1"}}
    };

    static byte[] burst() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            for (int i = 0; i < 3; i++) {
                for (Object field : new Object[]{"Standardwettkampf", 1, 1,
                        new String[]{"Testklasse"}, new int[]{0}, 0, 0, 0, i == 2 ? 1 : 0})
                    output.writeObject(field);
                for (String[] row : TABLES[i]) output.writeObject(row);
                output.writeObject("tabelle");
                output.writeObject(HEADERS);
                output.writeObject("ende");
            }
        }
        return bytes.toByteArray();
    }

    @Test void threeOriginalHintsSurviveCoalescedDelivery() throws Exception {
        var store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        var observed = new ArrayList<String>();
        var target = new Sink();
        target.start();
        assertTrue(target.ready.await(3, TimeUnit.SECONDS));
        var config = new OutputTargetConfig("test", OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:" + target.getPort() + "/bridge/v1/channels/local"),
                "local", "synthetic-secret");
        try (var adapter = new WebSocketOutputAdapter(config, "test-stream", store.get())) {
            store.addListener(value -> {
                var finish = value.state().currentFinish();
                observed.add(value.state().competition().classes().get(finish.classIndex())
                        .snapshot().rows().get(finish.rowIndex()).get(1));
                adapter.publish(value);
            });
            // Deterministic delivery pause: all three callbacks precede worker startup.
            // Equivalent to the permitted latest-slot overwrite while a target is unavailable.
            try (var input = new ObjectInputStream(new ByteArrayInputStream(burst()))) {
                var reader = new WinLaufenProtocolReader(input, ignored -> {}, store::result);
                long before = System.nanoTime();
                for (int i = 0; i < 3; i++) reader.readNext();
                assertTrue(System.nanoTime() - before < TimeUnit.SECONDS.toNanos(1));
            }
            assertEquals(List.of("12", "A12", "0012"), observed);
            assertEquals(3, store.get().sourceRevision());
            adapter.start();
            SnapshotEnvelope received = target.snapshots.poll(5, TimeUnit.SECONDS);
            assertNotNull(received);
            assertEquals(3, received.sourceRevision());
            assertEquals(List.of("12", "A12", "0012"), received.state().finishHistory().hints()
                    .stream().map(h -> h.row().get(1)).toList());
            assertEquals(1, received.state().currentFinish().rowIndex());
            assertEquals(3, received.state().competition().classes().getFirst().snapshot().rows().size());
            assertNull(target.snapshots.poll(200, TimeUnit.MILLISECONDS), "no replay of revisions 1 and 2");
        } finally {
            target.stop(1000);
        }
    }

    static class Sink extends WebSocketServer {
        final CountDownLatch ready = new CountDownLatch(1);
        final BlockingQueue<SnapshotEnvelope> snapshots = new LinkedBlockingQueue<>();
        Sink() { super(new InetSocketAddress("127.0.0.1", 0)); }
        public void onOpen(WebSocket socket, ClientHandshake handshake) {}
        public void onClose(WebSocket socket, int code, String reason, boolean remote) {}
        public void onError(WebSocket socket, Exception error) {}
        public void onStart() { ready.countDown(); }
        public void onMessage(WebSocket socket, String message) {
            try {
                if ("snapshot".equals(ContractJson.typeOf(message)))
                    snapshots.add(ContractJson.readSnapshot(message));
            } catch (Exception error) { throw new IllegalStateException(error); }
        }
    }
}
