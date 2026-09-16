package com.opensocket.aievent.core.a2a.core.port;

/** Port through which A2A requests a Child Task. Implementations remain owned by Task Authority. */
public interface TaskAuthorityPort {
    ChildTaskReceipt createChildTask(CreateChildTaskRequest request);

    /**
     * Immutable creation instructions resolved by A2A Core. Source event/task metadata is copied by
     * Task Authority from {@code parentTaskId}; governance decisions are carried explicitly so the
     * adapter does not re-resolve A2A policy.
     */
    record CreateChildTaskRequest(
            String tenantId,
            String a2aRequestId,
            String parentTaskId,
            String rootTaskId,
            String requestingTaskId,
            String requestingAgentId,
            String targetDepartmentId,
            String targetGroupId,
            String targetDomainId,
            String targetAgentPoolId,
            String taskType,
            String requestedServiceCode,
            java.util.List<String> requestedCapabilityCodes,
            String sensitivityLevel,
            int hopCount,
            String a2aPolicyId,
            String handoffContextPolicyId,
            String handoffContextRequirement,
            String resultAggregationPolicy,
            String childCancellationPolicy,
            String childFailurePolicy,
            String reason,
            String idempotencyKey,
            String correlationId) {}

    record ChildTaskReceipt(String childTaskId, long version, boolean replayed) {}
}
