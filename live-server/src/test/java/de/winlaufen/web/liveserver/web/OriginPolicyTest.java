package de.winlaufen.web.liveserver.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginPolicyTest {

    @Test
    void acceptsSameHostAcrossHttpAndWebSocketPorts() {
        assertTrue(OriginPolicy.accepts("http://10.77.0.18:44440", "10.77.0.18:44441"));
        assertTrue(OriginPolicy.accepts("http://localhost:44440", "localhost:44441"));
        assertTrue(OriginPolicy.accepts("http://127.0.0.1:44440", "127.0.0.1:44441"));
        assertTrue(OriginPolicy.accepts("http://LOCALHOST:44440", "localhost:44441"));
        assertTrue(OriginPolicy.accepts("http://timing-pc:44440", "timing-pc:44441"));
    }

    @Test
    void acceptsTlsOriginsForTheSameHost() {
        assertTrue(OriginPolicy.accepts("https://live.example:443", "live.example:44441"));
        assertTrue(OriginPolicy.accepts("https://10.77.0.18", "10.77.0.18:44441"));
    }

    @Test
    void acceptsIpv6LiteralsWithAndWithoutPort() {
        assertTrue(OriginPolicy.accepts("http://[::1]:44440", "[::1]:44441"));
        assertTrue(OriginPolicy.accepts("http://[fd00::1]:44440", "[fd00::1]"));
        assertFalse(OriginPolicy.accepts("http://[::1]:44440", "[fd00::1]:44441"));
    }

    @Test
    void rejectsForeignOriginsAndMissingOrigin() {
        assertFalse(OriginPolicy.accepts("http://evil.test:44440", "localhost:44441"));
        assertFalse(OriginPolicy.accepts("http://evil.test", "10.77.0.18:44441"));
        assertFalse(OriginPolicy.accepts(null, "localhost:44441"));
        assertFalse(OriginPolicy.accepts("http://localhost:44440", null));
        assertFalse(OriginPolicy.accepts("null", "localhost:44441"));
        assertFalse(OriginPolicy.accepts("", "localhost:44441"));
        assertFalse(OriginPolicy.accepts("file:///etc/passwd", "localhost:44441"));
        assertFalse(OriginPolicy.accepts("ws://localhost:44441", "localhost:44441"));
    }

    @Test
    void stripsOnlyRealPortSuffixes() {
        assertEquals("localhost", OriginPolicy.hostOnly("localhost:44441"));
        assertEquals("localhost", OriginPolicy.hostOnly("localhost"));
        assertEquals("::1", OriginPolicy.hostOnly("[::1]:44441"));
        assertEquals("::1", OriginPolicy.hostOnly("[::1]"));
    }
}
