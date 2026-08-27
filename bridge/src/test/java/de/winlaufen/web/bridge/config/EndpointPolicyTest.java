package de.winlaufen.web.bridge.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointPolicyTest {

    @Test
    void plaintextIsAllowedForLoopbackAndLanAddressLiterals() {
        assertDoesNotThrow(() -> target(OutputTargetType.LOCAL, "ws://127.0.0.1:8081/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.LOCAL, "ws://localhost:8081/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.LOCAL, "ws://[::1]:8081/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://192.168.1.20:8081/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://10.77.0.18:8081/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://172.16.3.4:8081/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://169.254.4.5:8081/x"));
    }

    @Test
    void plaintextIsRejectedForPublicAddressesAndForEveryHostname() {
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.SELFHOST, "ws://203.0.113.7:8081/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.SELFHOST, "ws://liveserver.example.com/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.LOCAL, "ws://liveserver.local:8081/x"));
    }

    @Test
    void encryptedEndpointsAreAlwaysAllowedAndRichterProjectsRequiresThem() {
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "wss://liveserver.example.com/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.RICHTER_PROJECTS, "wss://live.example/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.RICHTER_PROJECTS, "ws://127.0.0.1:8081/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.RICHTER_PROJECTS, "ws://live.example/x"));
    }

    @Test
    void rejectsNonWebSocketSchemesAndMissingHosts() {
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.LOCAL, "http://127.0.0.1:8081/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.LOCAL, "file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> EndpointPolicy.validate(OutputTargetType.LOCAL, null));
    }

    @Test
    void hostClassificationNeverResolvesNames() {
        assertTrue(EndpointPolicy.isTrustedPlaintextHost("127.0.0.1"));
        assertTrue(EndpointPolicy.isTrustedPlaintextHost("localhost"));
        assertTrue(EndpointPolicy.isTrustedPlaintextHost("[fd00::1]"));
        assertTrue(EndpointPolicy.isTrustedPlaintextHost("fe80::1"));
        // A name is never trusted for plaintext, so no DNS lookup is required to decide.
        assertTrue(!EndpointPolicy.isTrustedPlaintextHost("example.com"));
        assertTrue(!EndpointPolicy.isTrustedPlaintextHost(""));
        assertTrue(!EndpointPolicy.isTrustedPlaintextHost("8.8.8.8"));
    }

    private static OutputTargetConfig target(OutputTargetType type, String endpoint) {
        return new OutputTargetConfig("t", type, true, URI.create(endpoint), "local", "12345678");
    }
}
