package de.winlaufen.web.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OriginPolicyTest {
    @Test void acceptsSameHostAcrossHttpAndWebSocketPorts() {
        assertTrue(OriginPolicy.accepts("http://10.77.0.18:8080", "10.77.0.18:8081"));
        assertTrue(OriginPolicy.accepts("http://localhost:8080", "localhost:8081"));
        assertFalse(OriginPolicy.accepts("http://evil.test:8080", "localhost:8081"));
        assertFalse(OriginPolicy.accepts("https://localhost:8080", "localhost:8081"));
    }
}
