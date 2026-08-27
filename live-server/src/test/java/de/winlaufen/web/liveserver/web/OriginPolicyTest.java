package de.winlaufen.web.liveserver.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginPolicyTest {

    @Test
    void acceptsSameHostAcrossHttpAndWebSocketPorts() {
        assertTrue(OriginPolicy.accepts("http://10.77.0.18:8080", "10.77.0.18:8081"));
        assertTrue(OriginPolicy.accepts("http://localhost:8080", "localhost:8081"));
        assertTrue(OriginPolicy.accepts("http://127.0.0.1:8080", "127.0.0.1:8081"));
        assertTrue(OriginPolicy.accepts("http://LOCALHOST:8080", "localhost:8081"));
        assertTrue(OriginPolicy.accepts("http://timing-pc:8080", "timing-pc:8081"));
    }

    @Test
    void acceptsTlsOriginsForTheSameHost() {
        assertTrue(OriginPolicy.accepts("https://live.example:443", "live.example:8081"));
        assertTrue(OriginPolicy.accepts("https://10.77.0.18", "10.77.0.18:8081"));
    }

    @Test
    void acceptsIpv6LiteralsWithAndWithoutPort() {
        assertTrue(OriginPolicy.accepts("http://[::1]:8080", "[::1]:8081"));
        assertTrue(OriginPolicy.accepts("http://[fd00::1]:8080", "[fd00::1]"));
        assertFalse(OriginPolicy.accepts("http://[::1]:8080", "[fd00::1]:8081"));
    }

    @Test
    void rejectsForeignOriginsAndMissingOrigin() {
        assertFalse(OriginPolicy.accepts("http://evil.test:8080", "localhost:8081"));
        assertFalse(OriginPolicy.accepts("http://evil.test", "10.77.0.18:8081"));
        assertFalse(OriginPolicy.accepts(null, "localhost:8081"));
        assertFalse(OriginPolicy.accepts("http://localhost:8080", null));
        assertFalse(OriginPolicy.accepts("null", "localhost:8081"));
        assertFalse(OriginPolicy.accepts("", "localhost:8081"));
        assertFalse(OriginPolicy.accepts("file:///etc/passwd", "localhost:8081"));
        assertFalse(OriginPolicy.accepts("ws://localhost:8081", "localhost:8081"));
    }

    @Test
    void stripsOnlyRealPortSuffixes() {
        assertEquals("localhost", OriginPolicy.hostOnly("localhost:8081"));
        assertEquals("localhost", OriginPolicy.hostOnly("localhost"));
        assertEquals("::1", OriginPolicy.hostOnly("[::1]:8081"));
        assertEquals("::1", OriginPolicy.hostOnly("[::1]"));
    }
}
