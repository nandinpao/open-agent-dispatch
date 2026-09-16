package com.opensocket.aievent.core.task.domain;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.task.TaskLifecycleTransitionGuard;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.lineage.TaskInvestigationView;
import com.opensocket.aievent.core.task.lineage.TaskLineageEvidence;
import com.opensocket.aievent.core.task.lineage.TaskLineageRepository;
import com.opensocket.aievent.core.task.lineage.TaskLineageQuery;

@Service
public class TaskDomainService {
    private final TaskRepository tasks;
    private final TaskRelationshipRepository relationships;
    private final TaskParticipantRepository participants;
    private final TaskStateHistoryRepository stateHistory;
    private List<TaskCompletionGuard> completionGuards = List.of();
    private TaskLineageRepository lineage;

    public TaskDomainService(TaskRepository tasks,
                             TaskRelationshipRepository relationships,
                             TaskParticipantRepository participants,
                             TaskStateHistoryRepository stateHistory) {
        this.tasks = tasks;
        this.relationships = relationships;
        this.participants = participants;
        this.stateHistory = stateHistory;
    }

    @Autowired(required = false)
    public void setLineageRepository(TaskLineageRepository lineage) { this.lineage = lineage; }

    @Autowired(required = false)
    public void setCompletionGuards(List<TaskCompletionGuard> guards) {
        this.completionGuards = guards == null ? List.of() : List.copyOf(guards);
    }

    @Transactional(readOnly = true)
    public TaskChainView chain(String tenantId, String taskId, int limit) {
        TaskRecord requested = requireTask(tenantId, taskId);
        String rootTaskId = firstNonBlank(requested.getRootTaskId(), requested.getTaskId());
        List<TaskRecord> records = new ArrayList<>(tasks.findByRootTaskId(tenantId, rootTaskId, cap(limit)));
        if (records.stream().noneMatch(task -> taskId.equals(task.getTaskId()))) records.add(requested);
        Map<String, TaskRecord> byId = new LinkedHashMap<>();
        records.forEach(task -> byId.put(task.getTaskId(), task));
        List<TaskChainNode> nodes = records.stream()
                .map(task -> new TaskChainNode(
                        task.getTaskId(), firstNonBlank(task.getTaskKey(), task.getTaskId()),
                        firstNonBlank(task.getTitle(), "Task " + task.getTaskId()),
                        task.getStatus() == null ? null : task.getStatus().name(),
                        firstNonBlank(task.getRootTaskId(), rootTaskId), task.getParentTaskId(),
                        task.getOwnerDepartmentId(), task.getExecutorDepartmentId(), task.getExecutorDomainId(),
                        task.getVersion(), depth(task, byId)))
                .sorted(Comparator.comparingInt(TaskChainNode::depth).thenComparing(TaskChainNode::taskId))
                .toList();
        return new TaskChainView(tenantId, taskId, rootTaskId, nodes);
    }

    @Transactional(readOnly = true)
    public List<TaskLineageEvidence> lineage(String tenantId, String taskId, int limit) {
        TaskRecord requested = requireTask(tenantId, taskId);
        if (lineage == null) return List.of();
        String rootTaskId = firstNonBlank(requested.getRootTaskId(), requested.getTaskId());
        return lineage.findByRootTask(tenantId, rootTaskId, cap(limit));
    }

    @Transactional(readOnly = true)
    public List<TaskLineageEvidence> lineageByAgent(String tenantId, String agentId, int limit) {
        if (lineage == null || agentId == null || agentId.isBlank()) return List.of();
        return lineage.findByAgent(tenantId, agentId.trim(), cap(limit));
    }

    @Transactional(readOnly = true)
    public List<TaskLineageEvidence> lineageByCorrelation(String tenantId, String correlationId, int limit) {
        if (lineage == null || correlationId == null || correlationId.isBlank()) return List.of();
        return lineage.findByCorrelation(tenantId, correlationId.trim(), cap(limit));
    }

