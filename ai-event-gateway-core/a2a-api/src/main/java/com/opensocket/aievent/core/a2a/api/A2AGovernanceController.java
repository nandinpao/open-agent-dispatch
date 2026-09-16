package com.opensocket.aievent.core.a2a.api;

import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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

import com.opensocket.aievent.core.a2a.A2ACancellationEvidence;
import com.opensocket.aievent.core.a2a.A2AGovernanceScopeClass;
import com.opensocket.aievent.core.a2a.A2AGovernanceScopeClassifier;
import com.opensocket.aievent.core.a2a.A2ACancellationRecord;
import com.opensocket.aievent.core.a2a.A2ACancellationReliabilitySnapshot;
import com.opensocket.aievent.core.a2a.A2ALateResultResolution;
import com.opensocket.aievent.core.a2a.A2APolicy;
import com.opensocket.aievent.core.a2a.A2APolicyVisibilityScope;
import com.opensocket.aievent.core.a2a.A2AReconciliationCase;
import com.opensocket.aievent.core.a2a.A2AReconciliationStatus;
import com.opensocket.aievent.core.a2a.A2ARequest;
import com.opensocket.aievent.core.a2a.A2ARequestCommand;
import com.opensocket.aievent.core.a2a.A2ARequesterType;
import com.opensocket.aievent.core.a2a.A2AResultQuarantine;
import com.opensocket.aievent.core.a2a.A2AStateHistoryEntry;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACancellationUseCase;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACancellationReliabilityUseCase;
import com.opensocket.aievent.core.a2a.application.port.in.A2AGovernanceUseCase;
import com.opensocket.aievent.core.a2a.application.port.in.A2ALateResultGovernanceUseCase;
import com.opensocket.aievent.core.a2a.A2ARejectedException;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecision;
import com.opensocket.aievent.core.resourceaccess.contract.AuthorizationDecisionMode;
import com.opensocket.aievent.core.resourceaccess.contract.OperationPhase;
import com.opensocket.aievent.core.resourceaccess.contract.RequestChannel;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAccessEnforcementPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlanPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceOwnershipCandidate;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceScopeAssignmentGuardPort;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceEnforcementCommand;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceRef;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.SecurityEpoch;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.iam.security.contract.MachineExecutionContext;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipalType;

/** Phase 0 A2A archive/read adapter. Directional policy writes and new delegation are retired. */
@RestController
@RequestMapping("/api")
public class A2AGovernanceController {
    private final A2AGovernanceUseCase governance;
    private final A2ACancellationUseCase cancellation;
    private final A2ACancellationReliabilityUseCase cancellationReliability;
    private final A2ALateResultGovernanceUseCase lateResults;
    private final A2AApiRequestContextAccessor contextAccessor;
    @Autowired(required = false)
    private ResourceAccessEnforcementPort resourceAccess;
    @Autowired(required = false)
    private ResourceListScopeQueryPlanPort resourceListScopes;
    @Autowired(required = false)
    private ResourceScopeAssignmentGuardPort scopeAssignmentGuard;
    @Value("${resource-access.a2a-enabled:false}")
    private boolean a2aResourceAccessEnabled;

    public A2AGovernanceController(
            A2AGovernanceUseCase governance,
            A2ACancellationUseCase cancellation,
            A2ACancellationReliabilityUseCase cancellationReliability,
            A2ALateResultGovernanceUseCase lateResults,
            A2AApiRequestContextAccessor contextAccessor) {
        this.governance = governance;
        this.cancellation = cancellation;
        this.cancellationReliability = cancellationReliability;
        this.lateResults = lateResults;
        this.contextAccessor = contextAccessor;
    }

