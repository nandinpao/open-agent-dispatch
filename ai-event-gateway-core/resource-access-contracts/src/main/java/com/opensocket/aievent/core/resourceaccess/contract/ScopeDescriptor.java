package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;

/** Versioned scope evidence emitted by trusted Resource Access resolvers and policy evaluation. */
public record ScopeDescriptor(
        ScopeType scopeType,
        String scopeRefId,
        String source,
        long sourceVersion,
        Instant validFrom,
        Instant validTo) {
    public ScopeDescriptor {
        if (scopeType == null) throw new IllegalArgumentException("scopeType is required");
        scopeRefId = scopeRefId == null ? "" : scopeRefId.trim();
        source = requireText(source, "source");
        if (sourceVersion < 0) throw new IllegalArgumentException("sourceVersion must be non-negative");
        if (validFrom == null) throw new IllegalArgumentException("validFrom is required");
        if (validTo != null && !validTo.isAfter(validFrom)) throw new IllegalArgumentException("validTo must be after validFrom");
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
