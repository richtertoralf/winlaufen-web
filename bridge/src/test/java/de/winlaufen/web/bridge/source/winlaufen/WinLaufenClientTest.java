package de.winlaufen.web.bridge.source.winlaufen;

import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Protocol integration against a real socket.
 *
 * <p>The listener runs on a port the operating system hands out, never on TCP 4444. On a real
 * Sprecher-PC that port belongs to WinLaufen, and a build there must not fail because the machine
 * is doing its job. The production default is unchanged and lives in the client.
 */
class WinLaufenClientTest {
    @Test
    void anEmptySourceNeverConnectsAndCanBeConfiguredLater() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(300);
            CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);
            try (WinLaufenClient client = new WinLaufenClient("", server.getLocalPort(), state)) {
                client.start();
                org.junit.jupiter.api.Assertions.assertThrows(java.net.SocketTimeoutException.class,
                        server::accept);
                assertEquals(SourceHealth.DISCONNECTED, state.get().state().sourceHealth());
                assertNull(state.get().state().clock());
                client.reconnectTo("localhost");
                server.setSoTimeout(2_000);
                try (var connection = server.accept()) {
                    assertTrue(connection.isConnected());
                }
            }
        }
    }

    @Test
    void disablingTheSourceRejectsAPreviouslySelectedHost() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(300);
            CanonicalStateStore state = new CanonicalStateStore(PresentationConfig.defaults(), null);
            try (WinLaufenClient client = new WinLaufenClient("localhost", server.getLocalPort(), state)) {
                // Reproduce the worker selecting a host just before the operator disables it.
                client.reconnectTo("");
                var consume = WinLaufenClient.class.getDeclaredMethod("consumeConnection", String.class);
                consume.setAccessible(true);
                consume.invoke(client, "localhost");
                assertThrows(java.net.SocketTimeoutException.class, server::accept);
                assertEquals(SourceHealth.DISCONNECTED, state.get().state().sourceHealth());
            }
        }
    }

    @Test @Timeout(10)
    void protocolDiagnosisIsStableWithinConnectionAndResetsOnReconnect() throws Exception {
        for (boolean legacyFirst : List.of(false, true)) {
            try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                server.setSoTimeout(2_000);
                var store = new CanonicalStateStore(PresentationConfig.defaults(), null);
                try (var client = new WinLaufenClient("localhost", server.getLocalPort(), store)) {
                    assertEquals(ProtocolVariant.UNKNOWN, client.protocolVariant());
                    client.start();
                    try (var socket = server.accept();
                         var output = new ObjectOutputStream(socket.getOutputStream())) {
                        assertEquals(ProtocolVariant.UNKNOWN, client.protocolVariant());
                        var expected = legacyFirst ? ProtocolVariant.LEGACY : ProtocolVariant.CURRENT;
                        for (int i = 0; i < 4; i++) {
                            String clock = "10:00:0" + i;
                            boolean legacy = i < 2 ? legacyFirst : !legacyFirst;
                            output.writeObject(legacy ? new java.util.Vector<>(List.of(clock)) : "Uhr" + clock);
                            // Message acts as a barrier after the diagnosis callback.
                            output.writeObject(new java.util.Vector<>(List.of("barrier" + i, "nachricht")));
                            output.flush();
                            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                            while (!("barrier" + i).equals(store.get().state().message())
                                    && System.nanoTime() < deadline) Thread.sleep(5);
                            assertEquals("barrier" + i, store.get().state().message());
                            assertEquals(clock, store.get().state().clock());
                            assertEquals(expected, client.protocolVariant());
                        }
                    }
                    try (var socket = server.accept();
                         var output = new ObjectOutputStream(socket.getOutputStream())) {
                        assertEquals(ProtocolVariant.UNKNOWN, client.protocolVariant());
                        output.writeObject(new java.util.Vector<>(List.of("fresh", "nachricht")));
                        output.flush();
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                        while (!"fresh".equals(store.get().state().message())
                                && System.nanoTime() < deadline) Thread.sleep(5);
                        assertEquals("fresh", store.get().state().message());
                        assertEquals(ProtocolVariant.UNKNOWN, client.protocolVariant());
                    }
                }
            }
        }
    }

    @Test @Timeout(10)
    void legacyResultsAndUnknownVectorsKeepConnectionButEofReconnects() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(2_000);
            var store = new CanonicalStateStore(PresentationConfig.defaults(), null);
            var updates = new java.util.concurrent.LinkedBlockingQueue<
                    de.winlaufen.web.bridge.state.CanonicalSnapshot>();
            store.addListener(updates::add);
            try (var client = new WinLaufenClient("localhost", server.getLocalPort(), store)) {
                client.start();
                try (var connection = server.accept();
                     var output = new ObjectOutputStream(connection.getOutputStream())) {
                    output.writeObject(new java.util.Vector<>(List.of("10:29:27")));
                    output.flush();
                    assertEquals("10:29:27", updates.poll(2, TimeUnit.SECONDS).state().clock());
                    output.writeObject(LegacyProtocolFixture.result());
                    output.flush();
                    // The separate terminator may arrive after the client's 500ms read timeout.
                    Thread.sleep(700);
                    assertNull(store.get().state().competition());
                    output.writeObject("ende");
                    output.flush();
                    var result = updates.poll(2, TimeUnit.SECONDS);
                    assertNotNull(result);
                    assertEquals(List.of(LegacyProtocolFixture.ROW),
                            result.state().competition().classes().get(10).snapshot().rows());
                    output.writeObject(new java.util.Vector<>(List.of("unbekannt", 123)));
                    output.writeObject(new java.util.Vector<>(List.of("10:29:28")));
                    output.writeObject(new java.util.Vector<>(List.of("10:29:29")));
                    output.flush();
                    for (String time : List.of("10:29:28", "10:29:29")) {
                        var update = updates.poll(2, TimeUnit.SECONDS);
                        assertNotNull(update);
                        assertEquals(time, update.state().clock());
                        assertEquals(SourceHealth.CONNECTED, update.state().sourceHealth());
                        assertEquals(result.state().competition(), update.state().competition());
                    }
                    connection.setSoTimeout(200);
                    assertThrows(SocketTimeoutException.class, () -> connection.getInputStream().read());
                    server.setSoTimeout(200);
                    assertThrows(SocketTimeoutException.class, server::accept);
                }
                // Server closes the established socket: this real EOF must still reconnect.
                server.setSoTimeout(2_000);
                try (var next = server.accept();
                     var output = new ObjectOutputStream(next.getOutputStream())) {
                    var disconnected = updates.poll(2, TimeUnit.SECONDS);
                    assertNotNull(disconnected);
                    assertEquals(SourceHealth.DISCONNECTED, disconnected.state().sourceHealth());
                    output.writeObject(new java.util.Vector<>(List.of("10:29:30")));
                    output.flush();
                    var recovered = updates.poll(2, TimeUnit.SECONDS);
                    assertNotNull(recovered);
                    assertEquals("10:29:30", recovered.state().clock());
                    assertEquals(SourceHealth.CONNECTED, recovered.state().sourceHealth());
                }
            }
        }
    }

    @Test @Timeout(15)
    void legacyClocksKeepOneConnectionAliveBeyondInitialHeartbeatDeadline() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(2_000);
            CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
            var updates = new java.util.concurrent.LinkedBlockingQueue<String>();
            List<SourceHealth> health = new CopyOnWriteArrayList<>();
            store.addListener(event -> {
                health.add(event.state().sourceHealth());
                if (event.state().clock() != null) updates.add(event.state().clock());
            });
            try (WinLaufenClient client = new WinLaufenClient("localhost", server.getLocalPort(), store)) {
                client.start();
                try (var connection = server.accept();
                     var output = new ObjectOutputStream(connection.getOutputStream())) {
                    for (String time : List.of("09:33:50", "09:33:51", "09:33:52", "09:33:53")) {
                        var vector = new java.util.Vector<String>(10);
                        vector.add(time);
                        output.writeObject(vector);
                        output.flush();
                        assertEquals(time, updates.poll(2, TimeUnit.SECONDS));
                        assertEquals(time, store.get().state().clock());
                        assertEquals(SourceHealth.CONNECTED, store.get().state().sourceHealth());
                        Thread.sleep(1_100);
                    }
                    // More than four seconds since connection establishment: heartbeat refreshed.
                    assertEquals(List.of(SourceHealth.CONNECTED, SourceHealth.CONNECTED,
                            SourceHealth.CONNECTED, SourceHealth.CONNECTED), health);
                    connection.setSoTimeout(200);
                    assertThrows(SocketTimeoutException.class, () -> connection.getInputStream().read(),
                            "Connection must remain open and read-only");
                    server.setSoTimeout(200);
                    assertThrows(SocketTimeoutException.class, server::accept, "No reconnect");
                }
            }
        }
    }

    @Test @Timeout(10)
    void reconnectsWhenAValidSerializationStreamNeverSendsItsFirstClock() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(8_000);
            CountDownLatch secondConnection = new CountDownLatch(1);
            Thread fake = Thread.ofPlatform().start(() -> {
                try (var first = server.accept()) {
                    first.setSoTimeout(7_000);
                    new ObjectOutputStream(first.getOutputStream()).flush();
                    assertEquals(-1, first.getInputStream().read(), "Client must close without sending application bytes");
                } catch (Exception ex) { throw new RuntimeException(ex); }
                try (var ignored = server.accept()) { secondConnection.countDown(); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
            CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
            try (WinLaufenClient client = new WinLaufenClient("localhost", server.getLocalPort(), store)) {
                client.start();
                assertTrue(secondConnection.await(7, TimeUnit.SECONDS));
                assertNotEquals(SourceHealth.CONNECTED, store.get().state().sourceHealth());
                assertNull(store.get().state().clock());
            }
            fake.join();
        }
    }

    @Test @Timeout(10)
    void becomesStaleClosesReadOnlySocketAndReconnectsImmediately() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(8_000);
            CountDownLatch secondConnection = new CountDownLatch(1);
            List<SourceHealth> health = new CopyOnWriteArrayList<>();
            Thread fake = Thread.ofPlatform().start(() -> {
                try (var first = server.accept()) {
                    first.setSoTimeout(7_000);
                    var output = new ObjectOutputStream(first.getOutputStream());
                    output.writeObject("Uhr12:00:00"); output.flush();
                    assertEquals(-1, first.getInputStream().read(), "Client must send no application bytes");
                } catch (Exception ex) { throw new RuntimeException(ex); }
                try (var ignored = server.accept()) { secondConnection.countDown(); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
            CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults(), null);
            store.addListener(event -> health.add(event.state().sourceHealth()));
            try (WinLaufenClient client = new WinLaufenClient("localhost", server.getLocalPort(), store)) {
                client.start();
                assertTrue(secondConnection.await(7, TimeUnit.SECONDS));
            }
            fake.join();
            assertTrue(health.contains(SourceHealth.CONNECTED));
            assertTrue(health.contains(SourceHealth.STALE));
            assertTrue(health.contains(SourceHealth.DISCONNECTED));
        }
    }
}
