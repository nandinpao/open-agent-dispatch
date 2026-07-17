package com.opensocket.aievent.core.task;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;

/** Result of applying a Phase 32-E TRIAGE classification result. */
public record TaskClassificationResult(
        String parentTaskId,
        String parentStatus,
        String classificationStatus,
        String classificationResultJson,
        boolean resolutionTaskCreated,
        String resolutionTaskId,
        String resolutionTaskType,
        String resolutionEventType,
        String resolutionObjectType,
        String resolutionErrorCode,
        String matchedFlowId,
        String matchedRuleId,
        String targetPoolId,
        String routingPath,
        boolean assignmentCreated,
        String assignmentId,
        String selectedAgentId,
        String assignmentStatus,
        String assignmentReason
) {
    public static TaskClassificationResult of(TaskRecord parent,
                                              TaskRecord resolution,
                                              boolean created,
                                              AssignmentDecisionResult assignment) {
        AssignmentDecisionResult a = assignment == null ? AssignmentDecisionResult.none("Resolution assignment not evaluated") : assignment;
        return new TaskClassificationResult(
                parent == null ? null : parent.getTaskId(),
                parent == null || parent.getStatus() == null ? null : parent.getStatus().name(),
                parent == null ? null : parent.getClassificationStatus(),
                parent == null ? null : parent.getClassificationResultJson(),
                created,
                resolution == null ? null : resolution.getTaskId(),
                resolution == null || resolution.getTaskType() == null ? null : resolution.getTaskType().name(),
                resolution == null ? null : resolution.getEventType(),
                resolution == null ? null : resolution.getObjectType(),
                resolution == null ? null : resolution.getErrorCode(),
                resolution == null ? null : resolution.getMatchedFlowId(),
                resolution == null ? null : resolution.getMatchedRuleId(),
                resolution == null ? null : resolution.getTargetPoolId(),
                resolution == null ? null : resolution.getRoutingPath(),
                a.assignmentCreated(),
                a.assignmentId(),
                a.selectedAgentId(),
                a.assignmentStatus(),
                a.reason());
    }
}
