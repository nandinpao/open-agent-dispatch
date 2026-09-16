package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Externalized per-binding evidence for A0-R5 explainability. */
public record PlanAdmissionBindingEvaluation(
        String evaluationId,String tenantId,String decisionId,String planId,int planRevision,String stepId,
        String capabilityCode,int capabilityVersion,String bindingId,String providerId,String providerType,
        String bindingClass,String trustDomainRef,String result,String authorizationDecisionId,
        List<String> reasonCodes,OffsetDateTime evaluatedAt) {
    public PlanAdmissionBindingEvaluation { reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes); }
}
