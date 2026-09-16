package com.opensocket.aievent.core.security.outbound;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * SSRF boundary for platform-controlled outbound clients.
 *
 * <p>{@link #resolveAllowed(URI, OutboundDestinationPolicy, String)} returns the exact validated
 * addresses. PC-S4's External A2A transport connects directly to one of those InetAddress values;
 * it never hands the hostname back to another resolver between policy validation and connect.</p>
 */
@Component
public final class OutboundDestinationValidator {
    private static final String AWS_METADATA_V4 = "169.254.169.254";
    private static final String ALIBABA_METADATA_V4 = "100.100.100.200";
    private static final String AZURE_WIRE_SERVER_V4 = "168.63.129.16";
    private static final String GOOGLE_METADATA_HOST = "metadata.google.internal";

    public URI requireAllowed(String rawUrl, OutboundDestinationPolicy policy, String purpose) {
        return resolveAllowed(rawUrl, policy, purpose).uri();
    }

    public URI requireAllowed(URI uri, OutboundDestinationPolicy policy, String purpose) {
        return resolveAllowed(uri, policy, purpose).uri();
    }

    public ResolvedDestination resolveAllowed(String rawUrl, OutboundDestinationPolicy policy, String purpose) {
        if (rawUrl == null || rawUrl.isBlank()) throw denied(purpose, "DESTINATION_URL_REQUIRED");
        try { return resolveAllowed(URI.create(rawUrl.trim()), policy, purpose); }
        catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("DESTINATION_")) throw ex;
            throw denied(purpose, "DESTINATION_URL_INVALID");
        }
    }

    public ResolvedDestination resolveAllowed(URI uri, OutboundDestinationPolicy policy, String purpose) {
        if (uri == null) throw denied(purpose, "DESTINATION_URL_REQUIRED");
        OutboundDestinationPolicy effective = policy == null ? OutboundDestinationPolicy.externalHttp() : policy;
        String scheme = lower(uri.getScheme());
        if (!effective.allowedSchemes().contains(scheme)) throw denied(purpose, "DESTINATION_SCHEME_NOT_ALLOWED");
        if (uri.getUserInfo() != null) throw denied(purpose, "DESTINATION_USERINFO_NOT_ALLOWED");
        if (uri.getFragment() != null) throw denied(purpose, "DESTINATION_FRAGMENT_NOT_ALLOWED");
        String host = lower(uri.getHost());
        if (host.isBlank()) throw denied(purpose, "DESTINATION_HOST_REQUIRED");
        if (effective.denyCloudMetadataEndpoints() && GOOGLE_METADATA_HOST.equals(host)) throw denied(purpose, "CLOUD_METADATA_ENDPOINT_DENIED");
        if (!effective.allowlistHostSuffixes().isEmpty()
                && effective.allowlistHostSuffixes().stream().noneMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix))) {
            throw denied(purpose, "DESTINATION_HOST_NOT_ALLOWLISTED");
        }
        InetAddress[] addresses = resolve(host, purpose);
        if (addresses.length == 0) throw denied(purpose, "DESTINATION_DNS_EMPTY");
        boolean cidrRestricted = !effective.allowlistCidrs().isEmpty();
        for (InetAddress address : addresses) {
            if (effective.denyCloudMetadataEndpoints() && cloudMetadata(address)) throw denied(purpose, "CLOUD_METADATA_ENDPOINT_DENIED");
            if (effective.denyLoopbackAddresses() && (address.isLoopbackAddress() || address.isAnyLocalAddress())) throw denied(purpose, "LOOPBACK_DESTINATION_DENIED");
            if (effective.denyLinkLocalAddresses() && address.isLinkLocalAddress()) throw denied(purpose, "LINK_LOCAL_DESTINATION_DENIED");
            if (effective.denyPrivateAddresses() && (address.isSiteLocalAddress() || uniqueLocalIpv6(address) || carrierGradeNat(address))) {
                throw denied(purpose, "PRIVATE_DESTINATION_DENIED");
            }
            if (address.isMulticastAddress()) throw denied(purpose, "MULTICAST_DESTINATION_DENIED");
            if (cidrRestricted && effective.allowlistCidrs().stream().noneMatch(cidr -> inCidr(address, cidr))) {
                throw denied(purpose, "DESTINATION_CIDR_NOT_ALLOWLISTED");
            }
        }
        return new ResolvedDestination(uri, host, List.of(addresses));
    }

    private static InetAddress[] resolve(String host, String purpose) {
        try { return InetAddress.getAllByName(host); }
        catch (UnknownHostException ex) { throw denied(purpose, "DESTINATION_DNS_RESOLUTION_FAILED"); }
    }

    private static boolean cloudMetadata(InetAddress address) {
        String value = address.getHostAddress();
        int zone = value.indexOf('%'); if (zone >= 0) value = value.substring(0, zone);
        return AWS_METADATA_V4.equals(value) || ALIBABA_METADATA_V4.equals(value) || AZURE_WIRE_SERVER_V4.equals(value)
                || value.equalsIgnoreCase("fd00:ec2::254");
    }

    private static boolean uniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private static boolean carrierGradeNat(InetAddress address) {
        byte[] b = address.getAddress();
        return b.length == 4 && (b[0] & 0xff) == 100 && ((b[1] & 0xc0) == 64);
    }

    public static boolean inCidr(InetAddress address, String cidr) {
        if (cidr == null || cidr.isBlank()) return false;
        String[] parts = cidr.trim().split("/", 2);
        if (parts.length != 2) return false;
        try {
            byte[] actual = address.getAddress();
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            if (actual.length != network.length) return false;
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > actual.length * 8) return false;
            int fullBytes = prefix / 8, remainder = prefix % 8;
            if (!Arrays.equals(Arrays.copyOf(actual, fullBytes), Arrays.copyOf(network, fullBytes))) return false;
            if (remainder == 0) return true;
            int mask = (0xff << (8 - remainder)) & 0xff;
            return (actual[fullBytes] & mask) == (network[fullBytes] & mask);
        } catch (Exception ignored) { return false; }
    }

    private static String lower(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static IllegalArgumentException denied(String purpose, String reason) {
        String prefix = purpose == null || purpose.isBlank() ? "OUTBOUND" : purpose.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return new IllegalArgumentException(prefix + "_" + reason);
    }

    public record ResolvedDestination(URI uri, String host, List<InetAddress> addresses) {}
}
