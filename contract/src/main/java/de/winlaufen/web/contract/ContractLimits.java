package de.winlaufen.web.contract;

/**
 * Structural resource limits of the versioned bridge/live-server contract.
 *
 * <p>These are defensive transport limits, not competition plausibility rules. Every source
 * adapter must reject wire data that exceeds them at its own entry boundary, so that the
 * canonical state can never hold a value that is afterwards unpublishable.
 */
public final class ContractLimits {
    /** Maximum encoded length of one snapshot envelope. */
    public static final int MAX_JSON_CHARS = 8_000_000;

    /**
     * Maximum accepted ingest WebSocket message size in bytes. Two bytes per contract character
     * covers Latin-1 supplement text (German umlauts) without truncating a valid snapshot.
     */
    public static final int MAX_INGEST_MESSAGE_BYTES = 2 * MAX_JSON_CHARS;

    /** Browser connections are read-only; they never need to send an application payload. */
    public static final int MAX_BROWSER_MESSAGE_BYTES = 4_096;

    /** Maximum encoded length of one ACK envelope. */
    public static final int MAX_ACK_CHARS = 4_096;

    public static final int MAX_CLASSES = 1_000;
    public static final int MAX_ROWS = 10_000;
    public static final int MAX_HEADERS = 256;
    public static final int MAX_CELL_CHARS = 65_536;
    public static final int MAX_NAME_CHARS = 4_096;
    public static final int MAX_MESSAGE_CHARS = 65_536;
    public static final int MAX_IDENTIFIER_CHARS = 256;

    private ContractLimits() { }
}
