package de.winlaufen.web.bridge.startlist;

import java.util.List;

/**
 * A fully parsed and validated start list that has not been adopted yet.
 *
 * <p>It carries no generation: the generation belongs to the adopted stock and is assigned by
 * {@link StartListStore#replace(StartListImport)}. Keeping both apart is what makes a failed
 * import harmless — nothing that was rejected ever received a version.
 */
public record StartListImport(StartListSource source, String sourceLabel,
                              List<StartListEntry> entries) {

    public StartListImport {
        entries = List.copyOf(entries);
        if (entries.isEmpty()) {
            throw new StartListFormatException("Startliste enthält keine Teilnehmer");
        }
    }
}
