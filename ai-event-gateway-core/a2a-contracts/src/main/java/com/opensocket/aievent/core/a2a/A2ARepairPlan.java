package com.opensocket.aievent.core.a2a;
import java.util.List;

/** Immutable deterministic repair plan. It contains no raw tokens or sensitive payloads. */
public record A2ARepairPlan(
        String caseId,
        A2AReconciliationCaseType caseType,
        String diagnosisCode,
        A2ARepairAuthority authorityOwner,
        A2ARepairAction action,
        String requiredPermission,
        long expectedResourceVersion,
        String evidenceSnapshotHash,
        String impactSummary,
        List<String> preconditions,
        String planHash) {
    public A2ARepairPlan {
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
    }
}
