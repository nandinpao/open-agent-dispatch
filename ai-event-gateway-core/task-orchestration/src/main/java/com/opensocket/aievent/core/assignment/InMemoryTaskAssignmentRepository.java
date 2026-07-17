package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "assignment", name = "store", havingValue = "MEMORY")
public class InMemoryTaskAssignmentRepository implements TaskAssignmentRepository {
    private final ConcurrentHashMap<String, TaskAssignment> assignments = new ConcurrentHashMap<>();

    @Override
    public TaskAssignment save(TaskAssignment assignment) {
        assignments.put(assignment.getAssignmentId(), assignment);
        return assignment;
    }

    @Override
    public Optional<TaskAssignment> findById(String assignmentId) {
        return Optional.ofNullable(assignments.get(assignmentId));
    }

    @Override
    public Optional<TaskAssignment> findOpenByTaskId(String taskId) {
        return assignments.values().stream()
                .filter(assignment -> taskId.equals(assignment.getTaskId()))
                .filter(assignment -> assignment.getStatus() == AssignmentStatus.ASSIGNED || assignment.getStatus() == AssignmentStatus.AWAITING_REVIEW)
                .max(Comparator.comparing(TaskAssignment::getCreatedAt));
    }

    @Override
    public boolean releaseCapacityReservation(String assignmentId, OffsetDateTime releasedAt) {
        boolean[] released = {false};
        assignments.computeIfPresent(assignmentId, (id, assignment) -> {
            synchronized (assignment) {
                if (assignment.isCapacityReserved()) {
                    assignment.setCapacityReserved(false);
                    assignment.setCapacityReleasedAt(releasedAt);
                    assignment.setUpdatedAt(releasedAt);
                    released[0] = true;
                }
                return assignment;
            }
        });
        return released[0];
    }

    @Override
    public List<TaskAssignment> findByTaskId(String taskId, int limit) {
        return assignments.values().stream()
                .filter(assignment -> taskId.equals(assignment.getTaskId()))
                .sorted(Comparator.comparing(TaskAssignment::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public List<TaskAssignment> recent(int limit) {
        return assignments.values().stream()
                .sorted(Comparator.comparing(TaskAssignment::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public String mode() { return "MEMORY"; }
}
