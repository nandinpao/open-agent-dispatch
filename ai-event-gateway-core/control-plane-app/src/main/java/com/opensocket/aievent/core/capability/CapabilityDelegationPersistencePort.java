package com.opensocket.aievent.core.capability;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Stage 6 write/read persistence port for canonical capability delegation state and evidence.
 * Workflow code owns transitions; the adapter owns SQL and tenant-session binding.
 */
public interface CapabilityDelegationPersistencePort {
    ExistingDelegation findExisting(String tenantId, String parentTaskId, String requestingAgentId, String idempotencyKey);

    void createReceived(String tenantId, String delegationId, String parentTaskId, String requestingAgentId,
            String requestingAgentSessionId, String gatewayNodeId, String idempotencyKey, String requestDigest,
            CapabilityRequirement requirement, String reason, String inputPayloadRef, String sensitivityLevel,
            String correlationId, OffsetDateTime occurredAt);

    void markAuthorized(String tenantId, String delegationId, String authorizationDecisionId,
            String routingDecisionId, String adapterResolutionId, String bindingId, String providerId,
            String providerType, String executionKind, OffsetDateTime occurredAt);

    void markChildCreated(String tenantId, String delegationId, String childTaskId, OffsetDateTime occurredAt);

    void markDispatchQueued(String tenantId, String delegationId, String assignmentId, String dispatchRequestId,
            List<String> reasonCodes, OffsetDateTime occurredAt);

    void stop(String tenantId, String delegationId, String status, List<String> reasonCodes,
            String authorizationDecisionId, String routingDecisionId, String adapterResolutionId,
            String bindingId, String providerId, OffsetDateTime occurredAt);

    CapabilityDelegationReceipt receipt(String tenantId, String delegationId);

    void appendEvent(String tenantId, String delegationId, String eventType, String fromStatus, String toStatus,
            List<String> reasonCodes, Map<String, Object> evidence, OffsetDateTime occurredAt);

    record ExistingDelegation(String delegationId, String requestDigest) {}
}
