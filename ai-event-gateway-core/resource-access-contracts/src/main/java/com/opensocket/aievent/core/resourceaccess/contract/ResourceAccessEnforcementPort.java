package com.opensocket.aievent.core.resourceaccess.contract;

/** Enforcement boundary used by REST, internal ports and streaming adapters. */
public interface ResourceAccessEnforcementPort {
    /** Evaluate and return evidence without throwing. Used for chain-node redaction and shadow comparison. */
    AuthorizationDecision assess(ResourceEnforcementCommand command);
    /** Evaluate and enforce according to the configured rollout mode. */
    AuthorizationDecision authorize(ResourceEnforcementCommand command);
}
