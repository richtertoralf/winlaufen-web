package de.winlaufen.web.bridge.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Transport-security rule for output endpoints.
 *
 * <p>The architecture allows plaintext {@code ws} only where the operator can point at the target
 * deliberately, and requires {@code wss} everywhere else. Whether a host is "on the Internet"
 * cannot be decided reliably without DNS or geo lookups, and this project must not perform either.
 * The conservative substitute is a purely syntactic decision on the configured host:
 *
 * <ul>
 *   <li>{@code localhost} and loopback address literals may use {@code ws}.</li>
 *   <li>Private, link-local and unique-local address literals may use {@code ws}.</li>
 *   <li>A {@code SELFHOST} target may additionally use {@code ws} to a public IP address
 *       literal. This is the temporary self-hosted presentation node on a rented cloud VM with a
 *       public IPv4 address and no domain. It is permitted, but never silent: the endpoint carries
 *       a {@link #transportWarning(OutputTargetType, URI) transport warning} that Bridge Control
 *       shows permanently and the bridge writes to its log.</li>
 *   <li>Every other combination — every DNS name, and every public address for {@code LOCAL} —
 *       must use {@code wss}.</li>
 * </ul>
 *
 * <p>A plaintext target therefore has to be configured by IP address, not by hostname.
 * {@code RICHTER_PROJECTS} is a hosted Internet product and always requires {@code wss}.
 */
public final class EndpointPolicy {

    /**
     * Shown for every accepted plaintext endpoint that leaves the trusted LAN. It states what is
     * actually exposed and does not claim that the LAN rule protects nothing.
     */
    public static final String PLAINTEXT_INTERNET_WARNING =
            "Unverschlüsselte Internetverbindung. Übertragene Daten und der Verbindungsschlüssel "
                    + "können mitgelesen werden.";

    private EndpointPolicy() { }

    public static void validate(OutputTargetType type, URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null) {
            throw new IllegalArgumentException("Ungültiger Target-Endpoint");
        }
        String scheme = scheme(endpoint);
        if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
            throw new IllegalArgumentException("Ungültiger Target-Endpoint");
        }
        if ("wss".equals(scheme)) {
            return;
        }
        if (type == OutputTargetType.RICHTER_PROJECTS) {
            throw new IllegalArgumentException("RICHTER_PROJECTS erfordert WSS");
        }
        if (isTrustedPlaintextHost(endpoint.getHost())) {
            return;
        }
        if (type == OutputTargetType.SELFHOST && isRoutableAddressLiteral(endpoint.getHost())) {
            return;
        }
        if (type == OutputTargetType.SELFHOST) {
            throw new IllegalArgumentException(
                    "Unverschlüsseltes ws:// ist nur mit einer IP-Adresse zulässig; "
                            + "für Hostnamen und Domains wss:// verwenden");
        }
        throw new IllegalArgumentException(
                "Unverschlüsseltes ws:// ist für dieses Ziel nur für Loopback- oder "
                        + "LAN-IP-Adressen zulässig; für andere Ziele wss:// verwenden");
    }

    /**
     * The permanent warning for an already valid endpoint, or {@code null} when none is needed.
     * This is the single source of truth for the transport warning: Bridge Control renders exactly
     * this text and never derives a second security rule in JavaScript.
     */
    public static String transportWarning(OutputTargetType type, URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null || !"ws".equals(scheme(endpoint))) {
            return null;
        }
        if (isTrustedPlaintextHost(endpoint.getHost())) {
            return null;
        }
        return PLAINTEXT_INTERNET_WARNING;
    }

    private static String scheme(URI endpoint) {
        return endpoint.getScheme() == null ? "" : endpoint.getScheme().toLowerCase(Locale.ROOT);
    }

    /** Purely syntactic: no name resolution is performed, so the result is deterministic. */
    static boolean isTrustedPlaintextHost(String host) {
        String value = stripBrackets(host).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return false;
        }
        if ("localhost".equals(value)) {
            return true;
        }
        if (!isAddressLiteral(value)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || isUniqueLocal(address);
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    /**
     * An address literal that names one reachable host, such as the public IPv4 address of a
     * rented presentation node. A name is never accepted here, so this decision also stays free of
     * DNS. Wildcard and multicast addresses are not a target and stay rejected.
     */
    static boolean isRoutableAddressLiteral(String host) {
        String value = stripBrackets(host).toLowerCase(Locale.ROOT);
        if (value.isEmpty() || !isAddressLiteral(value)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return !address.isAnyLocalAddress() && !address.isMulticastAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    /** Accepts only literal IPv4/IPv6 forms, so {@code getByName} can never trigger a DNS lookup. */
    private static boolean isAddressLiteral(String value) {
        boolean digitsAndDots = value.matches("[0-9.]+");
        boolean ipv6 = value.indexOf(':') >= 0 && value.matches("[0-9a-f:.%]+");
        return digitsAndDots || ipv6;
    }

    private static boolean isUniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    private static String stripBrackets(String host) {
        if (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }
}
