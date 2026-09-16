package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;

/** Current governed run view for a frozen Plan revision. */
public record PlanExecutionRun(
        String runId,String tenantId,String planId,int planRevision,String planDecisionId,String executionMode,String status,
        String policyId,int policyVersion,String idempotencyKey,long fencingToken,
        String completionPolicy,Integer quorumRequired,String convergenceOutcome,String convergenceReason,
        OffsetDateTime startedAt,OffsetDateTime updatedAt,OffsetDateTime completedAt) {
    public PlanExecutionRun(String runId,String tenantId,String planId,int planRevision,String planDecisionId,String executionMode,String status,
            String policyId,int policyVersion,String idempotencyKey,long fencingToken,
            OffsetDateTime startedAt,OffsetDateTime updatedAt,OffsetDateTime completedAt) {
        this(runId,tenantId,planId,planRevision,planDecisionId,executionMode,status,policyId,policyVersion,idempotencyKey,fencingToken,
                "ALL_REQUIRED",null,null,null,startedAt,updatedAt,completedAt);
    }
}
