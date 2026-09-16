package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.http.context.OpenDispatchRequestContext;
import com.opensocket.aievent.core.http.context.OpenDispatchRequestContextHolder;
import com.opensocket.aievent.core.integration.handoff.AgentContextAccessEvent;
import com.opensocket.aievent.core.integration.handoff.AgentTaskContext;
import com.opensocket.aievent.core.integration.handoff.HandoffApprovalDecision;
import com.opensocket.aievent.core.integration.handoff.HandoffAttachmentMetadata;
import com.opensocket.aievent.core.integration.handoff.HandoffContextCheckpoint;
import com.opensocket.aievent.core.integration.handoff.HandoffContextField;
import com.opensocket.aievent.core.integration.handoff.HandoffContextPolicy;
import com.opensocket.aievent.core.integration.handoff.HandoffContextPreview;
import com.opensocket.aievent.core.integration.handoff.HandoffContextReadiness;
import com.opensocket.aievent.core.integration.handoff.HandoffContextService;
import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;
import com.opensocket.aievent.core.integration.handoff.HandoffReleaseEvidence;
import com.opensocket.aievent.core.integration.handoff.HandoffReconciliationSummary;
import com.opensocket.aievent.core.integration.handoff.ResultContextSnapshot;
import com.opensocket.aievent.core.organization.SensitivityLevel;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecision;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecisionMode;
import com.opensocket.aievent.core.resourceaccess.contract.OperationPhase;
import com.opensocket.aievent.core.resourceaccess.contract.RequestChannel;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceEnforcementCommand;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.SecurityEpoch;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;

/** English-only Current API for immutable A2A Handoff Context. */
@RestController
@RequestMapping
public class HandoffContextController {
    private final HandoffContextService service;
    @Autowired(required = false)
    private ResourceAccessEnforcementPort resourceAccess;
    @Value("${resource-access.a2a-enabled:false}")
    private boolean a2aResourceAccessEnabled;

    public HandoffContextController(HandoffContextService service) {
        this.service = service;
    }

    @GetMapping("/api/handoff-context/policies")
    public List<HandoffContextPolicy> policies(
            @RequestParam(defaultValue = "200") int limit) {
        return run(() -> service.policies(tenant(), limit));
    }

    @GetMapping("/api/handoff-context/policies/{policyId}")
    public HandoffContextPolicy policy(@PathVariable String policyId) {
        return run(() -> service.policy(tenant(), policyId));
    }

    @PutMapping("/api/handoff-context/policies/{policyId}")
    public HandoffContextPolicy policy(
            @PathVariable String policyId,
            @RequestHeader(value = "If-Match", required = false) Long expected,
            @RequestBody HandoffContextPolicy body) {
        return run(() -> service.savePolicy(tenant(), policyId, body, expected));
    }

    @PostMapping("/api/tasks/{taskId}/handoff-context/preview")
    public HandoffContextPreview preview(
            @PathVariable String taskId,
            @RequestBody PreviewRequest body) {
        authorize(ResourceType.TASK, taskId, "a2a.context.preview", ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "HANDOFF_CONTEXT_PREVIEW");
        return run(() -> service.preview(
                tenant(),
                taskId,
                body.targetTaskId(),
                body.policyId(),
                body.summary(),
                body.sourceContext(),
                body.fields(),
                body.commentRefs(),
                body.attachments(),
                body.sensitivityLevel()));
    }

    @PostMapping("/api/tasks/{taskId}/handoff-context")
    public HandoffContextSnapshot create(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody CreateSnapshotRequest body) {
        required(key, "Idempotency-Key");
        authorize(ResourceType.TASK, taskId, "a2a.context.create", ResourceAction.ActionKind.CREATE, true, VisibilityLevel.SENSITIVE, "HANDOFF_CONTEXT_CREATE");
        if (!taskId.equals(body.preview().sourceTaskId())) {
            throw bad("Path Task must equal preview sourceTaskId.");
        }
        return run(() -> service.createSnapshot(
                tenant(),
                body.rootTaskId(),
                body.preview(),
                actorType(),
                operator(),
                correlation(),
                body.expiresAt()));
    }

    @GetMapping("/api/tasks/{taskId}/handoff-context/readiness")
    public HandoffContextReadiness readiness(
            @PathVariable String taskId,
            @RequestParam String policyId,
            @RequestParam(defaultValue = "DISPATCH") HandoffContextCheckpoint checkpoint) {
        authorize(ResourceType.TASK, taskId, "a2a.context.read", ResourceAction.ActionKind.READ, false, VisibilityLevel.SUMMARY, "HANDOFF_CONTEXT_READINESS");
        return run(() -> service.readiness(tenant(), taskId, policyId, checkpoint));
    }

