package de.winlaufen.web.bridge.source.winlaufen;

import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.PresentationConfig;
import de.winlaufen.web.contract.SourceHealth;
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
            CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
            try (WinLaufenClient client = new WinLaufenClient("localhost", store)) {
                client.start();
                assertTrue(secondConnection.await(7, TimeUnit.SECONDS));
                assertNotEquals(SourceHealth.CONNECTED, store.get().state().sourceHealth());
                assertNull(store.get().state().clock());
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
            CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
            store.addListener(event -> health.add(event.state().sourceHealth()));
            try (WinLaufenClient client = new WinLaufenClient("localhost", store)) {
                client.start();
                assertTrue(secondConnection.await(7, TimeUnit.SECONDS));
            }
            fake.join();
            assertTrue(health.contains(SourceHealth.CONNECTED));
            assertTrue(health.contains(SourceHealth.STALE));
            assertTrue(health.contains(SourceHealth.DISCONNECTED));
        } catch (java.net.BindException occupied) {
            fail("Local TCP/4444 must be free for the protocol integration test", occupied);
        }
    }
}
