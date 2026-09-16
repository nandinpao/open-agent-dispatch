package com.opensocket.aievent.core.iam.runtime.machine;

import com.opensocket.aievent.core.iam.runtime.config.IamMachineTokenProperties;
import com.opensocket.aievent.core.iam.token.domain.CidrBlock;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the caller IP without trusting spoofable forwarding headers from untrusted peers.
 *
 * <p>The direct peer must first belong to an explicitly configured trusted-proxy CIDR. When
 * forwarding information is accepted, the chain is evaluated from right to left and trusted
 * proxy hops are skipped. This prevents a caller-supplied left-most X-Forwarded-For value from
 * becoming the security identity when a trusted reverse proxy appends the real caller address.</p>
 */
public final class TrustedClientIpResolver {
    private final List<CidrBlock> trustedProxies;

    public TrustedClientIpResolver(IamMachineTokenProperties properties) {
        List<CidrBlock> parsed = new ArrayList<>();
        for (String value : properties.getTrustedProxyCidrs()) {
            if (value != null && !value.isBlank()) parsed.add(CidrBlock.parse(value.trim()));
        }
        this.trustedProxies = List.copyOf(parsed);
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) return "";
        String peer = ipLiteral(request.getRemoteAddr());
        if (!trusted(peer)) return peer;

        List<String> forwarded = forwardedChain(request.getHeader("Forwarded"));
        if (forwarded.isEmpty()) forwarded = xForwardedForChain(request.getHeader("X-Forwarded-For"));
        if (forwarded.isEmpty()) return peer;

        // RFC-style proxy chains are caller -> proxy -> proxy. Walk from the peer side back
        // toward the caller, ignoring only hops that are themselves explicitly trusted.
        for (int i = forwarded.size() - 1; i >= 0; i--) {
            String candidate = forwarded.get(i);
            if (!trusted(candidate)) return candidate;
        }
        // All reported hops are trusted. The left-most hop is the best available caller
        // identity; returning it is safer than collapsing every request to the last proxy.
        return forwarded.get(0);
    }

    private boolean trusted(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return trustedProxies.stream().anyMatch(cidr -> cidr.contains(ip));
    }

    private static List<String> forwardedChain(String header) {
        List<String> values = new ArrayList<>();
        if (header == null || header.isBlank()) return values;
        for (String element : header.split(",")) {
            for (String parameter : element.split(";")) {
                String p = parameter.trim();
                if (!p.regionMatches(true, 0, "for=", 0, 4)) continue;
                String ip = ipLiteral(p.substring(4));
                if (!ip.isBlank()) values.add(ip);
                break;
            }
        }
        return values;
    }

    private static List<String> xForwardedForChain(String header) {
        List<String> values = new ArrayList<>();
        if (header == null || header.isBlank()) return values;
        for (String candidate : header.split(",")) {
            String ip = ipLiteral(candidate);
            if (!ip.isBlank()) values.add(ip);
        }
        return values;
    }

    /** Accept only IP literals. Host names and RFC 7239 obfuscated identifiers are ignored. */
    private static String ipLiteral(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.startsWith("[") && value.contains("]")) {
            value = value.substring(1, value.indexOf(']'));
        } else {
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            if (firstColon > 0 && firstColon == lastColon) value = value.substring(0, firstColon);
        }
        return value.matches("[0-9A-Fa-f:.]+") ? value : "";
    }
}
