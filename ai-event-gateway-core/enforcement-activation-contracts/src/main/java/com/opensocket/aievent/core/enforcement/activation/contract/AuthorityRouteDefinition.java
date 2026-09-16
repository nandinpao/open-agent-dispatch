package com.opensocket.aievent.core.enforcement.activation.contract;

import java.util.LinkedHashSet;
import java.util.Set;

public record AuthorityRouteDefinition(
        AuthorityRouteKey key,
        AuthorityMode mode,
        int targetBasisPoints,
        Set<String> includeCohorts,
        Set<String> excludeCohorts,
        String reasonCode) {

    public AuthorityRouteDefinition {
        if (key == null) throw new IllegalArgumentException("key is required");
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (targetBasisPoints < 0 || targetBasisPoints > 10_000) throw new IllegalArgumentException("targetBasisPoints must be between 0 and 10000");
        includeCohorts = normalize(includeCohorts);
        excludeCohorts = normalize(excludeCohorts);
        LinkedHashSet<String> overlap = new LinkedHashSet<>(includeCohorts);
        overlap.retainAll(excludeCohorts);
        if (!overlap.isEmpty()) throw new IllegalArgumentException("include/exclude cohorts overlap: " + overlap);
        reasonCode = reasonCode == null || reasonCode.isBlank() ? "ROUTE_POLICY" : reasonCode.trim();
        validateMode(mode, targetBasisPoints, includeCohorts, excludeCohorts);
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("cohort values must not be blank");
            normalized.add(value.trim());
        }
        return Set.copyOf(normalized);
    }

    private static void validateMode(AuthorityMode mode, int bps, Set<String> includes, Set<String> excludes) {
        switch (mode) {
            case LEGACY_ONLY, SHADOW, PAUSED -> {
                if (bps != 0) throw new IllegalArgumentException(mode + " requires targetBasisPoints=0");
                if (!includes.isEmpty() || !excludes.isEmpty()) throw new IllegalArgumentException(mode + " does not accept cohorts");
            }
            case TARGET_CANARY -> {
                if (bps <= 0 || bps >= 10_000) throw new IllegalArgumentException("TARGET_CANARY requires 1..9999 basis points");
            }
            case TARGET_PRIMARY -> {
                if (bps != 10_000) throw new IllegalArgumentException("TARGET_PRIMARY requires 10000 basis points");
            }
            case TARGET_ONLY -> {
                if (bps != 10_000) throw new IllegalArgumentException("TARGET_ONLY requires 10000 basis points");
                if (!includes.isEmpty() || !excludes.isEmpty()) throw new IllegalArgumentException("TARGET_ONLY cannot retain cohort exceptions");
            }
        }
    }
}
