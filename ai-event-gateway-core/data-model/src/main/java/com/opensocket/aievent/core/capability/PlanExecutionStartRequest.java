package com.opensocket.aievent.core.capability;

/** Starts execution of one frozen, semantically validated Plan revision. */
public record PlanExecutionStartRequest(
        String planId,Integer planRevision,String planDecisionId,String executionMode,String idempotencyKey,
        String completionPolicy,Integer quorumRequired) {
    public PlanExecutionStartRequest(String planId,Integer planRevision,String planDecisionId,String executionMode,String idempotencyKey) {
        this(planId,planRevision,planDecisionId,executionMode,idempotencyKey,"ALL_REQUIRED",null);
    }
}
