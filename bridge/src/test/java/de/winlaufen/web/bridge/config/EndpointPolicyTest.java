package de.winlaufen.web.bridge.config;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointPolicyTest {

    @Test
    void plaintextIsAllowedForLoopbackAndLanAddressLiterals() {
        assertDoesNotThrow(() -> target(OutputTargetType.LOCAL, "ws://127.0.0.1:44441/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.LOCAL, "ws://localhost:44441/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.LOCAL, "ws://[::1]:44441/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://192.168.1.20:44441/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://10.77.0.18:44441/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://172.16.3.4:44441/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://169.254.4.5:44441/x"));
    }

    /** The temporary self-hosted presentation node on a rented cloud VM without a domain. */
    @Test
    void plaintextIsAllowedForASelfhostServerAtAPublicIpAddress() {
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://203.0.113.7:44441/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "ws://[2001:db8::7]:44441/x"));
    }

    @Test
    void aPlaintextInternetTargetAlwaysCarriesATransportWarning() {
        assertEquals(EndpointPolicy.PLAINTEXT_INTERNET_WARNING,
                EndpointPolicy.transportWarning(OutputTargetType.SELFHOST,
                        URI.create("ws://203.0.113.7:44441/x")));
        assertTrue(EndpointPolicy.PLAINTEXT_INTERNET_WARNING.contains("Unverschlüsselte"),
                "the warning names the actual exposure in the operator's language");
    }

    @Test
    void aLanOrEncryptedTargetIsNotWarnedAbout() {
        assertNull(EndpointPolicy.transportWarning(OutputTargetType.SELFHOST,
                URI.create("ws://192.168.1.20:44441/x")));
        assertNull(EndpointPolicy.transportWarning(OutputTargetType.LOCAL,
                URI.create("ws://127.0.0.1:44441/x")));
        assertNull(EndpointPolicy.transportWarning(OutputTargetType.SELFHOST,
                URI.create("wss://liveserver.example.com/x")));
        assertNull(EndpointPolicy.transportWarning(OutputTargetType.LOCAL, null));
    }

    @Test
    void plaintextToAPublicAddressStaysRejectedForEveryOtherType() {
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.LOCAL, "ws://203.0.113.7:44441/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.RICHTER_PROJECTS, "ws://203.0.113.7:44441/x"));
    }

    @Test
    void plaintextIsRejectedForEveryHostname() {
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.SELFHOST, "ws://liveserver.example.com/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.LOCAL, "ws://liveserver.local:44441/x"));
    }

    /** A wildcard or multicast address does not name a reachable presentation node. */
    @Test
    void plaintextIsRejectedForWildcardAndMulticastAddresses() {
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.SELFHOST, "ws://0.0.0.0:44441/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.SELFHOST, "ws://224.0.0.1:44441/x"));
    }

    @Test
    void encryptedEndpointsAreAlwaysAllowedAndRichterProjectsRequiresThem() {
        assertDoesNotThrow(() -> target(OutputTargetType.SELFHOST, "wss://liveserver.example.com/x"));
        assertDoesNotThrow(() -> target(OutputTargetType.RICHTER_PROJECTS, "wss://live.example/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.RICHTER_PROJECTS, "ws://127.0.0.1:44441/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.RICHTER_PROJECTS, "ws://live.example/x"));
    }

    @Test
    void rejectsNonWebSocketSchemesAndMissingHosts() {
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.LOCAL, "http://127.0.0.1:44441/x"));
        assertThrows(IllegalArgumentException.class,
                () -> target(OutputTargetType.SELFHOST, "http://203.0.113.7:44441/x"));
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
        // The LAN meaning of this predicate is unchanged: a public address is not a LAN address.
        assertTrue(!EndpointPolicy.isTrustedPlaintextHost("8.8.8.8"));
    }

    @Test
    void theSelfhostAddressRuleAlsoStaysFreeOfNameResolution() {
        assertTrue(EndpointPolicy.isRoutableAddressLiteral("8.8.8.8"));
        assertTrue(EndpointPolicy.isRoutableAddressLiteral("[2001:db8::7]"));
        assertTrue(!EndpointPolicy.isRoutableAddressLiteral("example.com"));
        assertTrue(!EndpointPolicy.isRoutableAddressLiteral("localhost"));
        assertTrue(!EndpointPolicy.isRoutableAddressLiteral("0.0.0.0"));
        assertTrue(!EndpointPolicy.isRoutableAddressLiteral("224.0.0.1"));
        assertTrue(!EndpointPolicy.isRoutableAddressLiteral(""));
    }

    private static OutputTargetConfig target(OutputTargetType type, String endpoint) {
        return new OutputTargetConfig("t", type, true, URI.create(endpoint), "local", "12345678");
    }
}