    @GetMapping("/a2a-policies")
    public List<A2APolicy> policies(@RequestParam(required = false) String sourceDomainId,
                                    @RequestParam(required = false) String targetDomainId,
                                    @RequestParam(defaultValue = "200") int limit) {
        if (!a2aResourceAccessEnabled || resourceListScopes == null) {
            return execute(() -> governance.searchPolicies(tenantId(), sourceDomainId, targetDomainId, limit));
        }
        ResourceListScopeQueryPlan plan = resourceListScopes.build("a2a.policy.read", ResourceType.A2A_POLICY,
                VisibilityLevel.SENSITIVE, "A2A_POLICY_LIST");
        A2APolicyVisibilityScope scope = policyVisibilityScope(plan);
        return execute(() -> governance.searchPolicies(tenantId(), sourceDomainId, targetDomainId, limit, scope));
    }

    @GetMapping("/a2a-policies/{policyId}")
    public A2APolicy policy(@PathVariable String policyId) {
        authorize(ResourceType.A2A_POLICY, policyId, "a2a.policy.read", ResourceAction.ActionKind.READ,
                false, VisibilityLevel.SENSITIVE, "A2A_POLICY_READ", Map.of());
        return execute(() -> governance.getPolicy(tenantId(), policyId));
    }

    @PutMapping("/a2a-policies/{policyId}")
    public A2APolicy upsertPolicy(@PathVariable String policyId,
                                  @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                  @RequestBody A2APolicy body) {
        authorize(ResourceType.A2A_POLICY, policyId, "a2a.policy.manage", ResourceAction.ActionKind.UPDATE,
                true, VisibilityLevel.SENSITIVE, "A2A_POLICY_RETIRED", Map.of());
        throw new ResponseStatusException(HttpStatus.GONE,
                "A2A_LEGACY_ROUTING_RETIRED: Directional A2A policy mutation was removed by Phase 0. Existing rows are historical evidence only.");
    }

    @PostMapping("/tasks/{taskId}/a2a-requests")
    public A2ARequest request(@PathVariable String taskId,
                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                              @RequestBody CreateA2ARequest body) {
        if (body == null) throw badRequest("Request body is required.");
        authorize(ResourceType.TASK, taskId, "a2a.request.create", ResourceAction.ActionKind.CREATE,
                true, VisibilityLevel.STANDARD, "CAPABILITY_DELEGATION_PHASE0", Map.of());
        throw new ResponseStatusException(HttpStatus.GONE,
                "A2A_LEGACY_DIRECTIONAL_EXECUTION_RETIRED: Legacy directional request creation is retired. Use the canonical /capability-delegations endpoint.");
    }

    @GetMapping("/a2a-requests/{requestId}")
    public A2ARequest get(@PathVariable String requestId) {
        A2ARequest request = execute(() -> governance.get(tenantId(), requestId));
        AuthorizationDecision decision = authorize(ResourceType.A2A_REQUEST, requestId, "a2a.request.read",
                ResourceAction.ActionKind.READ, false, VisibilityLevel.SUMMARY, "A2A_REQUEST_READ", Map.of());
        return formal(decision) ? redact(request, decision.grantedVisibility()) : request;
    }

    @PostMapping("/a2a-requests/{requestId}/approve")
    public A2ARequest approve(@PathVariable String requestId,
                              @RequestHeader("Idempotency-Key") String key) {
        A2ARequest current = approvalCandidate(requestId);
        authorize(ResourceType.A2A_APPROVAL, requestId, "a2a.approval.approve",
                ResourceAction.ActionKind.APPROVE, true, VisibilityLevel.SENSITIVE, "A2A_APPROVAL",
                Map.of("requestedById", safe(current.getRequestedById()), "requestStatus", current.getRequestStatus().name()));
        throw new ResponseStatusException(HttpStatus.GONE,
                "A2A_LEGACY_DIRECTIONAL_EXECUTION_RETIRED: Legacy directional approval is retired. Existing rows are historical evidence only.");
    }

