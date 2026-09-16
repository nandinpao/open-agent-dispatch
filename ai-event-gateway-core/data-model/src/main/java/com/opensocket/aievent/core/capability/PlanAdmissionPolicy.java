package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** A0-R5 plan-time security ceiling. It cannot rank or select a Provider. */
public record PlanAdmissionPolicy(
        String tenantId,String policyId,String displayName,int maxCandidateBindingsPerStep,long envelopeTtlSeconds,
        List<String> allowedBindingClasses,List<String> allowedDataClasses,String maxSensitivityLevel,
        boolean externalEgressAllowed,List<String> allowedRegions,String maxSideEffect,
        String securityCeilingRef,String dataPolicyRef,String egressPolicyRef,String residencyConstraintRef,
        String status,int version,OffsetDateTime createdAt,OffsetDateTime updatedAt) {
    public PlanAdmissionPolicy {
        allowedBindingClasses=allowedBindingClasses==null?List.of():List.copyOf(allowedBindingClasses);
        allowedDataClasses=allowedDataClasses==null?List.of():List.copyOf(allowedDataClasses);
        allowedRegions=allowedRegions==null?List.of():List.copyOf(allowedRegions);
    }
}
