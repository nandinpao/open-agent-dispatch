package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** A0-R5 step-scoped Plan-time envelope. It defines the maximum supply boundary Router may consider. */
public record BindingAuthorizationEnvelope(
        String envelopeId,String tenantId,String decisionId,String planId,int planRevision,String stepId,
        String capabilityCode,int capabilityVersion,List<String> admittedBindingIds,List<String> admittedBindingClasses,
        List<String> admittedTrustDomainRefs,String maxSideEffect,String securityCeilingRef,String dataPolicyRef,
        String egressPolicyRef,String residencyConstraintRef,String policySnapshotRef,long authorizationEpoch,
        long revocationVersion,String status,OffsetDateTime issuedAt,OffsetDateTime validUntil,
        OffsetDateTime revokedAt,String revocationReason) {
    public BindingAuthorizationEnvelope {
        admittedBindingIds=admittedBindingIds==null?List.of():List.copyOf(admittedBindingIds);
        admittedBindingClasses=admittedBindingClasses==null?List.of():List.copyOf(admittedBindingClasses);
        admittedTrustDomainRefs=admittedTrustDomainRefs==null?List.of():List.copyOf(admittedTrustDomainRefs);
    }
}
