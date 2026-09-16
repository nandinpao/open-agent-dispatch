package com.opensocket.aievent.core.resourceaccess.contract;
/** Stable entry point for formal, explain and simulation Resource Access decisions. */
public interface ResourceAuthorizationPort {
 AuthorizationDecision evaluate(AuthorizationRequest request);
 AuthorizationDecision explain(AuthorizationRequest request);
 AuthorizationDecision simulate(AuthorizationSimulationRequest request);
}
