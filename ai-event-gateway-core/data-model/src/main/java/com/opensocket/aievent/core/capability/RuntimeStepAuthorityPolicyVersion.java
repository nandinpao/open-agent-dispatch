package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.Map;

/** Immutable Phase 12 runtime authority policy snapshot. */
public record RuntimeStepAuthorityPolicyVersion(
        String tenantId,String policyId,int version,Map<String,Object> snapshot,String changeReason,String actorRef,OffsetDateTime createdAt) {
    public RuntimeStepAuthorityPolicyVersion { snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot); }
}