    @PostMapping("/a2a-requests/{requestId}/reject")
    public A2ARequest reject(@PathVariable String requestId,
                             @RequestHeader("Idempotency-Key") String key,
                             @RequestBody RejectA2ARequest body) {
        if (body == null) throw badRequest("Request body is required.");
        A2ARequest current = approvalCandidate(requestId);
        authorize(ResourceType.A2A_APPROVAL, requestId, "a2a.approval.reject",
                ResourceAction.ActionKind.APPROVE, true, VisibilityLevel.SENSITIVE, "A2A_REJECTION",
                Map.of("requestedById", safe(current.getRequestedById()), "requestStatus", current.getRequestStatus().name()));
        throw new ResponseStatusException(HttpStatus.GONE,
                "A2A_LEGACY_DIRECTIONAL_EXECUTION_RETIRED: Legacy directional rejection is retired. Existing rows are historical evidence only.");
    }

    @PostMapping("/a2a-requests/{requestId}/cancel")
    public A2ARequest cancel(@PathVariable String requestId,
                             @RequestHeader("Idempotency-Key") String key,
                             @RequestBody(required = false) CancelA2ARequest body) {
        String reason = body == null || body.reason() == null || body.reason().isBlank()
                ? "A2A cancellation requested" : body.reason();
        authorize(ResourceType.A2A_REQUEST, requestId, "a2a.request.cancel",
                ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.STANDARD, "A2A_CANCEL", Map.of());
        return execute(() -> cancellation.request(tenantId(), requestId, "USER", actorId(),
                reason, required(key, "Idempotency-Key")));
    }

    @GetMapping("/a2a-cancellations/{cancellationId}")
    public A2ACancellationRecord cancellation(@PathVariable String cancellationId) {
        A2ACancellationRecord value = execute(() -> cancellation.get(tenantId(), cancellationId));
        authorize(ResourceType.A2A_REQUEST, value.getRequestId(), "a2a.request.read",
                ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "A2A_CANCELLATION_READ", Map.of());
        return value;
    }

    @GetMapping("/a2a-cancellations/{cancellationId}/evidence")
    public List<A2ACancellationEvidence> cancellationEvidence(@PathVariable String cancellationId,
                                                               @RequestParam(defaultValue = "100") int limit) {
        A2ACancellationRecord value = execute(() -> cancellation.get(tenantId(), cancellationId));
        authorize(ResourceType.A2A_REQUEST, value.getRequestId(), "a2a.request.read",
                ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "A2A_CANCELLATION_EVIDENCE_READ", Map.of());
        return execute(() -> cancellation.evidence(tenantId(), cancellationId, limit));
    }

    @GetMapping("/a2a-cancellations/{cancellationId}/reliability")
    public A2ACancellationReliabilitySnapshot cancellationReliability(
            @PathVariable String cancellationId,
            @RequestParam(defaultValue = "100") int limit) {
        A2ACancellationRecord value = execute(() -> cancellation.get(tenantId(), cancellationId));
        authorize(ResourceType.A2A_REQUEST, value.getRequestId(), "a2a.request.read",
                ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "A2A_CANCELLATION_RELIABILITY_READ", Map.of());
        return execute(() -> cancellationReliability.reliability(
                tenantId(), cancellationId, limit));
    }

    @PostMapping("/a2a-cancellations/{cancellationId}/reconcile")
    public A2ACancellationRecord reconcileCancellation(
            @PathVariable String cancellationId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody ReconcileCancellation body) {
        if (body == null || body.reason() == null || body.reason().isBlank()) {
            throw badRequest("reason is required.");
        }
        A2ACancellationRecord value = execute(() -> cancellation.get(tenantId(), cancellationId));
        authorize(ResourceType.A2A_REQUEST, value.getRequestId(), "a2a.request.cancel",
                ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "A2A_CANCELLATION_RECONCILE", Map.of());
        return execute(() -> cancellationReliability.reconcile(tenantId(), cancellationId,
                actorId(), body.reason(), required(key, "Idempotency-Key")));
    }

