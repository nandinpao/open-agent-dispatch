package com.opensocket.aievent.core.action;

import com.opensocket.aievent.service.adapter.AdapterWorkItem;

public final class AdapterWorkItemMapper {
    private AdapterWorkItemMapper() {
    }

    public static AdapterWorkItem from(AdapterAction action) {
        if (action == null) return null;
        return new AdapterWorkItem(
                action.getActionId(),
                action.getIdempotencyKey(),
                action.getIncidentId(),
                action.getTaskId(),
                action.getDispatchRequestId(),
                action.getAssignmentId(),
                action.getAgentId(),
                action.getAdapterName(),
                action.getAdapterType() == null ? null : action.getAdapterType().name(),
                action.getActionType() == null ? null : action.getActionType().name(),
                action.getStatus() == null ? null : action.getStatus().name(),
                action.getReason(),
                action.getRequestHash(),
                action.getResponseRef(),
                action.getPayload(),
                action.getCreatedAt(),
                action.getUpdatedAt(),
                action.getExecutingAt(),
                action.getCompletedAt(),
                action.getFailedAt(),
                action.getNextAttemptAt(),
                action.getRetryWaitingAt(),
                action.getExecutorUnavailableAt(),
                action.getClaimedBy(),
                action.getClaimedAt(),
                action.getLeaseExpiresAt(),
                action.getWorkerHeartbeatAt(),
                action.getAttemptCount(),
                action.getMaxAttempts(),
                action.getExecutorName(),
                action.getLastError());
    }
}
