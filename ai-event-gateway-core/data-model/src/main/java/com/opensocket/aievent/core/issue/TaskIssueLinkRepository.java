package com.opensocket.aievent.core.issue;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface TaskIssueLinkRepository {
    TaskIssueLink save(TaskIssueLink link);
    Optional<TaskIssueLink> findByTenantAndLinkId(String tenantId, String linkId);
    Optional<TaskIssueLink> findByTenantAndIdempotencyKey(String tenantId, String idempotencyKey);
    List<TaskIssueLink> findAllByTenantAndTaskId(String tenantId, String taskId);
    Optional<TaskIssueLink> findByExternalIssue(String tenantId,String connectionId,String externalProjectId,String externalIssueId);
    List<TaskIssueLink> findAllByTenantAndTaskIds(String tenantId, List<String> taskIds);
    List<TaskIssueLink> recent(String tenantId, int limit);
    String mode();

    /** Compatibility read: returns the primary or most recently updated link. */
    default Optional<TaskIssueLink> findByTaskId(String taskId) {
        return findAllByTenantAndTaskId(null, taskId).stream()
                .sorted(Comparator.comparing((TaskIssueLink v) -> "PRIMARY".equals(v.getLinkRole())).reversed()
                        .thenComparing(TaskIssueLink::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }
    default List<TaskIssueLink> findByTaskIds(List<String> taskIds) { return findAllByTenantAndTaskIds(null, taskIds); }
    default List<TaskIssueLink> recent(int limit) { return recent(null, limit); }

    default Map<String, TaskIssueLink> findByTaskIdsAsMap(List<String> taskIds) {
        return findByTaskIds(taskIds).stream()
                .filter(link -> link.getTaskId() != null && !link.getTaskId().isBlank())
                .collect(Collectors.toMap(TaskIssueLink::getTaskId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    static TaskIssueLinkRepository noop() {
        return new TaskIssueLinkRepository() {
            @Override public TaskIssueLink save(TaskIssueLink link) { return link; }
            @Override public Optional<TaskIssueLink> findByTenantAndLinkId(String tenantId,String linkId){return Optional.empty();}
            @Override public Optional<TaskIssueLink> findByTenantAndIdempotencyKey(String tenantId,String key){return Optional.empty();}
            @Override public List<TaskIssueLink> findAllByTenantAndTaskId(String tenantId,String taskId){return List.of();}
            @Override public Optional<TaskIssueLink> findByExternalIssue(String tenantId,String connectionId,String projectId,String issueId){return Optional.empty();}
            @Override public List<TaskIssueLink> findAllByTenantAndTaskIds(String tenantId,List<String> taskIds){return List.of();}
            @Override public List<TaskIssueLink> recent(String tenantId,int limit){return List.of();}
            @Override public String mode() { return "NOOP"; }
        };
    }
}
