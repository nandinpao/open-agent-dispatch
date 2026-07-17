package com.opensocket.aievent.core.task;

import java.util.List;

import com.opensocket.aievent.core.decision.DecisionAction;

public record TaskSuppressionDecision(
        boolean suppressed,
        String reason,
        List<DecisionAction> actions,
        String existingTaskId,
        TaskType taskType
) {
    public TaskSuppressionDecision {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public static TaskSuppressionDecision proceed() {
        return new TaskSuppressionDecision(false, null, List.of(), null, null);
    }

    public static TaskSuppressionDecision suppressed(String reason,
                                                     List<DecisionAction> actions,
                                                     String existingTaskId,
                                                     TaskType taskType) {
        return new TaskSuppressionDecision(true, reason, actions, existingTaskId, taskType);
    }
}
