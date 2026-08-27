package de.winlaufen.web.web;

import de.winlaufen.web.TestBlocks;
import de.winlaufen.web.json.JsonSyntax;
import de.winlaufen.web.state.StateStore;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveWebSocketServerTest {
    LiveWebSocketServer server;
    StateStore store;
    @BeforeEach void start() throws Exception { store = new StateStore(); server = new LiveWebSocketServer(0, store); server.start(); server.awaitStart(); }
    @AfterEach void stop() throws Exception { server.shutdown(); }

    @Test void sendsInitialSnapshotToMultipleClients() throws Exception {
        Collector one = connect("http://localhost:8080"); Collector two = connect("http://localhost:8080");
        String first = one.next();
        String second = two.next();
        JsonSyntax.parse(first);
        JsonSyntax.parse(second);
        assertTrue(first.contains("\"type\":\"snapshot\""));
        assertTrue(second.contains("\"revision\":0"));
        one.socket.abort(); two.socket.abort();
    }

    @Test void sendsValidClockAndClassSnapshotMessages() throws Exception {
        Collector client = connect("http://localhost:8080");
        JsonSyntax.parse(client.next());
        store.clock("12:34:56");
        String clock = client.next();
        JsonSyntax.parse(clock);
        assertTrue(clock.contains("\"type\":\"clock\""));
        store.result(TestBlocks.block(0, 0, List.of(List.of("1", "7")), List.of("Rang", "StNr")));
        String classSnapshot = client.next();
        JsonSyntax.parse(classSnapshot);
        assertTrue(classSnapshot.contains("\"type\":\"classSnapshot\""));
        client.socket.abort();
    }

    @Test void rejectsForeignOriginDuringHandshake() {
        assertThrows(Exception.class, () -> connect("http://evil.test:8080"));
    }

    @Test void olderQueuedRevisionIsRejectedAfterInitialSnapshot() {
        assertTrue(LiveWebSocketServer.revisionMayFollow(10, 10));
        assertTrue(LiveWebSocketServer.revisionMayFollow(10, 12));
        assertFalse(LiveWebSocketServer.revisionMayFollow(12, 11));
    }

    @Test void cleanShutdownAllowsImmediateRebindOnSamePort() throws Exception {
        assertTrue(server.isReuseAddr());
        int port = server.getPort();
        server.shutdown();
        server = new LiveWebSocketServer(port, new StateStore());
        server.start();
        server.awaitStart();
        assertEquals(port, server.getPort());
    }

    private Collector connect(String origin) throws Exception {
        Collector listener = new Collector();
        listener.socket = HttpClient.newHttpClient().newWebSocketBuilder().header("Origin", origin)
                .buildAsync(URI.create("ws://localhost:" + server.getPort()), listener).get(3, TimeUnit.SECONDS);
        return listener;
    }
    private static final class Collector implements WebSocket.Listener {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>(); WebSocket socket; StringBuilder text = new StringBuilder();
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) { messages.add(text.toString()); text = new StringBuilder(); }
            webSocket.request(1); return null;
        }
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        String next() throws InterruptedException {
            String value = messages.poll(3, TimeUnit.SECONDS);
            assertNotNull(value);
            return value;
        }
    }
}
