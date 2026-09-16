package com.opensocket.aievent.core.capability;

/** Phase 5 HOW preview input. The caller supplies only a persisted Phase 4 routing decision. */
public record ExecutionAdapterResolutionRequest(String routingDecisionId) {}
