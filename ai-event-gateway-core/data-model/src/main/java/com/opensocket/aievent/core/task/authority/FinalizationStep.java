package com.opensocket.aievent.core.task.authority;

/** Frozen A0-R2 finalization pipeline order. */
public enum FinalizationStep {
    OUTCOME_RESOLUTION(1), AGGREGATION_PERSISTENCE(2), EVIDENCE_PERSISTENCE(3), CASE_PERSISTENCE(4),
    ISSUE_PROJECTION_OUTBOX(5), BUDGET_SETTLEMENT(6), EXECUTION_MEMORY(7), LEASE_RELEASE(8);
    private final int order;
    FinalizationStep(int order){this.order=order;}
    public int order(){return order;}
}
