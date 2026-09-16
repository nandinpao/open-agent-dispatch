package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

public record ExecutionPlanPolicyVersion(
        String tenantId,
        String policyId,
        int version,
        Map<String,Object> snapshot,
        String changeReason,
        String actorRef,
        OffsetDateTime createdAt) {
    public ExecutionPlanPolicyVersion { snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot); }
}
