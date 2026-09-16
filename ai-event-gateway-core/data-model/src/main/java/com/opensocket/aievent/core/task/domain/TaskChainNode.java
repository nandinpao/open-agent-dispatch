package com.opensocket.aievent.core.task.domain;

public record TaskChainNode(
        String taskId,
        String taskKey,
        String title,
        String status,
        String rootTaskId,
        String parentTaskId,
        String ownerDepartmentId,
        String executorDepartmentId,
        String executorDomainId,
        long version,
        int depth) {
}
