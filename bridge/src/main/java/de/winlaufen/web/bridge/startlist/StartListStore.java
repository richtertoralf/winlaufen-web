package de.winlaufen.web.bridge.startlist;

import de.winlaufen.web.bridge.config.BridgeConfigStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the one start list the bridge currently works with and keeps it across restarts.
 *
 * <p>The store offers exactly two operations, {@link #current()} and
 * {@link #replace(StartListImport)}. There is deliberately no merge and no patch: a start list is
 * a whole stock, and a new import replaces it completely. Otherwise a bib that belonged to one
 * participant in an earlier list could survive into a later one and silently resolve to the wrong
 * person.
 *
 * <p>{@code replace} is all-or-nothing. The new list is built and validated first, then persisted,
 * and only a successful persist makes it current. Every failure — an invalid list, a full disk, a
 * crash mid-write — leaves both the in-memory and the persisted start list exactly as they were,
 * and leaves the generation untouched.
 *
 * <p>This is organiser-supplied data, not WinLaufen source state. It is therefore persisted, while
 * the canonical competition state from TCP 4444 stays memory-only by design: a stored start list is
 * exactly as current as the operator's last import, whereas a stored competition state would
 * pretend to be live data it is not.
 *
 * <p>Adopting a start list says nothing about the competition. It is not a statement that a new
 * competition started, and this class never derives one.
 */
public final class StartListStore {

    /** Format marker of the persisted file, so a future change can be recognised. */
    static final String FILE_VERSION = "1";

    private static final String FILE_NAME = "startlist.properties";

    private final Path path;
    private final AtomicReference<CanonicalStartList> current;

    private StartListStore(Path path, CanonicalStartList initial) {
        this.path = path;
        this.current = new AtomicReference<>(initial);
    }

    /**
     * Opens the store at {@code path} and adopts a previously persisted start list.
     *
     * @throws IOException if the file exists but cannot be read as a complete start list; a
     *                     half-written file is never silently accepted as the current stock
     */
    public static StartListStore open(Path path) throws IOException {
        return new StartListStore(path, load(path));
    }

    /**
     * Opens the store next to the organiser configuration, so both live in the same directory a
     * deployment already provides — {@code /etc/winlaufen-web}, {@code C:\ProgramData\WinLaufen
     * Web} or {@code ${user.home}/.winlaufen-web}.
     */
    public static StartListStore besideConfig(Path configPath) throws IOException {
        return open(configPath.resolveSibling(FILE_NAME));
    }

    /** The location that {@link BridgeConfigStore#fromSystemProperties()} resolves to. */
    public static StartListStore fromSystemProperties() throws IOException {
        return besideConfig(BridgeConfigStore.fromSystemProperties().path());
    }

    public Path path() {
        return path;
    }

    /** The start list in force right now; {@link CanonicalStartList#empty()} before any import. */
    public CanonicalStartList current() {
        return current.get();
    }

    /**
     * Replaces the whole start list with {@code parsed} and gives it the next generation.
     *
     * <p>Synchronised so that two concurrent imports cannot receive the same generation or
     * interleave their writes.
     *
     * @return the adopted start list
     * @throws StartListFormatException if the import violates a canonical rule, for example a bib
     *                                  appearing twice in the same class
     * @throws IOException              if it could not be persisted; the previous start list stays
     *                                  in force
     */
    public synchronized CanonicalStartList replace(StartListImport parsed) throws IOException {
        CanonicalStartList next = new CanonicalStartList(current.get().generation() + 1,
                parsed.source(), parsed.sourceLabel(), parsed.entries());
        write(next);
        current.set(next);
        return next;
    }

    // --- Persistence ---------------------------------------------------------------------------

    /**
     * The file is an internal detail of this store, not a published contract. It uses
     * {@code java.util.Properties} because that is the format the bridge already stores its
     * organiser configuration in, so no new state technology enters the project.
     */
    private void write(CanonicalStartList list) throws IOException {
        Properties values = new Properties();
        values.setProperty("startlist.version", FILE_VERSION);
        values.setProperty("startlist.generation", Long.toString(list.generation()));
        values.setProperty("startlist.source", list.source().name());
        values.setProperty("startlist.sourceLabel", list.sourceLabel());
        values.setProperty("entries.count", Integer.toString(list.entries().size()));
        for (int index = 0; index < list.entries().size(); index++) {
            StartListEntry entry = list.entries().get(index);
            String prefix = "entries." + index + ".";
            values.setProperty(prefix + "bib", entry.bib());
            values.setProperty(prefix + "class", entry.className());
            put(values, prefix + "startTime", entry.startTime());
            put(values, prefix + "lastName", entry.lastName());
            put(values, prefix + "firstName", entry.firstName());
            put(values, prefix + "club", entry.club());
            put(values, prefix + "association", entry.association());
            put(values, prefix + "course", entry.course());
            put(values, prefix + "birthYear", entry.birthYear());
            put(values, prefix + "gender", entry.gender());
            put(values, prefix + "nation", entry.nation());
        }
        writeAtomically(values);
    }

    /** Absent and empty mean the same thing, so empty values are not written out. */
    private static void put(Properties values, String key, String value) {
        if (!value.isEmpty()) {
            values.setProperty(key, value);
        }
    }

    /**
     * Writes a complete temporary file first and then replaces the current one in a single move.
     * A reader therefore sees either the old start list or the new one, never a partial write, and
     * a crash before the move leaves the previous file untouched. Identical to the way the
     * organiser configuration is written.
     */
    private void writeAtomically(Properties values) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "startlist", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "WinLaufen Web Bridge Startliste");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Reads a persisted start list. Anything that does not read back as the complete stock it
     * claims to be — a missing count, a missing entry, an unknown format version — is an error
     * rather than a silently shortened start list.
     */
    private static CanonicalStartList load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return CanonicalStartList.empty();
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
        }
        try {
            String version = required(values, "startlist.version");
            if (!FILE_VERSION.equals(version)) {
                throw new IllegalArgumentException("Unbekannte Dateiversion: " + version);
            }
            long generation = Long.parseLong(required(values, "startlist.generation"));
            StartListSource source = StartListSource.valueOf(required(values, "startlist.source"));
            int count = Integer.parseInt(required(values, "entries.count"));
            if (count < 0 || count > CanonicalStartList.MAX_ENTRIES) {
                throw new IllegalArgumentException("Ungültige Teilnehmerzahl: " + count);
            }
            List<StartListEntry> entries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                String prefix = "entries." + index + ".";
                entries.add(new StartListEntry(
                        required(values, prefix + "bib"),
                        required(values, prefix + "class"),
                        values.getProperty(prefix + "startTime", ""),
                        values.getProperty(prefix + "lastName", ""),
                        values.getProperty(prefix + "firstName", ""),
                        values.getProperty(prefix + "club", ""),
                        values.getProperty(prefix + "association", ""),
                        values.getProperty(prefix + "course", ""),
                        values.getProperty(prefix + "birthYear", ""),
                        values.getProperty(prefix + "gender", ""),
                        values.getProperty(prefix + "nation", "")));
            }
            return new CanonicalStartList(generation, source,
                    values.getProperty("startlist.sourceLabel", ""), entries);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Gespeicherte Startliste in " + path + " ist unbrauchbar: "
                    + ex.getMessage(), ex);
        }
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Fehlender Eintrag: " + key);
        }
        return value;
    }
}
