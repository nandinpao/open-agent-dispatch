package com.opensocket.aievent.core.assignment;

/**
 * Internal-only provider-neutral assignment command emitted after WHO MAY / WHO SHOULD / HOW.
 * It is not a public target selector. Concrete provider/interface/tool identity is resolved
 * server-side from trusted registries.
 */
public record GovernedExternalProviderAssignmentRequest(
        String taskId,
        String executionTargetType,
        String providerType,
        String providerId,
        String bindingId,
        String authorizationDecisionId,
        String routingDecisionId,
        String adapterResolutionId,
        String selectedPeerInterfaceId,
        String selectedMcpServerId,
        String selectedMcpToolId,
        String reason) {
}
