package com.opensocket.aievent.core.capability;

/** Attaches the WHO MAY -> WHO SHOULD -> HOW evidence chain to one READY Step. */
public record PlanStepAuthorityRequest(String authorizationDecisionId,String routingDecisionId,String adapterResolutionId) {}
