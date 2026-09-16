package com.opensocket.aievent.core.resourceaccess.contract;

/** Stable non-sensitive placeholder for a Task Chain node the current principal cannot inspect. */
public record RestrictedTaskChainNode(
        String taskId,
        String parentTaskId,
        String rootTaskId,
        String statusCategory,
        int depth,
        String visibility) {
    public RestrictedTaskChainNode {
        taskId = required(taskId, "taskId");
        rootTaskId = required(rootTaskId, "rootTaskId");
        parentTaskId = parentTaskId == null ? "" : parentTaskId.trim();
        statusCategory = statusCategory == null || statusCategory.isBlank() ? "UNKNOWN" : statusCategory.trim();
        if (depth < 0) throw new IllegalArgumentException("depth must be non-negative");
        visibility = "HIDDEN_NODE_PLACEHOLDER";
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
