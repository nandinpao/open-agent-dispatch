package com.opensocket.aievent.core.a2a;

import java.util.List;

import com.opensocket.aievent.core.iam.security.contract.MachineExecutionContext;

/**
 * Legacy directional A2A command retained only for historical request replay,
 * reconciliation and source compatibility during the Phase 0 hard cutover.
 *
 * <p>New delegation contracts MUST NOT require targetDomainId, targetAgentPoolId
 * or targetAgentId. Phase 1 replaces this compatibility shape with a canonical
 * capability-first delegation intent.</p>
 */
@Deprecated(forRemoval = true)
public record A2ARequestCommand(
        String tenantId,
        String sourceTaskId,
        String requestingAgentId,
        A2ARequesterType requesterType,
        String targetDepartmentId,
        String targetGroupId,
        String targetDomainId,
        String requestedTaskType,
        String requestedServiceCode,
        List<String> requestedCapabilityCodes,
        String reason,
        String inputPayloadRef,
        String sensitivityLevel,
        String idempotencyKey,
        String correlationId,
        String actorType,
        String actorId,
        MachineExecutionContext machineExecutionContext) {

    /** Backward-compatible constructor for Human/System callers that do not carry a machine principal. */
    public A2ARequestCommand(
            String tenantId, String sourceTaskId, String requestingAgentId, A2ARequesterType requesterType,
            String targetDepartmentId, String targetGroupId, String targetDomainId, String requestedTaskType,
            String requestedServiceCode, List<String> requestedCapabilityCodes, String reason, String inputPayloadRef,
            String sensitivityLevel, String idempotencyKey, String correlationId, String actorType, String actorId) {
        this(tenantId, sourceTaskId, requestingAgentId, requesterType, targetDepartmentId, targetGroupId,
                targetDomainId, requestedTaskType, requestedServiceCode, requestedCapabilityCodes, reason,
                inputPayloadRef, sensitivityLevel, idempotencyKey, correlationId, actorType, actorId, null);
    }
}