    @Transactional(readOnly = true)
    public List<TaskLineageEvidence> searchLineage(TaskLineageQuery query) {
        if (lineage == null || query == null || query.getTenantId() == null || query.getTenantId().isBlank()) return List.of();
        return lineage.search(query);
    }

    @Transactional(readOnly = true)
    public TaskInvestigationView investigation(String tenantId, String taskId, int limit) {
        TaskRecord task = requireTask(tenantId, taskId);
        return new TaskInvestigationView(tenantId, taskId, task, chain(tenantId, taskId, limit), lineage(tenantId, taskId, limit));
    }

    @Transactional(readOnly = true)
    public List<TaskRelationship> relationships(String tenantId, String taskId,
                                                 TaskRelationshipQueryDirection direction, int limit) {
        requireTask(tenantId, taskId);
        TaskRelationshipQueryDirection effective = direction == null ? TaskRelationshipQueryDirection.BOTH : direction;
        if (effective == TaskRelationshipQueryDirection.OUTBOUND) return relationships.findOutbound(tenantId, taskId, cap(limit));
        if (effective == TaskRelationshipQueryDirection.INBOUND) return relationships.findInbound(tenantId, taskId, cap(limit));
        Map<String, TaskRelationship> combined = new LinkedHashMap<>();
        relationships.findOutbound(tenantId, taskId, cap(limit)).forEach(value -> combined.put(value.getRelationshipId(), value));
        relationships.findInbound(tenantId, taskId, cap(limit)).forEach(value -> combined.put(value.getRelationshipId(), value));
        return combined.values().stream().limit(cap(limit)).toList();
    }

