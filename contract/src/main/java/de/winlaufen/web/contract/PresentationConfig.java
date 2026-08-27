package de.winlaufen.web.contract;

/** Organiser presentation choices. Owned by the bridge, published as part of every snapshot. */
public record PresentationConfig(boolean showClub, boolean showAssociation, boolean showNation,
                                 boolean showShooting, boolean showPublicMessages) {

    public static PresentationConfig defaults() {
        return new PresentationConfig(true, true, false, true, false);
    }
}
