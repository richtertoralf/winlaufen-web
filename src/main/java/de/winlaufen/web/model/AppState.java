package de.winlaufen.web.model;

public record AppState(long revision, ConnectionHealth health, String clock,
                       Competition competition, CurrentFinish currentFinish, String message) {
    public static AppState empty() {
        return new AppState(0, ConnectionHealth.DISCONNECTED, null, null, null, null);
    }
}