    @Transactional
    public List<TaskRelationship> addRelationships(String tenantId,
                                                   String fromTaskId,
                                                   List<String> targetTaskIds,
                                                   TaskRelationshipType type,
                                                   TaskRelationshipDirection direction,
                                                   String reasonCode,
                                                   String description,
                                                   TaskReferenceVisibility visibility,
                                                   String actorId,
                                                   String idempotencyKey) {
        requireTask(tenantId, fromTaskId);
        if (type == null) throw new IllegalArgumentException("relationshipType is required");
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        if (targetTaskIds != null) targetTaskIds.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).limit(100).forEach(targets::add);
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one targetTaskId is required");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<TaskRelationship> created = new ArrayList<>();
        for (String targetTaskId : targets) {
            if (fromTaskId.equals(targetTaskId)) throw new IllegalArgumentException("TASK_RELATIONSHIP_SELF_REFERENCE");
            requireTask(tenantId, targetTaskId);
            String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim() + ":" + targetTaskId;
            Optional<TaskRelationship> replay = relationships.findByIdempotencyKey(tenantId, effectiveKey);
            if (replay.isPresent()) {
                TaskRelationship prior = replay.get();
                if (!fromTaskId.equals(prior.getFromTaskId()) || !targetTaskId.equals(prior.getToTaskId()) || type != prior.getRelationshipType()) {
                    throw new IllegalStateException("IDEMPOTENCY_KEY_SCOPE_CONFLICT");
                }
                created.add(prior); continue;
            }
            TaskRelationship value = new TaskRelationship();
            value.setTenantId(tenantId); value.setRelationshipId("tr-" + UUID.randomUUID());
            value.setFromTaskId(fromTaskId); value.setToTaskId(targetTaskId); value.setRelationshipType(type);
            value.setDirection(direction == null ? TaskRelationshipDirection.DIRECTED : direction);
            value.setReasonCode(trim(reasonCode)); value.setDescription(trim(description));
            value.setReferenceVisibility(visibility == null ? TaskReferenceVisibility.VISIBLE : visibility); value.setIdempotencyKey(effectiveKey);
            value.setCreatedByType(TaskActorType.USER); value.setCreatedById(firstNonBlank(actorId, "unknown-operator"));
            value.setCreatedAt(now); value.setVersion(1L);
            created.add(relationships.save(value));
        }
        return List.copyOf(created);
    }

    @Transactional
    public void removeRelationship(String tenantId, String taskId, String relationshipId) {
        requireTask(tenantId, taskId);
        Optional<TaskRelationship> existing = relationships.findById(tenantId, relationshipId);
        if (existing.isEmpty()) return; // DELETE is idempotent.
        TaskRelationship relationship = existing.get();
        if (!taskId.equals(relationship.getFromTaskId()) && !taskId.equals(relationship.getToTaskId())) {
            throw new IllegalArgumentException("Task relationship is not attached to Task: " + taskId);
        }
        if (!relationships.delete(tenantId, relationshipId)) throw new IllegalStateException("TASK_RELATIONSHIP_DELETE_CONFLICT");
    }

    @Transactional(readOnly = true)
    public List<TaskParticipant> participants(String tenantId, String taskId, int limit) {
        requireTask(tenantId, taskId);
        return participants.findByTask(tenantId, taskId, cap(limit));
    }

    @Transactional
    public TaskParticipant addParticipant(String tenantId, String taskId,
                                          TaskParticipantType type, String participantRefId,
                                          TaskParticipantRole role,
                                          TaskParticipantVisibilityLevel visibility,
                                          TaskParticipantOperationLevel operation,
                                          String actorId, String idempotencyKey) {
        requireTask(tenantId, taskId);
        if (type == null || role == null || participantRefId == null || participantRefId.isBlank()) {
            throw new IllegalArgumentException("participantType, participantRefId and participantRole are required");
        }
        String keyMaterial = tenantId + ":participant:" + firstNonBlank(idempotencyKey, UUID.randomUUID().toString());
        String participantId = "tp-" + UUID.nameUUIDFromBytes(keyMaterial.getBytes(StandardCharsets.UTF_8));
        Optional<TaskParticipant> replay = participants.findById(tenantId, participantId);
        if (replay.isPresent()) {
            TaskParticipant prior = replay.get();
            if (!taskId.equals(prior.getTaskId()) || type != prior.getParticipantType() || role != prior.getParticipantRole()
                    || !participantRefId.trim().equals(prior.getParticipantRefId())) {
                throw new IllegalStateException("IDEMPOTENCY_KEY_SCOPE_CONFLICT");
            }
            return prior;
        }
        TaskParticipant value = new TaskParticipant();
        value.setTenantId(tenantId); value.setParticipantId(participantId); value.setTaskId(taskId);
        value.setParticipantType(type); value.setParticipantRefId(participantRefId.trim()); value.setParticipantRole(role);
        value.setVisibilityLevel(visibility == null ? TaskParticipantVisibilityLevel.STANDARD : visibility);
        value.setOperationLevel(operation == null ? TaskParticipantOperationLevel.READ_ONLY : operation);
        value.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC)); value.setCreatedBy(firstNonBlank(actorId, "unknown-operator")); value.setVersion(1L);
        return participants.save(value);
    }

    @Transactional(readOnly = true)
    public List<TaskStateHistoryEntry> stateHistory(String tenantId, String taskId, int limit) {
        requireTask(tenantId, taskId);
        return stateHistory.findByTask(tenantId, taskId, cap(limit));
    }

    @Transactional
    public TaskRecord transition(TaskStateTransitionCommand command) {
        if (command == null || command.tenantId() == null || command.taskId() == null || command.newStatus() == null) {
            throw new IllegalArgumentException("tenantId, taskId and newStatus are required");
        }
        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<TaskStateHistoryEntry> replay = stateHistory.findByIdempotencyKey(command.tenantId(), command.idempotencyKey().trim());
            if (replay.isPresent()) {
                if (!command.taskId().equals(replay.get().getTaskId())) throw new IllegalStateException("IDEMPOTENCY_KEY_SCOPE_CONFLICT");
                return requireTask(command.tenantId(), command.taskId());
            }
        }
        TaskRecord before = requireTask(command.tenantId(), command.taskId());
        TaskStatus previousStatus = before.getStatus();
        if (before.getVersion() != command.expectedVersion()) {
            throw new IllegalStateException("RESOURCE_VERSION_CONFLICT currentVersion=" + before.getVersion() + " expectedVersion=" + command.expectedVersion());
        }
        TaskLifecycleTransitionGuard.requireTransition(before.getStatus(), command.newStatus(), command.taskId());
        if (command.newStatus().canonical() == TaskStatus.SUCCEEDED || command.newStatus() == TaskStatus.COMPLETED) {
            completionGuards.forEach(guard -> guard.requireCompletionAllowed(before, command.newStatus(), command.correlationId()));
        }
        TaskStateTransitionCommand normalized = new TaskStateTransitionCommand(
                command.tenantId(), command.taskId(), command.expectedVersion(), command.newStatus(),
                firstNonBlank(command.reasonCode(), "TASK_STATUS_CHANGED"), firstNonBlank(command.reason(), "Task status changed"),
                command.actorType() == null ? TaskActorType.USER : command.actorType(), firstNonBlank(command.actorId(), "unknown-operator"),
                command.correlationId(), command.idempotencyKey(),
                command.transitionAt() == null ? OffsetDateTime.now(ZoneOffset.UTC) : command.transitionAt());
        Optional<TaskRecord> transitionResult = tasks.transitionGovernanceState(normalized);
        if (transitionResult.isEmpty() && normalized.idempotencyKey() != null && !normalized.idempotencyKey().isBlank()) {
            Optional<TaskStateHistoryEntry> concurrentReplay = stateHistory.findByIdempotencyKey(normalized.tenantId(), normalized.idempotencyKey());
            if (concurrentReplay.isPresent() && normalized.taskId().equals(concurrentReplay.get().getTaskId())) {
                return requireTask(normalized.tenantId(), normalized.taskId());
            }
        }
        TaskRecord updated = transitionResult
                .orElseThrow(() -> new IllegalStateException("RESOURCE_VERSION_CONFLICT_OR_ILLEGAL_TRANSITION"));
        if ("MEMORY".equals(stateHistory.mode())) {
            TaskStateHistoryEntry entry = new TaskStateHistoryEntry();
            entry.setTenantId(command.tenantId()); entry.setHistoryId("tsh-" + UUID.randomUUID()); entry.setTaskId(command.taskId());
            entry.setFromStatus(previousStatus); entry.setToStatus(command.newStatus()); entry.setReasonCode(normalized.reasonCode()); entry.setReason(normalized.reason());
            entry.setActorType(normalized.actorType()); entry.setActorId(normalized.actorId()); entry.setCorrelationId(normalized.correlationId());
            entry.setTransitionAt(normalized.transitionAt()); entry.setTaskVersion(updated.getVersion()); entry.setIdempotencyKey(normalized.idempotencyKey());
            stateHistory.save(entry);
        }
        return updated;
    }

    private TaskRecord requireTask(String tenantId, String taskId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId is required");
        return tasks.findByTenantAndId(tenantId.trim(), taskId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Task not found in Tenant: " + taskId));
    }
    private int depth(TaskRecord task, Map<String, TaskRecord> byId) {
        int depth = 0; String parentId = task.getParentTaskId(); LinkedHashSet<String> visited = new LinkedHashSet<>();
        while (parentId != null && !parentId.isBlank() && depth < 100 && visited.add(parentId)) {
            depth++; TaskRecord parent = byId.get(parentId); if (parent == null) break; parentId = parent.getParentTaskId();
        }
        return depth;
    }
    private int cap(int limit){return Math.max(1,Math.min(limit,1000));}
    private String trim(String value){return value==null||value.isBlank()?null:value.trim();}
    private String firstNonBlank(String... values){if(values!=null)for(String value:values)if(value!=null&&!value.isBlank())return value.trim();return null;}
}
