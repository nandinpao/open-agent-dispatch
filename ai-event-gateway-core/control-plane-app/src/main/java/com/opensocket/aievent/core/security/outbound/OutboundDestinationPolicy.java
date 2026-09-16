package com.opensocket.aievent.core.security.outbound;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tenant-scoped outbound destination policy used by the dedicated External A2A transport.
 *
 * <p>PC-S4 turns the previous DNS/TLS governance declarations into runtime inputs. Redirects remain
 * disabled by the current transport. Request/response/SSE byte limits are enforced before data is
 * materialized into application objects.</p>
 */
public record OutboundDestinationPolicy(
        Set<String> allowedSchemes,
        List<String> allowlistHostSuffixes,
        List<String> allowlistCidrs,
        boolean denyPrivateAddresses,
        boolean denyLoopbackAddresses,
        boolean denyLinkLocalAddresses,
        boolean denyCloudMetadataEndpoints,
        int maxRedirects,
        boolean followRedirectSameOriginOnly,
        boolean dnsRebindingProtection,
        String requiredTlsVersion,
        List<String> certificatePins,
        int maxRequestBytes,
        int maxResponseBytes,
        int maxSseEventBytes,
        int maxSseStreamBytes,
        int maxSseEvents,
        int sseIdleTimeoutMs,
        int sseOverallTimeoutMs) {

    public OutboundDestinationPolicy {
        allowedSchemes = allowedSchemes == null || allowedSchemes.isEmpty()
                ? Set.of("https")
                : allowedSchemes.stream().map(v -> v.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        allowlistHostSuffixes = normalizeLower(allowlistHostSuffixes);
        allowlistCidrs = normalizeLower(allowlistCidrs);
        certificatePins = normalizeCaseSensitive(certificatePins);
        requiredTlsVersion = requiredTlsVersion == null || requiredTlsVersion.isBlank() ? null : requiredTlsVersion.trim();
        maxRequestBytes = positive(maxRequestBytes, 1_048_576);
        maxResponseBytes = positive(maxResponseBytes, 4_194_304);
        maxSseEventBytes = positive(maxSseEventBytes, 262_144);
        maxSseStreamBytes = positive(maxSseStreamBytes, 8_388_608);
        maxSseEvents = positive(maxSseEvents, 2_048);
        sseIdleTimeoutMs = positive(sseIdleTimeoutMs, 30_000);
        sseOverallTimeoutMs = positive(sseOverallTimeoutMs, 300_000);
    }

    /** Backward-compatible constructor for non-A2A callers that need only destination restrictions. */
    public OutboundDestinationPolicy(
            Set<String> allowedSchemes,
            List<String> allowlistHostSuffixes,
            List<String> allowlistCidrs,
            boolean denyPrivateAddresses,
            boolean denyLoopbackAddresses,
            boolean denyLinkLocalAddresses,
            boolean denyCloudMetadataEndpoints) {
        this(allowedSchemes, allowlistHostSuffixes, allowlistCidrs,
                denyPrivateAddresses, denyLoopbackAddresses, denyLinkLocalAddresses, denyCloudMetadataEndpoints,
                0, true, false, null, List.of(),
                1_048_576, 4_194_304, 262_144, 8_388_608, 2_048, 30_000, 300_000);
    }

    /** External peer endpoints: private/link-local/loopback destinations are denied by default. */
    public static OutboundDestinationPolicy externalHttp() {
        return new OutboundDestinationPolicy(Set.of("https", "http"), List.of(), List.of(), true, true, true, true,
                0, true, true, null, List.of(), 1_048_576, 4_194_304, 262_144, 8_388_608, 2_048, 30_000, 300_000);
    }

    /** Registered enterprise services may intentionally use RFC1918 but never loopback/link-local/metadata. */
    public static OutboundDestinationPolicy registeredEnterpriseService() {
        return new OutboundDestinationPolicy(Set.of("https", "http"), List.of(), List.of(), false, true, true, true);
    }

    private static int positive(int value, int fallback) { return value > 0 ? value : fallback; }

    private static List<String> normalizeLower(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(String::trim).filter(v -> !v.isBlank())
                .map(v -> v.toLowerCase(Locale.ROOT)).distinct().toList();
    }

    private static List<String> normalizeCaseSensitive(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(String::trim).filter(v -> !v.isBlank()).distinct().toList();
    }
}
