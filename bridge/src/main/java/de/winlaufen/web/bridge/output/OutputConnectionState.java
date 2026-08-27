package de.winlaufen.web.bridge.output;

/**
 * Runtime connection state of one output target.
 *
 * <p>{@code STALE} means the transport is open but the target stopped confirming snapshots, so it
 * must not be presented to the organiser as healthy.
 */
public enum OutputConnectionState { DISABLED, CONNECTING, CONNECTED, STALE, RETRY_WAIT }
