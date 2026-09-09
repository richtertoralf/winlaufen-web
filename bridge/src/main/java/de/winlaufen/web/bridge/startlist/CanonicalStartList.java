package de.winlaufen.web.bridge.startlist;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The complete start list the bridge currently holds.
 *
 * <p>A start list is always a whole stock, never a patch: a successful import replaces this object
 * entirely, so no bib, class or start time of a previous list can survive it. That matters in
 * practice — bib 12 may be one participant in the prologue and a different one in a later heat.
 *
 * <p>{@code generation} counts the adopted versions of that stock and increases by exactly one per
 * successful replacement. It is deliberately nothing else:
 *
 * <ul>
 *   <li>it is not a competition id and not a run id — a new start list may be a correction, a late
 *       entry, a class or start-time fix, a new bib assignment <em>or</em> a different competition,
 *       and the bridge does not interpret which of these it is;</li>
 *   <li>it is not a participant id;</li>
 *   <li>it is not the WinLaufen {@code sourceRevision}, which belongs to the canonical competition
 *       state read from TCP 4444.</li>
 * </ul>
 *
 * <p>Within one start list the pair {@code (className, bib)} is unique. The same bib in different
 * classes is normal and stays allowed; a globally unique bib is not assumed.
 */
public record CanonicalStartList(long generation, StartListSource source, String sourceLabel,
                                 List<StartListEntry> entries) {

    /** Defensive bound on one start list. The largest observed real export has about 3 100 rows. */
    public static final int MAX_ENTRIES = 50_000;

    /** Longest accepted diagnostic label. */
    public static final int MAX_SOURCE_LABEL_CHARS = 256;

    public CanonicalStartList {
        if (generation < 0) {
            throw new IllegalArgumentException("Ungültige Startlisten-Generation: " + generation);
        }
        if (source == null) {
            throw new IllegalArgumentException("Startlistenquelle fehlt");
        }
        sourceLabel = sourceLabel == null ? "" : sourceLabel.strip();
        if (sourceLabel.length() > MAX_SOURCE_LABEL_CHARS) {
            throw new StartListFormatException("Quellbezeichnung überschreitet "
                    + MAX_SOURCE_LABEL_CHARS + " Zeichen");
        }
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new StartListFormatException("Startliste hat mehr als " + MAX_ENTRIES
                    + " Teilnehmer");
        }
        // A pair, not a joined string: any separator could itself occur in a class name
        // or a bib and would then make two different entries look like a duplicate.
        Set<List<String>> seen = new HashSet<>();
        for (StartListEntry entry : entries) {
            if (!seen.add(List.of(entry.className(), entry.bib()))) {
                throw new StartListFormatException("Startnummer " + entry.bib()
                        + " kommt in Klasse \"" + entry.className() + "\" mehrfach vor");
            }
        }
    }

    /**
     * The state before any import: generation 0, no entries.
     *
     * <p>A start list with zero entries only ever means "none imported yet"; a successful import
     * always carries at least one entry.
     */
    public static CanonicalStartList empty() {
        return new CanonicalStartList(0, StartListSource.IMPORT_CSV, "", List.of());
    }

    /** Whether a start list has been imported at all. */
    public boolean isPresent() {
        return generation > 0;
    }

    /**
     * The entry of this bib in this class, or {@code null}.
     *
     * <p>The class is part of the lookup on purpose: a bib alone does not identify an entry.
     */
    public StartListEntry find(String className, String bib) {
        for (StartListEntry entry : entries) {
            if (entry.className().equals(className) && entry.bib().equals(bib)) {
                return entry;
            }
        }
        return null;
    }
}
