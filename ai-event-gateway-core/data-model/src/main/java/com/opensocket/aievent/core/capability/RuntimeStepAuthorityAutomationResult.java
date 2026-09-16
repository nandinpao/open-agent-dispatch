package com.opensocket.aievent.core.capability;

/** Result returned to the Plan executor. AUTHORIZED carries a fresh Phase 3/4/5 evidence chain. */
public record RuntimeStepAuthorityAutomationResult(
        String result,
        RuntimeStepAuthorityDecision decision,
        PlanStepAuthorityRequest authorityRequest) {}
