package com.opensocket.aievent.core.kernel.configuration.cutover;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Durable authority cutover intent projected into signed runtime snapshots before governance commit. */
public record RuntimeConfigurationCutoverPlan(
        String cutoverId,
        String configSetId,
        String setKey,
        String environment,
        String revisionId,
        int authorityContractVersion,
        String targetAuthorityMode,
        Set<String> requiredKeys,
        String expectedSnapshotFingerprint,
        RuntimeConfigurationCutoverState state,
        String requestedBy,
        String reason,
        OffsetDateTime preparedAt,
        OffsetDateTime finalizedAt,
        OffsetDateTime cancelledAt,
        long version) {
    public RuntimeConfigurationCutoverPlan {
        if (authorityContractVersion < 2) throw new IllegalArgumentException("cutover requires authority contract v2 or later");
        targetAuthorityMode = required(targetAuthorityMode, "targetAuthorityMode").toUpperCase();
        if (!"RUNTIME_ONLY".equals(targetAuthorityMode)) throw new IllegalArgumentException("Generic cutover target must be RUNTIME_ONLY");
        requiredKeys = requiredKeys == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(requiredKeys));
        if (requiredKeys.isEmpty()) throw new IllegalArgumentException("cutover requiredKeys must not be empty");
    }
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
}
