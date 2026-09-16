package com.opensocket.aievent.core.capability;

/**
 * Explicit per-Flow cutover request. R7 never performs a global authority switch.
 * releaseCandidateId is optional for R7 controlled-live, but required for Stage10 production-bound promotion.
 */
public record ExecutionSafetyActivationRequest(String targetState,String reason,String releaseCandidateId) {}
