package com.opensocket.aievent.core.api;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecision;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecisionMode;
import com.opensocket.aievent.core.resourceaccess.contract.DecisionEffect;
import com.opensocket.aievent.core.resourceaccess.contract.OperationPhase;
import com.opensocket.aievent.core.resourceaccess.contract.RequestChannel;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceEnforcementCommand;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.SecurityEpoch;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.lineage.TaskLineageEvidence;
import com.opensocket.aievent.core.task.lineage.TaskLineageQuery;
import com.opensocket.aievent.core.task.lineage.TaskFailureDomain;
import com.opensocket.aievent.core.task.domain.TaskActorType;
import com.opensocket.aievent.core.task.domain.TaskChainNode;
import com.opensocket.aievent.core.task.domain.TaskChainView;
import com.opensocket.aievent.core.task.domain.TaskDomainService;
import com.opensocket.aievent.core.task.domain.TaskParticipant;
import com.opensocket.aievent.core.task.domain.TaskParticipantOperationLevel;
import com.opensocket.aievent.core.task.domain.TaskParticipantRole;
import com.opensocket.aievent.core.task.domain.TaskParticipantType;
import com.opensocket.aievent.core.task.domain.TaskParticipantVisibilityLevel;
import com.opensocket.aievent.core.task.domain.TaskReferenceVisibility;
import com.opensocket.aievent.core.task.domain.TaskRelationship;
import com.opensocket.aievent.core.task.domain.TaskRelationshipDirection;
import com.opensocket.aievent.core.task.domain.TaskRelationshipQueryDirection;
import com.opensocket.aievent.core.task.domain.TaskRelationshipType;
import com.opensocket.aievent.core.task.domain.TaskStateHistoryEntry;
import com.opensocket.aievent.core.task.domain.TaskStateTransitionCommand;

/** Current Task Domain API with per-node P4RA-E Task Chain authorization. */
@RestController
@RequestMapping("/api/tasks")
public class TaskDomainController {
    private final TaskDomainService taskDomainService;
    @Autowired(required = false)
    private ResourceAccessEnforcementPort resourceAccess;
    @Value("${resource-access.task-enabled:false}")
    private boolean taskResourceAccessEnabled;

    public TaskDomainController(TaskDomainService taskDomainService) {
        this.taskDomainService = taskDomainService;
    }

    @GetMapping("/{taskId}/chain")
    public TaskChainView chain(@PathVariable String taskId,
            @RequestParam(defaultValue = "100") int limit) {
        authorize(ResourceType.TASK_CHAIN, taskId, "task.read", ResourceAction.ActionKind.READ,
                false, VisibilityLevel.METADATA, "TASK_CHAIN_READ");
        TaskChainView raw = execute(() -> taskDomainService.chain(tenantId(), taskId, limit));
        if (!taskResourceAccessEnabled || resourceAccess == null) return raw;
        List<TaskChainNode> nodes = raw.nodes().stream().map(this::authorizedNode).toList();
        return new TaskChainView(raw.tenantId(), raw.requestedTaskId(), raw.rootTaskId(), nodes);
    }

    @GetMapping("/{taskId}/lineage")
    public List<TaskLineageEvidence> lineage(@PathVariable String taskId,
            @RequestParam(defaultValue = "250") int limit) {
        requireFormalLineageResourceAccess();
        AuthorizationDecision chainDecision = authorize(ResourceType.TASK_CHAIN, taskId, "task.read", ResourceAction.ActionKind.READ,
                false, VisibilityLevel.METADATA, "TASK_LINEAGE_READ");
        if (!formal(chainDecision) || chainDecision.effect() != DecisionEffect.ALLOW) throw formalLineageUnavailable();
        List<TaskLineageEvidence> raw = execute(() -> taskDomainService.lineage(tenantId(), taskId, limit));
        return visibleLineage(raw);
    }

    @GetMapping("/lineage/agents/{agentId}")
    public List<TaskLineageEvidence> lineageByAgent(@PathVariable String agentId,
            @RequestParam(defaultValue = "250") int limit) {
        requireFormalLineageResourceAccess();
        List<TaskLineageEvidence> raw = execute(() -> taskDomainService.lineageByAgent(tenantId(), agentId, limit));
        return visibleLineage(raw);
    }

