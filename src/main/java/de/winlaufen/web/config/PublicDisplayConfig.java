package de.winlaufen.web.config;

public record PublicDisplayConfig(boolean showClub, boolean showAssociation, boolean showNation,
                                  boolean showShooting, boolean showPublicMessages) {
    public static PublicDisplayConfig defaults() {
        return new PublicDisplayConfig(true, true, false, true, false);
    }
}
