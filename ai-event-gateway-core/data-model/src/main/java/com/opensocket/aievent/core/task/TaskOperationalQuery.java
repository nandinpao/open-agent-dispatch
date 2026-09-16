package com.opensocket.aievent.core.task;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.resourceaccess.contract.TaskScopeQueryPlan;

/** Read-only query and operational boundary owned by Task Orchestration. */
public interface TaskOperationalQuery {
    Optional<TaskRecord> findTask(String taskId);
    default Optional<TaskRecord> findTask(String tenantId, String taskId) {
        return findTask(taskId).filter(task -> tenantId != null && tenantId.equals(task.getTenantId()));
    }
    List<TaskRecord> searchTasks(TaskQuery query);
    default List<TaskRecord> searchTasks(TaskQuery query, TaskScopeQueryPlan plan) {
        return searchTasks(query);
    }
    default List<TaskRecord> searchTasksTarget(TaskQuery query, TaskScopeQueryPlan plan) {
        return searchTasks(query, plan);
    }
    /** Indexed Task-family query used by operational read models. Implementations must not tenant-scan in memory. */
    default List<TaskRecord> findTaskFamily(String tenantId, String rootTaskId, int limit) {
        return List.of();
    }

    /** Direct-child query backed by the Task parent index; depth-1 relationship views must prefer this over family scans. */
    default List<TaskRecord> findTaskChildren(String tenantId, String parentTaskId, int limit) {
        return List.of();
    }
    List<TaskRecord> findTasksByIncident(String incidentId, int limit);
    default List<TaskRecord> findTasksByIncident(String tenantId, String incidentId, int limit) {
        return findTasksByIncident(incidentId, limit).stream()
                .filter(task -> tenantId != null && tenantId.equals(task.getTenantId())).toList();
    }
    Optional<TaskAssignment> findAssignment(String assignmentId);
    List<TaskAssignment> recentAssignments(int limit);
    List<TaskAssignment> findAssignmentsByTask(String taskId, int limit);
    Optional<RoutingDecisionRecord> findRoutingDecision(String decisionId);
    List<RoutingDecisionRecord> recentRoutingDecisions(int limit);
    List<RoutingDecisionRecord> findRoutingDecisionsByTask(String taskId, int limit);
    Map<String, Integer> taskStatusCounts(int limit);
    String taskStoreMode();
    String assignmentStoreMode();
    String routingStoreMode();
}
