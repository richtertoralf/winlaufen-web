package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.config.OutputTargetConfig;
import de.winlaufen.web.bridge.state.CanonicalSnapshot;
import de.winlaufen.web.bridge.state.CanonicalStateStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Owns one adapter per configured output target and fans the canonical state out to all of them.
 *
 * <p>Reconfiguration is incremental: unchanged targets keep their connection, ACK state and retry
 * counter. There is no global "reconnect everything" step and no shared queue.
 */
public final class OutputTargetManager implements AutoCloseable {

    private final CanonicalStateStore store;
    private final BiFunction<OutputTargetConfig, CanonicalSnapshot, LiveOutputAdapter> factory;
    private final Object lifecycle = new Object();
    private final AutoCloseable subscription;

    /** Immutable snapshot of the active adapters; read lock-free by the source thread. */
    private volatile List<LiveOutputAdapter> active = List.of();
    private Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean started;
    private boolean closed;

    private record Entry(OutputTargetConfig config, LiveOutputAdapter adapter) { }

    public OutputTargetManager(List<OutputTargetConfig> configs, String streamId,
                               CanonicalStateStore store) {
        this(configs, streamId, store,
                (config, initial) -> new WebSocketOutputAdapter(config, streamId, initial));
    }

    OutputTargetManager(List<OutputTargetConfig> configs, String streamId, CanonicalStateStore store,
                        BiFunction<OutputTargetConfig, CanonicalSnapshot, LiveOutputAdapter> factory) {
        this.store = store;
        this.factory = factory;
        reconfigure(configs);
        this.subscription = store.addListener(this::publish);
    }

    public void start() {
        List<LiveOutputAdapter> toStart;
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            started = true;
            toStart = List.copyOf(active);
        }
        toStart.forEach(LiveOutputAdapter::start);
    }

    /**
     * Applies a new target list without disturbing targets whose configuration is unchanged.
     * Removed targets are closed after they have been detached from the fan-out.
     */
    public void reconfigure(List<OutputTargetConfig> configs) {
        List<LiveOutputAdapter> obsolete = new ArrayList<>();
        List<LiveOutputAdapter> fresh = new ArrayList<>();
        boolean startFresh;
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            Map<String, Entry> previous = entries;
            Map<String, Entry> next = new LinkedHashMap<>();
            CanonicalSnapshot initial = store.get();
            for (OutputTargetConfig config : configs) {
                Entry existing = previous.get(config.id());
                if (existing != null && existing.config().equals(config)) {
                    next.put(config.id(), existing);
                    continue;
                }
                LiveOutputAdapter adapter = factory.apply(config, initial);
                next.put(config.id(), new Entry(config, adapter));
                fresh.add(adapter);
            }
            for (Map.Entry<String, Entry> item : previous.entrySet()) {
                Entry retained = next.get(item.getKey());
                if (retained == null || retained.adapter() != item.getValue().adapter()) {
                    obsolete.add(item.getValue().adapter());
                }
            }
            entries = next;
            active = next.values().stream().map(Entry::adapter).toList();
            startFresh = started;
        }
        if (startFresh) {
            fresh.forEach(LiveOutputAdapter::start);
        }
        obsolete.forEach(OutputTargetManager::closeQuietly);
    }

    public List<OutputTargetRuntime> runtimes() {
        return active.stream().map(LiveOutputAdapter::runtime).toList();
    }

    private void publish(CanonicalSnapshot snapshot) {
        for (LiveOutputAdapter adapter : active) {
            adapter.publish(snapshot);
        }
    }

    @Override
    public void close() {
        List<LiveOutputAdapter> toClose;
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            toClose = List.copyOf(active);
            active = List.of();
            entries = new LinkedHashMap<>();
        }
        try {
            subscription.close();
        } catch (Exception ignored) {
            // The store only removes a listener; failure here must not block shutdown.
        }
        toClose.forEach(OutputTargetManager::closeQuietly);
    }

    private static void closeQuietly(LiveOutputAdapter adapter) {
        try {
            adapter.close();
        } catch (RuntimeException ignored) {
            // One misbehaving adapter must not prevent the others from shutting down.
        }
    }
}
