package de.winlaufen.web.state;

import de.winlaufen.web.model.AppState;

public record StateEvent(Type type, AppState state, int classIndex) {
    public enum Type { SNAPSHOT, CLOCK, CLASS_SNAPSHOT, MESSAGE }
}