    @GetMapping("/api/tasks/{taskId}/handoff-contexts")
    public List<HandoffContextSnapshot> snapshots(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "200") int limit) {
        AuthorizationDecision decision = authorize(ResourceType.TASK, taskId, "a2a.context.read",
                ResourceAction.ActionKind.READ, false, VisibilityLevel.SUMMARY, "HANDOFF_CONTEXT_LIST");
        List<HandoffContextSnapshot> values = run(() -> service.snapshots(tenant(), taskId, limit));
        return formal(decision) ? values.stream().map(value -> redact(value, decision.grantedVisibility())).toList() : values;
    }

    @GetMapping("/api/handoff-contexts/{snapshotId}")
    public HandoffContextSnapshot snapshot(@PathVariable String snapshotId) {
        AuthorizationDecision decision = authorize(ResourceType.TASK_CONTEXT_SNAPSHOT, snapshotId, "a2a.context.read",
                ResourceAction.ActionKind.READ, false, VisibilityLevel.SUMMARY, "HANDOFF_CONTEXT_READ");
        HandoffContextSnapshot value = run(() -> service.snapshot(tenant(), snapshotId));
        return formal(decision) ? redact(value, decision.grantedVisibility()) : value;
    }

    @GetMapping("/api/handoff-contexts/{snapshotId}/release-evidence")
    public List<HandoffReleaseEvidence> releaseEvidence(
            @PathVariable String snapshotId,
            @RequestParam(defaultValue = "200") int limit) {
        authorize(ResourceType.TASK_CONTEXT_SNAPSHOT, snapshotId, "a2a.context.read", ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "HANDOFF_RELEASE_EVIDENCE_READ");
        return run(() -> service.releaseEvidence(tenant(), snapshotId, limit));
    }

    @PostMapping("/api/handoff-contexts/{snapshotId}/retry-release")
    public HandoffContextSnapshot retryRelease(
            @PathVariable String snapshotId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) ReleaseRetryRequest body) {
        required(key, "Idempotency-Key");
        ReleaseRetryRequest request = body == null ? new ReleaseRetryRequest(null) : body;
        authorize(ResourceType.TASK_CONTEXT_SNAPSHOT, snapshotId, "a2a.context.release", ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "HANDOFF_RELEASE_RETRY");
        return run(() -> service.retryRelease(
                tenant(), snapshotId, actorType(), operator(), request.reason()));
    }

    @PostMapping("/internal/handoff-context/reconcile")
    public HandoffReconciliationSummary reconcile(
            @RequestParam(defaultValue = "100") int limit) {
        return run(() -> service.reconcileDue(limit));
    }

    @PostMapping("/api/handoff-contexts/{snapshotId}/approve")
    public HandoffContextSnapshot approve(
            @PathVariable String snapshotId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) ApprovalRequest body) {
        required(key, "Idempotency-Key");
        ApprovalRequest request = body == null ? new ApprovalRequest(null) : body;
        authorize(ResourceType.TASK_CONTEXT_SNAPSHOT, snapshotId, "a2a.context.approve", ResourceAction.ActionKind.APPROVE, true, VisibilityLevel.SENSITIVE, "HANDOFF_CONTEXT_APPROVE");
        return run(() -> service.approve(
                tenant(),
                snapshotId,
                HandoffApprovalDecision.APPROVE,
                actorType(),
                operator(),
                request.reason(),
                key,
                correlation()));
    }

    @PostMapping("/api/handoff-contexts/{snapshotId}/reject")
    public HandoffContextSnapshot reject(
            @PathVariable String snapshotId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody(required = false) ApprovalRequest body) {
        required(key, "Idempotency-Key");
        ApprovalRequest request = body == null ? new ApprovalRequest(null) : body;
        authorize(ResourceType.TASK_CONTEXT_SNAPSHOT, snapshotId, "a2a.context.reject", ResourceAction.ActionKind.APPROVE, true, VisibilityLevel.SENSITIVE, "HANDOFF_CONTEXT_REJECT");
        return run(() -> service.approve(
                tenant(),
                snapshotId,
                HandoffApprovalDecision.REJECT,
                actorType(),
                operator(),
                request.reason(),
                key,
                correlation()));
    }

    @PostMapping("/api/handoff-contexts/{snapshotId}/supersede")
    public HandoffContextSnapshot supersede(
            @PathVariable String snapshotId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody HandoffContextPreview preview) {
        required(key, "Idempotency-Key");
        authorize(ResourceType.TASK_CONTEXT_SNAPSHOT, snapshotId, "a2a.context.create", ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "HANDOFF_CONTEXT_SUPERSEDE");
        return run(() -> service.supersede(
                tenant(),
                snapshotId,
                preview,
                actorType(),
                operator(),
                correlation()));
    }

    @PostMapping("/api/tasks/{taskId}/result-context")
    public ResultContextSnapshot result(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody ResultSnapshotRequest body) {
        required(key, "Idempotency-Key");
        authorize(ResourceType.TASK, taskId, "a2a.result.create", ResourceAction.ActionKind.CREATE, true, VisibilityLevel.SENSITIVE, "RESULT_CONTEXT_CREATE");
        if (!taskId.equals(body.targetTaskId())) {
            throw bad("Path Task must equal targetTaskId.");
        }
        return run(() -> service.createResultSnapshot(
                tenant(),
                body.sourceTaskId(),
                body.targetTaskId(),
                body.policyId(),
                body.summary(),
                body.evidenceRefs(),
                body.output(),
                body.omittedReasons(),
                body.agentId(),
                correlation()));
    }

    @GetMapping("/api/agent/tasks/{taskId}/context")
    public AgentTaskContext agentContext(
            @PathVariable String taskId,
            @RequestHeader("X-Agent-Id") String agentId,
            @RequestHeader("X-Assignment-Id") String assignmentId,
            @RequestHeader(value = "X-Agent-Session-Id", required = false) String sessionId,
            @RequestHeader("X-Dispatch-Token") String token) {
        return run(() -> service.agentContext(
                tenant(),
                taskId,
                agentId,
                assignmentId,
                sessionId,
                token,
                context().clientAddress(),
                correlation()));
    }

    @GetMapping("/api/tasks/{taskId}/context-access-audit")
    public List<AgentContextAccessEvent> accessEvents(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "200") int limit) {
        authorize(ResourceType.TASK, taskId, "a2a.context.audit.read", ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "CONTEXT_ACCESS_AUDIT_READ");
        return run(() -> service.accessEvents(tenant(), taskId, limit));
    }

    private AuthorizationDecision authorize(ResourceType type, String id, String permission,
            ResourceAction.ActionKind kind, boolean sideEffecting, VisibilityLevel visibility, String purpose) {
        if (!a2aResourceAccessEnabled) return null;
        if (resourceAccess == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resource Access enforcement guard is unavailable.");
        }
        return resourceAccess.authorize(new ResourceEnforcementCommand(
                new ResourceAction(permission, kind, sideEffecting), new ResourceRef(tenant(), type, id),
                visibility, RequestChannel.REST, purpose, OperationPhase.START, SecurityEpoch.ZERO, Map.of()));
    }

    private static boolean formal(AuthorizationDecision decision) {
        return decision != null && decision.mode() == AuthorizationDecisionMode.FORMAL;
    }

    private static HandoffContextSnapshot redact(HandoffContextSnapshot value, VisibilityLevel visibility) {
        if (value == null || visibility == null || visibility.ordinal() >= VisibilityLevel.STANDARD.ordinal()) return value;
        boolean metadata = visibility == VisibilityLevel.METADATA || visibility == VisibilityLevel.NONE;
        return new HandoffContextSnapshot(value.tenantId(), value.snapshotId(), value.aggregateId(), value.schemaVersion(),
                value.rootTaskId(), value.sourceTaskId(), value.targetTaskId(), metadata ? null : value.sourceAgentId(),
                metadata ? null : value.targetAgentId(), metadata ? null : value.targetDomainId(), null,
                value.contextPolicyId(), value.policyVersion(), value.snapshotVersion(), metadata ? "Restricted handoff context" : value.summary(),
                Map.of(), List.of(), List.of(), value.redactedFieldPaths(), value.omittedContentReasons(),
                value.sensitivityLevel(), value.contentHash(), value.sourceObservedAt(), value.createdAt(),
                value.createdByType(), metadata ? null : value.createdById(), value.expiresAt(), value.status(),
                metadata ? null : value.approvedBy(), value.approvedAt(), null, value.supersedesSnapshotId(),
                null, List.of(), value.releaseStatus(), value.releaseEvidenceId(), value.releasedAt(),
                value.lastReleaseErrorCode(), value.reconciliationClassification(), value.nextReconcileAt(),
                value.reconciliationCount(), value.rowVersion());
    }

    private String tenant() {
        return required(context().tenantId(), "tenantId");
    }

    private String operator() {
        return required(context().operatorId(), "operatorId");
    }

    private String actorType() {
        return "anonymous".equals(operator()) ? "SYSTEM" : "USER";
    }

    private String correlation() {
        String value = context().correlationId();
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private OpenDispatchRequestContext context() {
        return OpenDispatchRequestContextHolder.current()
                .orElseThrow(() -> bad("Request context is required."));
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw bad(name + " is required.");
        }
        return value.trim();
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private <T> T run(Operation<T> operation) {
        try {
            return operation.get();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().contains("not found in Tenant")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
            }
            throw bad(exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T get();
    }

    public record PreviewRequest(
            String targetTaskId,
            String policyId,
            String summary,
            Map<String, Object> sourceContext,
            List<HandoffContextField> fields,
            List<String> commentRefs,
            List<HandoffAttachmentMetadata> attachments,
            SensitivityLevel sensitivityLevel) {
    }

    /**
     * Issue-link fields are retained only for wire and schema compatibility.
     * They are not required for snapshot authorization or Agent context access.
     */
    public record CreateSnapshotRequest(
            String rootTaskId,
            HandoffContextPreview preview,
            OffsetDateTime expiresAt) {
    }

    public record ApprovalRequest(String reason) {
    }

    public record ReleaseRetryRequest(String reason) {
    }

    public record ResultSnapshotRequest(
            String sourceTaskId,
            String targetTaskId,
            String policyId,
            String summary,
            List<String> evidenceRefs,
            Map<String, Object> output,
            List<String> omittedReasons,
            String agentId) {
    }
}
