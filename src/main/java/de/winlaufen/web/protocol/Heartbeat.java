package de.winlaufen.web.protocol;

public final class Heartbeat {
    public static final long STALE_NANOS = 4_000_000_000L;
    private long lastTelegramNanos;

    public Heartbeat(long connectionStartedNanos) { lastTelegramNanos = connectionStartedNanos; }

    public void accept(long nowNanos) { lastTelegramNanos = nowNanos; }
    public boolean isStale(long nowNanos) { return nowNanos - lastTelegramNanos > STALE_NANOS; }
}