    @GetMapping("/lineage/correlation/{correlationId}")
    public List<TaskLineageEvidence> lineageByCorrelation(@PathVariable String correlationId,
            @RequestParam(defaultValue = "250") int limit) {
        requireFormalLineageResourceAccess();
        List<TaskLineageEvidence> raw = execute(() -> taskDomainService.lineageByCorrelation(tenantId(), correlationId, limit));
        return visibleLineage(raw);
    }

    @GetMapping("/lineage/search")
    public List<TaskLineageEvidence> searchLineage(
            @RequestParam(required=false) String rootTaskId,
            @RequestParam(required=false) String taskId,
            @RequestParam(required=false) String agentId,
            @RequestParam(required=false) String correlationId,
            @RequestParam(required=false) String principalType,
            @RequestParam(required=false) String principalId,
            @RequestParam(required=false) String credentialId,
            @RequestParam(required=false) String departmentId,
            @RequestParam(required=false) String groupId,
            @RequestParam(required=false) TaskFailureDomain failureDomain,
            @RequestParam(defaultValue="250") int limit) {
        requireFormalLineageResourceAccess();
        TaskLineageQuery query = new TaskLineageQuery(); query.setTenantId(tenantId()); query.setRootTaskId(rootTaskId);
        query.setTaskId(taskId); query.setAgentId(agentId); query.setCorrelationId(correlationId); query.setPrincipalType(principalType);
        query.setPrincipalId(principalId); query.setCredentialId(credentialId); query.setDepartmentId(departmentId); query.setGroupId(groupId);
        query.setFailureDomain(failureDomain); query.setLimit(limit);
        return visibleLineage(execute(() -> taskDomainService.searchLineage(query)));
    }

    @GetMapping("/{taskId}/relationships")
    public List<TaskRelationshipView> relationships(@PathVariable String taskId,
            @RequestParam(defaultValue = "BOTH") TaskRelationshipQueryDirection direction,
            @RequestParam(defaultValue = "100") int limit) {
        AuthorizationDecision decision = authorize(ResourceType.TASK, taskId, "task.read",
                ResourceAction.ActionKind.READ, false, VisibilityLevel.SUMMARY, "TASK_RELATIONSHIP_READ");
        boolean summaryOnly = formal(decision) && decision.grantedVisibility().ordinal() <= VisibilityLevel.SUMMARY.ordinal();
        return execute(() -> taskDomainService.relationships(tenantId(), taskId, direction, limit).stream()
                .map(value -> TaskRelationshipView.from(taskId, value, summaryOnly)).toList());
    }

    @PostMapping("/{taskId}/relationships")
    public List<TaskRelationshipView> addRelationships(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody AddRelationshipsRequest request) {
        if (request == null) throw badRequest("Request body is required.");
        authorize(ResourceType.TASK, taskId, "task.update", ResourceAction.ActionKind.UPDATE,
                true, VisibilityLevel.STANDARD, "TASK_RELATIONSHIP_CREATE");
        return execute(() -> taskDomainService.addRelationships(tenantId(), taskId, request.targetTaskIds(),
                request.relationshipType(), request.direction(), request.reasonCode(), request.description(),
                request.referenceVisibility(), actorId(), requireHeader(idempotencyKey, "Idempotency-Key")).stream()
                .map(value -> TaskRelationshipView.from(taskId, value, false)).toList());
    }

    @DeleteMapping("/{taskId}/relationships/{relationshipId}")
    public MutationResult removeRelationship(@PathVariable String taskId, @PathVariable String relationshipId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireHeader(idempotencyKey, "Idempotency-Key");
        authorize(ResourceType.TASK, taskId, "task.update", ResourceAction.ActionKind.DELETE,
                true, VisibilityLevel.STANDARD, "TASK_RELATIONSHIP_DELETE");
        execute(() -> { taskDomainService.removeRelationship(tenantId(), taskId, relationshipId); return null; });
        return new MutationResult(relationshipId, "DELETED", correlationId());
    }

