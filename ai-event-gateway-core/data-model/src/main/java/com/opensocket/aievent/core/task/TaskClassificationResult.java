package com.opensocket.aievent.core.task;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;

/** Result of applying an A2A TRIAGE classification result. */
public record TaskClassificationResult(
        String parentTaskId,
        String rootTaskId,
        String correlationId,
        String classificationVersion,
        String idempotencyKey,
        int a2aDepth,
        int maxA2ADepth,
        boolean cycleDetected,
        boolean coreOwnedTaskCreation,
        boolean agentCreatedTask,
        boolean manualReviewRequired,
        String nextAction,
        String governanceReason,
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
                                              AssignmentDecisionResult assignment,
                                              String rootTaskId,
                                              String correlationId,
                                              String classificationVersion,
                                              String idempotencyKey,
                                              int a2aDepth,
                                              int maxA2ADepth,
                                              boolean cycleDetected,
                                              boolean manualReviewRequired,
                                              String nextAction,
                                              String governanceReason) {
        AssignmentDecisionResult a = assignment == null ? AssignmentDecisionResult.none("Resolution assignment not evaluated") : assignment;
        return new TaskClassificationResult(
                parent == null ? null : parent.getTaskId(),
                rootTaskId,
                correlationId,
                classificationVersion,
                idempotencyKey,
                a2aDepth,
                maxA2ADepth,
                cycleDetected,
                true,
                false,
                manualReviewRequired,
                nextAction,
                governanceReason,
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

    public static TaskClassificationResult of(TaskRecord parent,
                                              TaskRecord resolution,
                                              boolean created,
                                              AssignmentDecisionResult assignment) {
        String rootTaskId = parent == null ? null : (parent.getParentTaskId() == null || parent.getParentTaskId().isBlank() ? parent.getTaskId() : parent.getParentTaskId());
        return of(parent, resolution, created, assignment, rootTaskId, parent == null ? null : parent.getCorrelationId(),
                "A2A_CLASSIFICATION_V1", null, 0, 3, false, false, created ? "ASSIGN_CHILD_TASK" : "NO_CHILD_TASK", "Legacy factory fallback");
    }
}
