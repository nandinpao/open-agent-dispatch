package com.opensocket.aievent.core.capability;

/**
 * Semantic reasoning SPI. Triage Agent can only propose WHAT. Implementations must not receive provider,
 * authorization, routing or transport authority through this contract.
 */
public interface TriageAgentPort {
    TriageProposal propose(TriageRequest request);
}
