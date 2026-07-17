package com.opensocket.aievent.core.task;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;

/** Read-only query and operational boundary owned by Task Orchestration. */
public interface TaskOperationalQuery {
    Optional<TaskRecord> findTask(String taskId);
    List<TaskRecord> searchTasks(TaskQuery query);
    List<TaskRecord> findTasksByIncident(String incidentId, int limit);
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
