package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.config.OutputTargetType;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.PresentationConfig;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the retry backoff. Snapshot updates must never shorten a pending retry
 * wait; only shutdown may interrupt it.
 */
class OutputRetryBackoffTest {

    @Test
    void retryCurveFollowsTheSpecifiedDelays() {
        assertEquals(0L, WebSocketOutputAdapter.retryDelayMillis(0));
        assertEquals(2_000L, WebSocketOutputAdapter.retryDelayMillis(1));
        assertEquals(5_000L, WebSocketOutputAdapter.retryDelayMillis(2));
        assertEquals(10_000L, WebSocketOutputAdapter.retryDelayMillis(3));
        assertEquals(10_000L, WebSocketOutputAdapter.retryDelayMillis(4));
        assertEquals(10_000L, WebSocketOutputAdapter.retryDelayMillis(97));
    }

    @Test
    void sourceUpdatesDoNotShortenAPendingRetryWait() throws Exception {
        CanonicalStateStore store = new CanonicalStateStore(PresentationConfig.defaults());
        WebSocketOutputAdapter adapter = new WebSocketOutputAdapter(unreachableTarget(),
                "stream", store.get());
        AutoCloseable subscription = store.addListener(adapter::publish);
        try {
            adapter.start();
            // First attempt fails immediately (nothing is listening), then the adapter must wait 2 s.
            awaitRetryAttempt(adapter, 1, 4_000);
            int afterFirstFailure = adapter.runtime().retryAttempt();

            // Drive canonical revisions at the WinLaufen clock rate for a full second.
            for (int index = 0; index < 10; index++) {
                store.clock(String.format("10:00:%02d", index));
                Thread.sleep(100);
            }

            // Without the fix each revision woke the backoff, so this counter raced ahead.
            assertEquals(afterFirstFailure, adapter.runtime().retryAttempt(),
                    "snapshot publishing must not trigger additional connection attempts");
            assertEquals(OutputConnectionState.RETRY_WAIT, adapter.runtime().state());
        } finally {
            adapter.close();
            subscription.close();
        }
    }

    @Test
    void closeInterruptsAPendingRetryWaitImmediately() throws Exception {
        WebSocketOutputAdapter adapter = new WebSocketOutputAdapter(unreachableTarget(),
                "stream", new CanonicalStateStore(PresentationConfig.defaults()).get());
        adapter.start();
        awaitRetryAttempt(adapter, 1, 4_000);
        long started = System.nanoTime();
        adapter.close();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        assertTrue(elapsedMillis < 1_500,
                "close() must not wait for the retry delay, took " + elapsedMillis + " ms");
    }

    private static void awaitRetryAttempt(LiveOutputAdapter adapter, int minimum, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (adapter.runtime().retryAttempt() >= minimum) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("target never reached retry attempt " + minimum);
    }

    private static OutputTargetConfig unreachableTarget() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        return new OutputTargetConfig("offline", OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"),
                "local", "12345678");
    }
}
