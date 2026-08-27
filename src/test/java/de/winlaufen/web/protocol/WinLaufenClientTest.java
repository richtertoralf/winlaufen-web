package de.winlaufen.web.protocol;

import de.winlaufen.web.model.ConnectionHealth;
import de.winlaufen.web.state.StateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class WinLaufenClientTest {
    @Test @Timeout(10)
    void reconnectsWhenAValidSerializationStreamNeverSendsItsFirstClock() throws Exception {
        try (ServerSocket server = new ServerSocket(4444)) {
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
            StateStore store = new StateStore();
            try (WinLaufenClient client = new WinLaufenClient("localhost", store)) {
                client.start();
                assertTrue(secondConnection.await(7, TimeUnit.SECONDS));
                assertNotEquals(ConnectionHealth.CONNECTED, store.get().health());
                assertNull(store.get().clock());
            }
            fake.join();
        } catch (java.net.BindException occupied) {
            fail("Local TCP/4444 must be free for the protocol integration test", occupied);
        }
    }

    @Test @Timeout(10)
    void becomesStaleClosesReadOnlySocketAndReconnectsImmediately() throws Exception {
        try (ServerSocket server = new ServerSocket(4444)) {
            server.setSoTimeout(8_000);
            CountDownLatch secondConnection = new CountDownLatch(1);
            List<ConnectionHealth> health = new CopyOnWriteArrayList<>();
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
            StateStore store = new StateStore();
            store.addListener(event -> health.add(event.state().health()));
            try (WinLaufenClient client = new WinLaufenClient("localhost", store)) {
                client.start();
                assertTrue(secondConnection.await(7, TimeUnit.SECONDS));
            }
            fake.join();
            assertTrue(health.contains(ConnectionHealth.CONNECTED));
            assertTrue(health.contains(ConnectionHealth.STALE));
            assertTrue(health.contains(ConnectionHealth.DISCONNECTED));
        } catch (java.net.BindException occupied) {
            fail("Local TCP/4444 must be free for the protocol integration test", occupied);
        }
    }
}
