package com.opensocket.aievent.core.capability;

/** A0-R7 transactional preparation result. No network I/O occurs while this result is created. */
public record ExecutionSafetyPrepareResult(
        RoutingAuthorityShadowResult routingEvidence,ExecutionAssignmentV206 executionAssignment,
        ExecutionLeaseV206 executionLease,ExecutionDispatchIntentV206 dispatchIntent,String cutoverState) {}
