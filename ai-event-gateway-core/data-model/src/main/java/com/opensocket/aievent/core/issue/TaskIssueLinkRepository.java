package com.opensocket.aievent.core.issue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface TaskIssueLinkRepository {
    TaskIssueLink save(TaskIssueLink link);
    Optional<TaskIssueLink> findByTaskId(String taskId);
    List<TaskIssueLink> findByTaskIds(List<String> taskIds);
    List<TaskIssueLink> recent(int limit);
    String mode();

    default Map<String, TaskIssueLink> findByTaskIdsAsMap(List<String> taskIds) {
        return findByTaskIds(taskIds).stream()
                .filter(link -> link.getTaskId() != null && !link.getTaskId().isBlank())
                .collect(Collectors.toMap(TaskIssueLink::getTaskId, Function.identity(), (left, right) -> left, java.util.LinkedHashMap::new));
    }

    static TaskIssueLinkRepository noop() {
        return new TaskIssueLinkRepository() {
            @Override public TaskIssueLink save(TaskIssueLink link) { return link; }
            @Override public Optional<TaskIssueLink> findByTaskId(String taskId) { return Optional.empty(); }
            @Override public List<TaskIssueLink> findByTaskIds(List<String> taskIds) { return List.of(); }
            @Override public List<TaskIssueLink> recent(int limit) { return List.of(); }
            @Override public String mode() { return "NOOP"; }
        };
    }
}
