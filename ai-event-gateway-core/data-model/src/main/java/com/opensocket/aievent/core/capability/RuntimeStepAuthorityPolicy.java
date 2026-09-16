package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Phase 12 Tenant policy for automatic READY-step authority orchestration. It selects policy profile, never Provider. */
public record RuntimeStepAuthorityPolicy(
        String tenantId,
        String policyId,
        String displayName,
        String routingProfileId,
        String defaultAccessMode,
        Map<String,String> operationAccessModes,
        int maxCandidateBindings,
        boolean automaticAttachmentEnabled,
        String status,
        int version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
    public RuntimeStepAuthorityPolicy {
        operationAccessModes = operationAccessModes == null ? Map.of() : Map.copyOf(operationAccessModes);
    }
}
