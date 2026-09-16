package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Append-only Phase 12 orchestration evidence for one READY step authority attempt. */
public record RuntimeStepAuthorityDecision(
        String decisionId,
        String tenantId,
        String runId,
        String stepId,
        int attemptGeneration,
        String result,
        String capabilityCode,
        String operation,
        String requesterPrincipalType,
        String requesterPrincipalId,
        String runtimePolicyId,
        Integer runtimePolicyVersion,
        String routingProfileId,
        int candidateCount,
        int passCount,
        int waitingApprovalCount,
        List<String> authorizationDecisionIds,
        String routingDecisionId,
        String adapterResolutionId,
        List<String> reasonCodes,
        OffsetDateTime decidedAt) {
    public RuntimeStepAuthorityDecision {
        authorizationDecisionIds = authorizationDecisionIds == null ? List.of() : List.copyOf(authorizationDecisionIds);
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }
}
