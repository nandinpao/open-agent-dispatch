package com.opensocket.aievent.core.callback;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Source-neutral callback facts exposed to callback acceptance guards before
 * any Task or Dispatch state transition is applied.
 */
public record TaskCallbackGuardContext(
        String tenantId,
        String taskId,
        String callbackId,
        TaskCallbackType callbackType,
        String dispatchRequestId,
        String assignmentId,
        String agentId,
        String idempotencyKey,
        Map<String, Object> payload,
        OffsetDateTime occurredAt) {

    public TaskCallbackGuardContext {
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
