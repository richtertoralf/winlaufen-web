package de.winlaufen.web.bridge.output;

import de.winlaufen.web.bridge.state.CanonicalSnapshot;

/**
 * One outgoing bridge connection to one live server. The same adapter is used for LOCAL and for
 * remote targets; only endpoint, TLS policy and credentials are configuration.
 */
public interface LiveOutputAdapter extends AutoCloseable {

    /** Non-blocking. Must never stall the source thread. */
    void publish(CanonicalSnapshot snapshot);

    OutputTargetRuntime runtime();

    void start();

    @Override
    void close();
}
