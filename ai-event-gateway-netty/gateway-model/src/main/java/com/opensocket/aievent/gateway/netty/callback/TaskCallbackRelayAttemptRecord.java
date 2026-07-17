package com.opensocket.aievent.gateway.netty.callback;

import java.time.OffsetDateTime;

/** Recent Netty callback relay submission record for Admin Runtime observability. */
public record TaskCallbackRelayAttemptRecord(
        String taskId,
        String callbackType,
        boolean submitted,
        String status,
        String message,
        OffsetDateTime occurredAt
) {
    public static TaskCallbackRelayAttemptRecord from(TaskCallbackRelayResult result) {
        TaskCallbackRelayResult safe = result == null
                ? TaskCallbackRelayResult.failed(null, null, "relay result is null")
                : result;
        return new TaskCallbackRelayAttemptRecord(
                safe.taskId(),
                safe.callbackType(),
                safe.submitted(),
                safe.status(),
                safe.message(),
                OffsetDateTime.now()
        );
    }
}
