package com.opensocket.aievent.core.timeline;

import java.time.OffsetDateTime;
import java.util.Map;

import com.opensocket.aievent.core.routing.DispatchUserFacingError;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskPriority;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.TaskType;

/**
 * TODO 15-E compact item for Admin Failure Queue.
 */
public record AdminFailureQueueItem(
        String taskId,
        String incidentId,
        TaskType taskType,
        TaskStatus status,
        TaskPriority priority,
        String tenantId,
        String siteId,
        String plantId,
        String objectType,
        String objectId,
        String errorCode,
        String reasonCategory,
        String blockedReason,
        String failureReason,
        String dispatchWaitReason,
        String lifecycleReason,
        String dispatchRetryReason,
        int dispatchAttemptCount,
        OffsetDateTime nextDispatchAttemptAt,
        OffsetDateTime terminalAt,
        OffsetDateTime updatedAt,
        RoutingDecisionRecord latestRoutingDecision,
        DispatchUserFacingError userFacingDispatchError,
        DispatchTimelineEvent latestTimelineEvent,
        Map<String, Object> actions
) {
    public AdminFailureQueueItem(String taskId,
                                 String incidentId,
                                 TaskType taskType,
                                 TaskStatus status,
                                 TaskPriority priority,
                                 String tenantId,
                                 String siteId,
                                 String plantId,
                                 String objectType,
                                 String objectId,
                                 String errorCode,
                                 String lifecycleReason,
                                 String dispatchRetryReason,
                                 int dispatchAttemptCount,
                                 OffsetDateTime nextDispatchAttemptAt,
                                 OffsetDateTime terminalAt,
                                 OffsetDateTime updatedAt,
                                 DispatchTimelineEvent latestTimelineEvent,
                                 Map<String, Object> actions) {
        this(taskId, incidentId, taskType, status, priority, tenantId, siteId, plantId, objectType, objectId,
                errorCode, null, null, null, null, lifecycleReason, dispatchRetryReason, dispatchAttemptCount,
                nextDispatchAttemptAt, terminalAt, updatedAt, null, null, latestTimelineEvent, actions);
    }
}
