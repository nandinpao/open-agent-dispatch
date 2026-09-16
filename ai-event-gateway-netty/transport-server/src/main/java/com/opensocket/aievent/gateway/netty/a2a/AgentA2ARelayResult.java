package com.opensocket.aievent.gateway.netty.a2a;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport relay result plus the business delegation receipt returned by Core.
 *
 * <p>{@code accepted} describes whether the Gateway successfully relayed the request to Core.
 * {@code delegationStatus} and {@code reasonCodes} describe the Core business outcome and must not
 * be conflated with transport acceptance.
 */
public record AgentA2ARelayResult(
        boolean accepted,
        String status,
        String message,
        int httpStatus,
        String delegationId,
        String delegationStatus,
        String childTaskId,
        String authorizationDecisionId,
        String routingDecisionId,
        String adapterResolutionId,
        List<String> reasonCodes) {

    public AgentA2ARelayResult {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    /** Backward-compatible transport-only acceptance used by tests or callers without a Core receipt. */
    public static AgentA2ARelayResult accepted(int httpStatus) {
        return accepted(httpStatus, null, null, null, null, null, null, List.of());
    }

    public static AgentA2ARelayResult accepted(int httpStatus, String delegationId, String delegationStatus,
            String childTaskId, String authorizationDecisionId, String routingDecisionId,
            String adapterResolutionId, List<String> reasonCodes) {
        return new AgentA2ARelayResult(true, "A2A_CORE_ACCEPTED", "Core processed Agent A2A request", httpStatus,
                delegationId, delegationStatus, childTaskId, authorizationDecisionId, routingDecisionId,
                adapterResolutionId, reasonCodes);
    }

    public static AgentA2ARelayResult rejected(int httpStatus, String message) {
        return new AgentA2ARelayResult(false, "A2A_CORE_REJECTED", message, httpStatus,
                null, null, null, null, null, null, List.of());
    }

    public static AgentA2ARelayResult failed(String message) {
        return new AgentA2ARelayResult(false, "A2A_RELAY_FAILED", message, 0,
                null, null, null, null, null, null, List.of());
    }

    /** Optional structured evidence attached to GATEWAY_ACK without changing its transport status semantics. */
    public Map<String, Object> evidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        put(evidence, "delegationId", delegationId);
        put(evidence, "delegationStatus", delegationStatus);
        put(evidence, "childTaskId", childTaskId);
        put(evidence, "authorizationDecisionId", authorizationDecisionId);
        put(evidence, "routingDecisionId", routingDecisionId);
        put(evidence, "adapterResolutionId", adapterResolutionId);
        if (!reasonCodes.isEmpty()) evidence.put("reasonCodes", reasonCodes);
        return Map.copyOf(evidence);
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
