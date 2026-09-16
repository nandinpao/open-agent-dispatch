package com.opensocket.aievent.core.enforcement.activation.contract;

import java.util.Locale;
import java.util.Objects;

public record AuthorityRouteKey(
        String tenantId,
        String domain,
        String unit,
        String riskLane,
        String entryPoint) {

    public static final String WILDCARD = "*";

    public AuthorityRouteKey {
        tenantId = normalize(tenantId, "tenantId", false);
        domain = normalize(domain, "domain", true);
        unit = normalize(unit, "unit", true);
        riskLane = normalize(riskLane, "riskLane", true);
        entryPoint = normalize(entryPoint, "entryPoint", false);
    }

    public boolean isExact() {
        return !tenantId.equals(WILDCARD) && !domain.equals(WILDCARD) && !unit.equals(WILDCARD)
                && !riskLane.equals(WILDCARD) && !entryPoint.equals(WILDCARD);
    }

    public int specificity() {
        int value = 0;
        if (!tenantId.equals(WILDCARD)) value++;
        if (!domain.equals(WILDCARD)) value++;
        if (!unit.equals(WILDCARD)) value++;
        if (!riskLane.equals(WILDCARD)) value++;
        if (!entryPoint.equals(WILDCARD)) value++;
        return value;
    }

    public boolean matches(AuthorityRouteKey candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return fieldMatches(tenantId, candidate.tenantId)
                && fieldMatches(domain, candidate.domain)
                && fieldMatches(unit, candidate.unit)
                && fieldMatches(riskLane, candidate.riskLane)
                && fieldMatches(entryPoint, candidate.entryPoint);
    }

    public String canonicalValue() {
        return String.join("|", tenantId, domain, unit, riskLane, entryPoint);
    }

    private static boolean fieldMatches(String policy, String candidate) {
        return WILDCARD.equals(policy) || policy.equals(candidate);
    }

    private static String normalize(String value, String field, boolean upperCase) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.contains("|")) throw new IllegalArgumentException(field + " must not contain '|'");
        return upperCase && !normalized.equals(WILDCARD) ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }
}