    @GetMapping("/{taskId}/participants")
    public List<TaskParticipant> participants(@PathVariable String taskId,
            @RequestParam(defaultValue = "100") int limit) {
        authorize(ResourceType.TASK, taskId, "task.read", ResourceAction.ActionKind.READ,
                false, VisibilityLevel.SENSITIVE, "TASK_PARTICIPANT_READ");
        return execute(() -> taskDomainService.participants(tenantId(), taskId, limit));
    }

    @PostMapping("/{taskId}/participants")
    public TaskParticipant addParticipant(@PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody AddParticipantRequest request) {
        if (request == null) throw badRequest("Request body is required.");
        requireHeader(idempotencyKey, "Idempotency-Key");
        authorize(ResourceType.TASK, taskId, "task.update", ResourceAction.ActionKind.MANAGE,
                true, VisibilityLevel.SENSITIVE, "TASK_PARTICIPANT_CREATE");
        return execute(() -> taskDomainService.addParticipant(tenantId(), taskId, request.participantType(),
                request.participantRefId(), request.participantRole(), request.visibilityLevel(),
                request.operationLevel(), actorId(), requireHeader(idempotencyKey, "Idempotency-Key")));
    }

    @GetMapping("/{taskId}/state-history")
    public List<TaskStateHistoryEntry> stateHistory(@PathVariable String taskId,
            @RequestParam(defaultValue = "100") int limit) {
        authorize(ResourceType.TASK, taskId, "task.read", ResourceAction.ActionKind.READ,
                false, VisibilityLevel.STANDARD, "TASK_STATE_HISTORY_READ");
        return execute(() -> taskDomainService.stateHistory(tenantId(), taskId, limit));
    }

    @PostMapping("/{taskId}/transitions")
    public TaskRecord transition(@PathVariable String taskId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TransitionRequest request) {
        if (request == null || request.newStatus() == null) throw badRequest("newStatus is required.");
        authorize(ResourceType.TASK, taskId, "task.update", ResourceAction.ActionKind.UPDATE,
                true, VisibilityLevel.STANDARD, "TASK_STATE_TRANSITION");
        long expectedVersion = parseVersion(ifMatch);
        TaskStateTransitionCommand command = new TaskStateTransitionCommand(tenantId(), taskId, expectedVersion,
                request.newStatus(), request.reasonCode(), request.reason(), TaskActorType.USER, actorId(),
                correlationId(), requireHeader(idempotencyKey, "Idempotency-Key"), OffsetDateTime.now());
        return execute(() -> taskDomainService.transition(command));
    }

    private List<TaskLineageEvidence> visibleLineage(List<TaskLineageEvidence> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        requireFormalLineageResourceAccess();
        return raw.stream().map(this::authorizedEvidence).filter(java.util.Objects::nonNull).toList();
    }

    private TaskLineageEvidence authorizedEvidence(TaskLineageEvidence evidence) {
        if (evidence == null || evidence.getTaskId() == null) return null;
        AuthorizationDecision decision;
        try {
            decision = assess(ResourceType.TASK, evidence.getTaskId(), "task.read", ResourceAction.ActionKind.READ,
                    false, VisibilityLevel.METADATA, "TASK_LINEAGE_NODE_READ");
        } catch (RuntimeException exception) {
            return null;
        }
        if (!formal(decision) || decision.effect() != DecisionEffect.ALLOW) return null;
        if (decision.grantedVisibility().ordinal() >= VisibilityLevel.SENSITIVE.ordinal()) return evidence;
        TaskLineageEvidence redacted = new TaskLineageEvidence();
        redacted.setTenantId(evidence.getTenantId()); redacted.setEvidenceId(evidence.getEvidenceId());
        redacted.setRootTaskId(evidence.getRootTaskId()); redacted.setTaskId(evidence.getTaskId()); redacted.setParentTaskId(evidence.getParentTaskId());
        redacted.setEventType(evidence.getEventType()); redacted.setOriginPrincipalType(evidence.getOriginPrincipalType());
        redacted.setActorPrincipalType(evidence.getActorPrincipalType()); redacted.setExecutorAgentId(evidence.getExecutorAgentId());
        redacted.setDepartmentId(evidence.getDepartmentId()); redacted.setGroupId(evidence.getGroupId());
        redacted.setFailureDomain(evidence.getFailureDomain()); redacted.setOccurredAt(evidence.getOccurredAt());
        if (decision.grantedVisibility().ordinal() >= VisibilityLevel.STANDARD.ordinal()) {
            redacted.setOriginPrincipalId(evidence.getOriginPrincipalId()); redacted.setActorPrincipalId(evidence.getActorPrincipalId());
            redacted.setSourceSystem(evidence.getSourceSystem()); redacted.setFailureCode(evidence.getFailureCode());
        }
        return redacted;
    }