    @GetMapping("/a2a-reconciliation-cases")
    public List<A2AReconciliationCase> reconciliationCases(@RequestParam(defaultValue = "100") int limit) {
        requireTenantWideOperation("a2a.reconciliation.read", ResourceType.A2A_REQUEST, "A2A_RECONCILIATION_LIST");
        return execute(() -> cancellation.openCases(tenantId(), limit));
    }

    @PostMapping("/a2a-reconciliation-cases/{caseId}/resolve")
    public A2AReconciliationCase resolveReconciliationCase(@PathVariable String caseId,
                                                            @RequestBody ResolveReconciliationCase body) {
        if (body == null || body.status() == null) throw badRequest("status is required.");
        requireTenantWideOperation("a2a.reconciliation.manage", ResourceType.A2A_REQUEST, "A2A_RECONCILIATION_RESOLVE");
        return execute(() -> cancellation.resolveCase(tenantId(), caseId, body.status(), actorId(), body.reason()));
    }

    @GetMapping("/a2a-result-quarantine")
    public List<A2AResultQuarantine> resultQuarantine(@RequestParam(defaultValue = "100") int limit) {
        requireTenantWideOperation("a2a.quarantine.read", ResourceType.A2A_REQUEST, "A2A_RESULT_QUARANTINE_LIST");
        return execute(() -> lateResults.open(tenantId(), limit));
    }

    @PostMapping("/a2a-result-quarantine/{quarantineId}/resolve")
    public A2AResultQuarantine resolveResultQuarantine(@PathVariable String quarantineId,
                                                        @RequestBody ResolveQuarantine body) {
        if (body == null || body.decision() == null) throw badRequest("decision is required.");
        requireTenantWideOperation("a2a.quarantine.manage", ResourceType.A2A_REQUEST, "A2A_RESULT_QUARANTINE_RESOLVE");
        return execute(() -> lateResults.resolve(tenantId(), quarantineId, body.decision(), actorId(), body.reason()));
    }

    @GetMapping("/tasks/{taskId}/a2a-history")
    public List<A2ARequest> history(@PathVariable String taskId,
                                    @RequestParam(defaultValue = "100") int limit) {
        AuthorizationDecision decision = authorize(ResourceType.TASK, taskId, "a2a.request.read",
                ResourceAction.ActionKind.READ, false, VisibilityLevel.SUMMARY, "A2A_HISTORY_READ", Map.of());
        List<A2ARequest> values = execute(() -> governance.history(tenantId(), taskId, limit));
        return formal(decision) ? values.stream().map(value -> redact(value, decision.grantedVisibility())).toList() : values;
    }

    @GetMapping("/a2a-requests/{requestId}/state-history")
    public List<A2AStateHistoryEntry> stateHistory(@PathVariable String requestId,
                                                   @RequestParam(defaultValue = "100") int limit) {
        authorize(ResourceType.A2A_REQUEST, requestId, "a2a.request.read", ResourceAction.ActionKind.READ,
                false, VisibilityLevel.STANDARD, "A2A_STATE_HISTORY_READ", Map.of());
        return execute(() -> governance.stateHistory(tenantId(), requestId, limit));
    }

    @GetMapping("/legacy/a2a/policies")
    public List<A2APolicy> legacyPolicies(@RequestParam(required = false) String sourceDomainId,
                                          @RequestParam(required = false) String targetDomainId,
                                          @RequestParam(defaultValue = "200") int limit) {
        return policies(sourceDomainId, targetDomainId, limit);
    }

    @GetMapping("/legacy/a2a/policies/{policyId}")
    public A2APolicy legacyPolicy(@PathVariable String policyId) {
        return policy(policyId);
    }

    @GetMapping("/legacy/a2a/requests/{requestId}")
    public A2ARequest legacyRequest(@PathVariable String requestId) {
        return get(requestId);
    }

    @GetMapping("/legacy/a2a/tasks/{taskId}/requests")
    public List<A2ARequest> legacyTaskRequests(@PathVariable String taskId,
                                               @RequestParam(defaultValue = "100") int limit) {
        return history(taskId, limit);
    }

