package de.winlaufen.web.bridge.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Transport-security rule for output endpoints.
 *
 * <p>The architecture allows plaintext {@code ws} only for loopback or a deliberately trusted LAN,
 * and requires {@code wss} for Internet targets. Whether a host is "on the Internet" cannot be
 * decided reliably without DNS or geo lookups, and this project must not perform either. The
 * conservative substitute is a purely syntactic decision on the configured host:
 *
 * <ul>
 *   <li>{@code localhost} and loopback address literals may use {@code ws}.</li>
 *   <li>Private, link-local and unique-local address literals may use {@code ws}.</li>
 *   <li>Every other host — including every DNS name — must use {@code wss}.</li>
 * </ul>
 *
 * <p>A plaintext LAN target therefore has to be configured by IP address, not by hostname.
 * {@code RICHTER_PROJECTS} is a hosted Internet product and always requires {@code wss}.
 */
public final class EndpointPolicy {

    private EndpointPolicy() { }

    public static void validate(OutputTargetType type, URI endpoint) {
        if (endpoint == null || endpoint.getHost() == null) {
            throw new IllegalArgumentException("Ungültiger Target-Endpoint");
        }
        String scheme = endpoint.getScheme() == null
                ? ""
                : endpoint.getScheme().toLowerCase(Locale.ROOT);
        if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
            throw new IllegalArgumentException("Ungültiger Target-Endpoint");
        }
        if ("wss".equals(scheme)) {
            return;
        }
        if (type == OutputTargetType.RICHTER_PROJECTS) {
            throw new IllegalArgumentException("RICHTER_PROJECTS erfordert WSS");
        }
        if (!isTrustedPlaintextHost(endpoint.getHost())) {
            throw new IllegalArgumentException(
                    "Unverschlüsseltes ws:// ist nur für Loopback- oder LAN-IP-Adressen zulässig; "
                            + "für andere Ziele wss:// verwenden");
        }
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