    private TaskChainNode authorizedNode(TaskChainNode node) {
        AuthorizationDecision decision;
        try {
            decision = assess(ResourceType.TASK, node.taskId(), "task.read", ResourceAction.ActionKind.READ,
                    false, VisibilityLevel.METADATA, "TASK_CHAIN_NODE_READ");
        } catch (RuntimeException exception) {
            return placeholder(node);
        }
        if (!formal(decision)) return node; // SHADOW preserves legacy response while recording evidence.
        if (decision.effect() != DecisionEffect.ALLOW) return placeholder(node);
        VisibilityLevel visibility = decision.grantedVisibility();
        if (visibility.ordinal() >= VisibilityLevel.STANDARD.ordinal()) return node;
        if (visibility == VisibilityLevel.SUMMARY) {
            return new TaskChainNode(node.taskId(), node.taskKey(), node.title(), node.status(), node.rootTaskId(),
                    node.parentTaskId(), null, null, null, node.version(), node.depth());
        }
        return new TaskChainNode(node.taskId(), "RESTRICTED", "Restricted task", safeStatus(node.status()),
                node.rootTaskId(), node.parentTaskId(), null, null, null, node.version(), node.depth());
    }

    private TaskChainNode placeholder(TaskChainNode node) {
        String opaque = "restricted-" + UUID.nameUUIDFromBytes(
                (tenantId() + ":" + node.taskId()).getBytes(StandardCharsets.UTF_8)).toString();
        return new TaskChainNode(opaque, "RESTRICTED", "Restricted downstream task", safeStatus(node.status()),
                opaqueRoot(node.rootTaskId()), null, null, null, null, 1L, node.depth());
    }

