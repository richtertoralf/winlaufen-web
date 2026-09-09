package de.winlaufen.web.bridge.startlist;

/**
 * Where a canonical start list came from.
 *
 * <p>The canonical model is deliberately not tied to a file format: once a verified WinLaufen
 * start-list wire format exists, it fills the same {@link CanonicalStartList} through an
 * additional constant here. No such format exists today and none may be invented; see
 * {@code docs/WINLAUFEN_PROTOCOL.md}.
 */
public enum StartListSource {
    IMPORT_CSV,
    IMPORT_XLSX
}
