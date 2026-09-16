package com.opensocket.aievent.core.assignment;

/**
 * Internal-only runtime assignment command emitted after capability WHO MAY / WHO SHOULD / HOW
 * authority has selected a MANAGED_AGENT provider. Concrete Agent identity is resolved by the
 * managed execution adapter from trusted provider linkage; it is never caller input.
 */
public record GovernedManagedAgentAssignmentRequest(
        String taskId,
        String agentId,
        String bindingId,
        String providerId,
        String authorizationDecisionId,
        String routingDecisionId,
        String adapterResolutionId,
        String reason) {
}
