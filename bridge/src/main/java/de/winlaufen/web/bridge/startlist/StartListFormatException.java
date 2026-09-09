package de.winlaufen.web.bridge.startlist;

/**
 * A start-list input is not a usable start list. Always thrown before any state is replaced, so a
 * rejected import can never leave a partially adopted start list behind.
 */
public class StartListFormatException extends IllegalArgumentException {

    public StartListFormatException(String message) {
        super(message);
    }

    public StartListFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
