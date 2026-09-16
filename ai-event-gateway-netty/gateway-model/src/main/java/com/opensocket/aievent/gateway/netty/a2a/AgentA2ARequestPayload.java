package com.opensocket.aievent.gateway.netty.a2a;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Agent-originated capability delegation intent carried on an authenticated runtime session.
 *
 * <p>Phase 0 deliberately removes target Department, Group, Domain, Pool and Agent topology
 * from the transport contract. Gateway derives the source Agent identity from the bound
 * connection. Current Core reauthorizes the source Task and resolves exactly one canonical
 * CapabilityRequirement; provider selection remains entirely server-side.</p>
 */
public record AgentA2ARequestPayload(
        @NotBlank String taskId,
        String requestedTaskType,
        String requestedServiceCode,
        List<String> requestedCapabilityCodes,
        @NotBlank String reason,
        String inputPayloadRef,
        String sensitivityLevel,
        @NotBlank String idempotencyKey,
        String correlationId) {
}
