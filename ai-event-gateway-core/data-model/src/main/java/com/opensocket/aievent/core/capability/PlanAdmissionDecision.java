package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;

/** Append-only A0-R5 Plan Admission decision. ADMITTED authorizes only a routing candidate boundary. */
public record PlanAdmissionDecision(
        String decisionId,String tenantId,String planId,int planRevision,String result,
        String policyId,Integer policyVersion,String policySnapshotRef,
        int stepCount,int admittedStepCount,int deniedStepCount,int waitingApprovalStepCount,
        int candidateBindingCount,int admittedBindingCount,List<String> reasonCodes,String actorRef,OffsetDateTime decidedAt) {
    public PlanAdmissionDecision { reasonCodes=reasonCodes==null?List.of():List.copyOf(reasonCodes); }
}
