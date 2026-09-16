package com.opensocket.aievent.core.capability;

/**
 * Phase 7 OpenDispatch-owned structural/budget ceilings attached to a semantically validated plan.
 * These values do not select a Provider, Agent or execution protocol.
 */
public record ExecutionPlanBudget(
        int maxSteps,
        int maxPlanDepth,
        int maxConcurrentBranches,
        int maxCapabilityInvocations,
        Long maxTokenBudget,
        Double maxEstimatedCost,
        Long maxExecutionTimeSeconds) {
}