    @GetMapping("/legacy/a2a/requests/{requestId}/state-history")
    public List<A2AStateHistoryEntry> legacyStateHistory(@PathVariable String requestId,
                                                         @RequestParam(defaultValue = "100") int limit) {
        return stateHistory(requestId, limit);
    }

    private A2APolicy existingPolicy(String policyId) {
        try { return governance.getPolicy(tenantId(), policyId); }
        catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("not found")) return null;
            throw exception;
        }
    }

    private static A2APolicyVisibilityScope policyVisibilityScope(ResourceListScopeQueryPlan plan) {
        return new A2APolicyVisibilityScope(plan.denyAll(), plan.tenantWide(), plan.exactDepartmentIds(),
                plan.subtreeDepartmentRootIds(), plan.groupIds(), plan.explicitResourceIds(), plan.excludedResourceIds(),
                plan.deniedDepartmentIds(), plan.deniedSubtreeDepartmentRootIds(), plan.deniedGroupIds());
    }

    private static boolean policyOrganizationScopeChanged(A2APolicy before, A2APolicy after) {
        return !sameNullableId(before.getSourceDepartmentId(), after.getSourceDepartmentId())
                || !sameNullableId(before.getSourceGroupId(), after.getSourceGroupId())
                || !sameNullableId(before.getTargetDepartmentId(), after.getTargetDepartmentId())
                || !sameNullableId(before.getTargetGroupId(), after.getTargetGroupId());
    }

    private static boolean policyResourceReferencesChanged(A2APolicy before, A2APolicy after) {
        return !sameNullableId(before.getSourceDomainId(), after.getSourceDomainId())
                || !sameNullableId(before.getTargetDomainId(), after.getTargetDomainId())
                || !sameNullableId(before.getSourceAgentPoolId(), after.getSourceAgentPoolId())
                || !sameNullableId(before.getTargetAgentPoolId(), after.getTargetAgentPoolId());
    }

    private void authorizePolicyReference(ResourceType type, String resourceId, String purpose) {
        if (blank(resourceId)) return;
        authorize(type, resourceId.trim(), "a2a.policy.manage", ResourceAction.ActionKind.UPDATE,
                true, VisibilityLevel.STANDARD, purpose, Map.of("policyReference", type.name()));
    }

    private static boolean sameNullableId(String left, String right) {
        String a = blank(left) ? "" : left.trim();
        String b = blank(right) ? "" : right.trim();
        return a.equals(b);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private A2ARequest approvalCandidate(String requestId) {
        A2ARequest current = execute(() -> governance.get(tenantId(), requestId));
        if (current.getRequestStatus() != com.opensocket.aievent.core.a2a.A2ARequestStatus.WAITING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A2A request is not waiting for approval.");
        }
        if ("USER".equalsIgnoreCase(current.getRequestedByType()) && actorId().equals(current.getRequestedById())) {
            throw new AccessDeniedException("A2A_APPROVAL_SEPARATION_OF_DUTIES_VIOLATION");
        }
        return current;
    }

    private A2AApiRequestContext context() {
        A2AApiRequestContext context = contextAccessor.current();
        if (context == null) throw badRequest("Request context is required.");
        return context;
    }


    private void requireTenantWideOperation(String permission, ResourceType type, String purpose) {
        if (!a2aResourceAccessEnabled) return;
        if (resourceListScopes == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resource Access list scope guard is unavailable.");
        }
        ResourceListScopeQueryPlan plan = resourceListScopes.build(permission, type, VisibilityLevel.SENSITIVE, purpose);
        if (plan.denyAll() || !plan.tenantWide()) {
            throw new AccessDeniedException("A2A_TENANT_WIDE_AUTHORITY_REQUIRED:" + permission);
        }
    }

    private AuthorizationDecision authorize(ResourceType type, String id, String permission,
            ResourceAction.ActionKind kind, boolean sideEffecting, VisibilityLevel visibility,
            String purpose, Map<String,String> trustedFlow) {
        if (!a2aResourceAccessEnabled) return null;
        if (resourceAccess == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resource Access enforcement guard is unavailable.");
        }
        return resourceAccess.authorize(new ResourceEnforcementCommand(
                new ResourceAction(permission, kind, sideEffecting), new ResourceRef(tenantId(), type, id),
                visibility, RequestChannel.REST, purpose, OperationPhase.START, SecurityEpoch.ZERO, trustedFlow));
    }

    private static boolean formal(AuthorizationDecision decision) {
        return decision != null && decision.mode() == AuthorizationDecisionMode.FORMAL;
    }

    private static A2ARequest redact(A2ARequest source, VisibilityLevel visibility) {
        if (source == null || visibility == null || visibility.ordinal() >= VisibilityLevel.STANDARD.ordinal()) return source;
        A2ARequest copy = new A2ARequest();
        BeanUtils.copyProperties(source, copy);
        copy.setInputPayloadRef(null);
        copy.setReason(null);
        copy.setRequestedCapabilityCodes(List.of());
        copy.setRequestingAgentId(null);
        copy.setPolicySnapshot(null);
        copy.setPolicySnapshotHash(null);
        copy.setApprovalActorIds(List.of());
        copy.setRequestedById(null);
        copy.setIdempotencyKey(null);
        copy.setCorrelationId(null);
        copy.setRejectionReasonCode(null);
        if (visibility == VisibilityLevel.METADATA || visibility == VisibilityLevel.NONE) {
            copy.setSourceDepartmentId(null); copy.setSourceGroupId(null); copy.setSourceDomainId(null);
            copy.setTargetDepartmentId(null); copy.setTargetGroupId(null); copy.setTargetDomainId(null);
            copy.setRequestedTaskType("RESTRICTED"); copy.setRequestedServiceCode(null);
            copy.setChildTaskId(null); copy.setPolicyId(null);
        }
        return copy;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private String tenantId() {
        String value = context().tenantId();
        if (value == null || value.isBlank()) throw badRequest("tenantId is required.");
        return value.trim();
    }

    private String actorId() {
        String value = context().actorId();
        return value == null || value.isBlank() ? "unknown-operator" : value.trim();
    }

    private String correlationId() {
        String value = context().correlationId();
        return value == null || value.isBlank() ? java.util.UUID.randomUUID().toString() : value.trim();
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw badRequest(name + " is required.");
        return value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private <T> T execute(Operation<T> operation) {
        try {
            return operation.run();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (A2ARejectedException exception) {
            HttpStatus status = switch (exception.reasonCode()) {
                case A2A_LEGACY_ROUTING_RETIRED -> HttpStatus.GONE;
                case A2A_CAPABILITY_DELEGATION_NOT_AVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                case A2A_MACHINE_IDENTITY_REQUIRED -> HttpStatus.UNAUTHORIZED;
                case A2A_MACHINE_TENANT_MISMATCH, A2A_AGENT_IDENTITY_MISMATCH,
                     A2A_SOURCE_AGENT_NOT_ALLOWED, A2A_DELEGATION_CHAIN_INVALID -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.BAD_REQUEST;
            };
            throw new ResponseStatusException(status, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("not found")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run();
    }

    public record CreateA2ARequest(
            String requestedTaskType,
            String requestedServiceCode,
            List<String> requestedCapabilityCodes,
            String reason,
            String inputPayloadRef,
            String sensitivityLevel) {
    }

    public record RejectA2ARequest(String reasonCode, String reason) {
    }

    public record ResolveReconciliationCase(A2AReconciliationStatus status, String reason) {
    }

    public record ResolveQuarantine(A2ALateResultResolution decision, String reason) {
    }

    public record CancelA2ARequest(String reason) {}

    public record ReconcileCancellation(String reason) {}
}
