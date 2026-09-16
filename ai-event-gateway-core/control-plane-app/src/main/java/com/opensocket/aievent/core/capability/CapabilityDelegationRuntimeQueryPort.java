package com.opensocket.aievent.core.capability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Stage 6 read-side port for capability delegation runtime facts.
 *
 * <p>The canonical delegation workflow may ask WHAT/WHO facts through this port, but it must not
 * know the database schema used to answer them.</p>
 */
public interface CapabilityDelegationRuntimeQueryPort {
    void requireCurrentAssignment(String tenantId, String taskId, String agentId, String agentSessionId);
    RuntimePolicySnapshot activePolicy(String tenantId);
    RequesterContext requester(String tenantId, String agentId);
    List<ProviderCandidate> whoCan(String tenantId, CapabilityRequirement requirement, String requestingAgentId);
    ProviderMetrics metrics(String tenantId, String bindingId);
    AssignmentDispatchReference currentAssignmentDispatch(String tenantId, String childTaskId);

    record RuntimePolicySnapshot(String routingProfileId, String defaultAccessMode,
            Map<String, String> operationModes, int maxCandidates) {}
    record RequesterContext(String departmentId, List<String> groupIds) {}
    record ProviderCandidate(String bindingId, String providerId, String providerType) {}
    record ProviderMetrics(BigDecimal estimatedCost, Long p95LatencyMs) {}
    record AssignmentDispatchReference(String assignmentId, String dispatchRequestId) {}
}