    private String opaqueRoot(String rootTaskId) {
        return "restricted-root-" + UUID.nameUUIDFromBytes(
                (tenantId() + ":" + rootTaskId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String safeStatus(String status) {
        if (status == null) return "UNKNOWN";
        return switch (status) {
            case "SUCCEEDED", "COMPLETED" -> "COMPLETED";
            case "FAILED", "TIMED_OUT", "EXPIRED", "CANCELLED" -> "TERMINAL";
            case "WAIT_HUMAN", "RECONCILING", "ORPHANED" -> "BLOCKED";
            default -> "ACTIVE";
        };
    }

    private AuthorizationDecision authorize(ResourceType type, String id, String permission,
            ResourceAction.ActionKind kind, boolean sideEffecting, VisibilityLevel visibility, String purpose) {
        if (!taskResourceAccessEnabled) return null;
        if (resourceAccess == null) throw enforcementUnavailable();
        return resourceAccess.authorize(command(type, id, permission, kind, sideEffecting, visibility, purpose));
    }

    private AuthorizationDecision assess(ResourceType type, String id, String permission,
            ResourceAction.ActionKind kind, boolean sideEffecting, VisibilityLevel visibility, String purpose) {
        if (!taskResourceAccessEnabled) return null;
        if (resourceAccess == null) throw enforcementUnavailable();
        return resourceAccess.assess(command(type, id, permission, kind, sideEffecting, visibility, purpose));
    }

    private ResourceEnforcementCommand command(ResourceType type, String id, String permission,
            ResourceAction.ActionKind kind, boolean sideEffecting, VisibilityLevel visibility, String purpose) {
        return new ResourceEnforcementCommand(new ResourceAction(permission, kind, sideEffecting),
                new ResourceRef(tenantId(), type, id), visibility, RequestChannel.REST, purpose,
                OperationPhase.START, SecurityEpoch.ZERO, Map.of());
    }

    private static boolean formal(AuthorizationDecision decision) {
        return decision != null && decision.mode() == AuthorizationDecisionMode.FORMAL;
    }

    private String tenantId() {
        String tenantId = context().tenantId();
        if (tenantId == null || tenantId.isBlank()) throw badRequest("tenantId is required.");
        return tenantId.trim();
    }
    private String actorId() {
        String actor = context().operatorId();
        return actor == null || actor.isBlank() ? "unknown-operator" : actor.trim();
    }
    private String correlationId() {
        OpenDispatchRequestContext context = context();
        String value = context.correlationId();
        if (value != null && !value.isBlank()) return value.trim();
        String requestId = context.requestId();
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();
    }
    private OpenDispatchRequestContext context() {
        return OpenDispatchRequestContextHolder.current().orElseThrow(() -> badRequest("Request context is required."));
    }
    private String requireHeader(String value, String name) {
        if (value == null || value.isBlank()) throw badRequest(name + " is required.");
        return value.trim();
    }
    private long parseVersion(String ifMatch) {
        String raw = requireHeader(ifMatch, "If-Match").replace("W/", "").replace("\"", "").trim();
        try { return Long.parseLong(raw); }
        catch (NumberFormatException ex) { throw badRequest("If-Match must contain the numeric Task version."); }
    }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException enforcementUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Resource Access enforcement guard is unavailable.");
    }
    private void requireFormalLineageResourceAccess() {
        if (!taskResourceAccessEnabled || resourceAccess == null) throw formalLineageUnavailable();
    }
    private ResponseStatusException formalLineageUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Task lineage requires formal Resource Access enforcement; shadow or disabled enforcement cannot expose investigation evidence.");
    }
    private <T> T execute(Operation<T> operation) {
        try { return operation.run(); }
        catch (ResponseStatusException ex) { throw ex; }
        catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Task not found"))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }
    @FunctionalInterface private interface Operation<T> { T run(); }

    public record AddRelationshipsRequest(List<String> targetTaskIds, TaskRelationshipType relationshipType,
            TaskRelationshipDirection direction, String reasonCode, String description,
            TaskReferenceVisibility referenceVisibility) {}
    public record AddParticipantRequest(TaskParticipantType participantType, String participantRefId,
            TaskParticipantRole participantRole, TaskParticipantVisibilityLevel visibilityLevel,
            TaskParticipantOperationLevel operationLevel) {}
    public record TransitionRequest(TaskStatus newStatus, String reasonCode, String reason) {}
    public record MutationResult(String resourceId, String status, String correlationId) {}
    public record TaskRelationshipView(String relationshipId, String relationshipType, String direction,
            String fromTaskId, String toTaskId, String reasonCode, String description,
            String referenceVisibility, boolean restrictedReference, OffsetDateTime createdAt, long version) {
        static TaskRelationshipView from(String requestedTaskId, TaskRelationship value, boolean forceOpaque) {
            boolean opaque = forceOpaque || value.getReferenceVisibility() == TaskReferenceVisibility.OPAQUE;
            String from = opaque && !requestedTaskId.equals(value.getFromTaskId()) ? null : value.getFromTaskId();
            String to = opaque && !requestedTaskId.equals(value.getToTaskId()) ? null : value.getToTaskId();
            return new TaskRelationshipView(value.getRelationshipId(), value.getRelationshipType().name(),
                    (value.getDirection() == null ? TaskRelationshipDirection.DIRECTED : value.getDirection()).name(),
                    from, to, opaque ? null : value.getReasonCode(), opaque ? null : value.getDescription(),
                    (opaque ? TaskReferenceVisibility.OPAQUE : value.getReferenceVisibility() == null
                            ? TaskReferenceVisibility.VISIBLE : value.getReferenceVisibility()).name(),
                    opaque, value.getCreatedAt(), value.getVersion());
        }
    }
}
