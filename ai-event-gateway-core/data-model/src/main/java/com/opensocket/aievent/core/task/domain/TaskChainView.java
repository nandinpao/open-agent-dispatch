package com.opensocket.aievent.core.task.domain;

import java.util.List;

public record TaskChainView(
        String tenantId,
        String requestedTaskId,
        String rootTaskId,
        List<TaskChainNode> nodes) {
    public TaskChainView {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }
}
