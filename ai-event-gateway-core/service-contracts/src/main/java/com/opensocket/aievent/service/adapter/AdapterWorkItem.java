package com.opensocket.aievent.service.adapter;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Version-neutral work item returned by the Core external adapter-worker API.
 *
 * <p>The field set intentionally mirrors the legacy AdapterAction JSON response
 * so existing external workers remain wire-compatible. Domain enums are strings
 * to let Core and worker versions evolve independently.</p>
 */
public record AdapterWorkItem(
        String actionId,
        String idempotencyKey,
        String incidentId,
        String taskId,
        String dispatchRequestId,
        String assignmentId,
        String agentId,
        String adapterName,
        String adapterType,
        String actionType,
        String status,
        String reason,
        String requestHash,
        String responseRef,
        Map<String, Object> payload,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime executingAt,
        OffsetDateTime completedAt,
        OffsetDateTime failedAt,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime retryWaitingAt,
        OffsetDateTime executorUnavailableAt,
        String claimedBy,
        OffsetDateTime claimedAt,
        OffsetDateTime leaseExpiresAt,
        OffsetDateTime workerHeartbeatAt,
        int attemptCount,
        int maxAttempts,
        String executorName,
        String lastError) {

    public AdapterWorkItem {
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
