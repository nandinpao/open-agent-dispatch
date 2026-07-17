package com.opensocket.aievent.core.task;

import java.util.List;

import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.decision.DecisionAction;

public record TaskDecisionResult(
        boolean taskCreated,
        boolean taskSuppressed,
        String taskId,
        TaskType taskType,
        String reason,
        List<DecisionAction> actions,
        boolean assignmentCreated,
        String assignmentId,
        String selectedAgentId,
        String selectedGatewayNodeId,
        String selectedSiteId,
        String routingDecisionId,
        String assignmentStatus,
        String assignmentReason,
        boolean dispatchRequestCreated,
        String dispatchRequestId,
        String dispatchStatus,
        String dispatchReviewMode,
        String dispatchEligibilityStatus,
        String dispatchGatewayPath,
        String dispatchReason
) {
    public static TaskDecisionResult suppressed(String reason, List<DecisionAction> actions) {
        return new TaskDecisionResult(false, true, null, null, reason, List.copyOf(actions),
                false, null, null, null, null, null, null, null,
                false, null, null, null, null, null, null);
    }

    public static TaskDecisionResult none(String reason, List<DecisionAction> actions) {
        return new TaskDecisionResult(false, false, null, null, reason, List.copyOf(actions),
                false, null, null, null, null, null, null, null,
                false, null, null, null, null, null, null);
    }

    public static TaskDecisionResult created(TaskRecord task, String reason, List<DecisionAction> actions, AssignmentDecisionResult assignment) {
        AssignmentDecisionResult a = assignment == null ? AssignmentDecisionResult.none("Assignment routing not evaluated") : assignment;
        return new TaskDecisionResult(true, false, task.getTaskId(), task.getTaskType(), reason, List.copyOf(actions),
                a.assignmentCreated(), a.assignmentId(), a.selectedAgentId(), a.selectedGatewayNodeId(), a.selectedSiteId(), a.routingDecisionId(), a.assignmentStatus(), a.reason(),
                a.dispatchRequestCreated(), a.dispatchRequestId(), a.dispatchStatus(), a.dispatchReviewMode(), a.dispatchEligibilityStatus(), a.dispatchGatewayPath(), a.dispatchReason());
    }
}
