package de.winlaufen.web.model;

public enum OutputMode {
    LOCAL(true), SELFHOST(false), RICHTER_PROJECTS(false);

    private final boolean enabled;

    OutputMode(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
}
