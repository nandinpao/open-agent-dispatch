package com.opensocket.aievent.core.capability;

/**
 * Canonical capability-first delegation intent. The contract intentionally contains no
 * target Agent, Agent Pool, Domain, System, Provider, Binding, Peer, endpoint, transport or
 * credential selector.
 */
public record CapabilityDelegationRequest(
        CapabilityRequirement requiredCapability,
        String reason,
        String inputPayloadRef,
        String sensitivityLevel) {
}
