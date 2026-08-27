package de.winlaufen.web.liveserver.web;

import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.HandshakeState;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.Protocol;

import java.util.List;

/**
 * RFC 6455 draft with a hard incoming payload limit that is selected by request path.
 *
 * <p>The limit is enforced by the library itself while frames are decoded, so an oversized message
 * is rejected before it can be assembled on the heap. Two drafts are registered on the server: the
 * ingest draft matches only the bridge path and allows a full contract snapshot; the browser draft
 * matches everything else and allows only a tiny payload, because browser connections are
 * read-only and never need to send application data.
 */
final class SizeLimitedDraft extends Draft_6455 {

    private final String ingestPath;
    private final boolean forIngest;
    private final int limit;

    SizeLimitedDraft(String ingestPath, boolean forIngest, int limit) {
        // Same known extensions and protocols as the default Draft_6455, only the
        // maximum incoming payload differs.
        super(List.of(), List.of(new Protocol("")), limit);
        this.ingestPath = ingestPath;
        this.forIngest = forIngest;
        this.limit = limit;
    }

    @Override
    public HandshakeState acceptHandshakeAsServer(ClientHandshake handshake)
            throws InvalidHandshakeException {
        boolean ingestRequest = ingestPath.equals(handshake.getResourceDescriptor());
        if (ingestRequest != forIngest) {
            return HandshakeState.NOT_MATCHED;
        }
        return super.acceptHandshakeAsServer(handshake);
    }

    @Override
    public Draft copyInstance() {
        return new SizeLimitedDraft(ingestPath, forIngest, limit);
    }
}
