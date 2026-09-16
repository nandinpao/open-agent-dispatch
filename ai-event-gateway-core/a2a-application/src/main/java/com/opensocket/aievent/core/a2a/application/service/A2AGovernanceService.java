package com.opensocket.aievent.core.a2a.application.service;
import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.port.in.A2AGovernanceUseCase;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import com.opensocket.aievent.core.a2a.core.A2ACoordinationGuard;
import com.opensocket.aievent.core.a2a.core.A2AStateMachine;
import com.opensocket.aievent.core.a2a.core.A2APolicySnapshotFactory;
import com.opensocket.aievent.core.a2a.core.A2ATransitionDecision;
import com.opensocket.aievent.core.a2a.application.port.out.A2ATaskAuthorityOperations;
import com.opensocket.aievent.core.a2a.application.port.out.A2AAgentRuntimeAuthorizationPort;
import com.opensocket.aievent.core.a2a.application.port.out.A2ADispatchAuthorityOperations;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.iam.security.contract.MachineExecutionContext;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipal;
import com.opensocket.aievent.core.iam.security.contract.MachinePrincipalType;

/**
 * Phase 0 legacy A2A archive and reconciliation authority.
 *
 * <p>Directional Source -> Target routing is no longer an execution authority.
 * New policy writes and new directional requests are hard-blocked. Historical
 * requests remain readable and existing reconciliation/cancellation evidence remains
 * authoritative until Phase 1 introduces capability-first delegation.</p>
 */
public class A2AGovernanceService implements A2AGovernanceUseCase {
    private static final System.Logger LOG = System.getLogger(A2AGovernanceService.class.getName());
    private static final int MAX_QUERY_LIMIT = 1_000;
    private static final int POLICY_DIAGNOSTIC_LIMIT = 100;
    private final A2ACoordinationGuard coordinationGuard = new A2ACoordinationGuard();
    private final A2AStateMachine stateMachine = new A2AStateMachine();
    private final A2APolicySnapshotFactory policySnapshots = new A2APolicySnapshotFactory();
    private final A2ATaskAuthorityOperations taskAuthority;
    private final A2ADispatchAuthorityOperations dispatchAuthority;
    private final A2APolicyRepository policies;
    private final A2ARequestRepository requests;
    private final A2AStateHistoryRepository history;
    private final A2ARateLimitRepository rateLimits;
    private final A2AIdempotencyRepository idempotency;
    private final A2ADomainEventPublisher events;
    private A2AAgentRuntimeAuthorizationPort agentRuntimeAuthorization;


    public A2AGovernanceService(
            A2ATaskAuthorityOperations taskAuthority,
            A2ADispatchAuthorityOperations dispatchAuthority,
            A2APolicyRepository policies,
            A2ARequestRepository requests,
            A2AStateHistoryRepository history,
            A2ARateLimitRepository rateLimits,
            A2AIdempotencyRepository idempotency,
            A2ADomainEventPublisher events) {
        this.taskAuthority = taskAuthority;
        this.dispatchAuthority = dispatchAuthority;
        this.policies = policies;
        this.requests = requests;
        this.history = history;
        this.rateLimits = rateLimits;
        this.idempotency = idempotency;
        this.events = events;
    }

    @Autowired(required = false)
    public void setAgentRuntimeAuthorization(A2AAgentRuntimeAuthorizationPort agentRuntimeAuthorization) {
        this.agentRuntimeAuthorization = agentRuntimeAuthorization;
    }

    @Transactional(readOnly = true)
    public List<A2APolicy> searchPolicies(
            String tenantId,
            String sourceDomainId,
            String targetDomainId,
            int limit) {
        if (blank(tenantId)) {
            throw new IllegalArgumentException("tenantId is required");
        }
        return policies.search(
                tenantId.trim(),
                trim(sourceDomainId),
                trim(targetDomainId),
                cap(limit));
    }

    @Transactional(readOnly = true)
    public List<A2APolicy> searchPolicies(String tenantId,String sourceDomainId,String targetDomainId,int limit,A2APolicyVisibilityScope scope) {
        if (blank(tenantId)) throw new IllegalArgumentException("tenantId is required");
        return policies.searchScoped(tenantId.trim(),trim(sourceDomainId),trim(targetDomainId),cap(limit),scope);
    }

    @Transactional(readOnly = true)
    public A2APolicy getPolicy(String tenantId, String policyId) {
        return requirePolicy(tenantId, policyId);
    }

    @Transactional
    public A2APolicy upsertPolicy(
            String tenantId,
            String policyId,
            A2APolicy value,
            Long expectedVersion) {
        reject(A2AReasonCode.A2A_LEGACY_ROUTING_RETIRED,
                "Directional A2A Policy writes were retired by Phase 0. Existing policies are historical evidence only; no Source Domain -> Target Domain or Policy -> Agent Pool route may be created or changed.");
        throw new IllegalStateException("unreachable");
    }

    @Transactional
    public A2ARequest request(A2ARequestCommand command) {
        reject(A2AReasonCode.A2A_LEGACY_ROUTING_RETIRED,
                "Directional A2A delegation was retired by Phase 0. New delegation must be capability-first and must not require targetDomainId, targetAgentPoolId or targetAgentId. Capability delegation becomes available in Phase 1.");
        throw new IllegalStateException("unreachable");
    }

    @Transactional
    public A2ARequest approve(
            String tenantId,
            String requestId,
            String actorType,
            String actorId,
            String idempotencyKey) {
        reject(A2AReasonCode.A2A_LEGACY_ROUTING_RETIRED,
                "Legacy A2A approvals were retired by Phase 0 because approval could create new directional child work. Historical requests may be rejected, cancelled, reconciled or inspected, but not advanced into execution.");
        throw new IllegalStateException("unreachable");
    }

    @Transactional
    public A2ARequest reject(
            String tenantId,
            String requestId,
            String reasonCode,
            String reason,
            String actorType,
            String actorId,
            String idempotencyKey) {
        String hash = A2ARequestCanonicalizer.operationHash(requestId, reasonCode, reason, actorType, actorId);
        A2AIdempotencyClaim claim = claim(
                tenantId, idempotencyKey, "A2A_REJECT", hash, now());
        A2ARequest request = requireRequest(tenantId, requestId);
        if (request.getRequestStatus() == A2ARequestStatus.REJECTED) {
            completeClaim(tenantId, idempotencyKey, "A2A_REJECT", requestId);
            return request;
        }
        if (!claim.acquired()) {
            reject(A2AReasonCode.A2A_IDEMPOTENCY_IN_PROGRESS,
                    "This A2A rejection is still being processed.");
        }
        if (request.getChildTaskId() != null) {
            throw new IllegalStateException("A2A_CHILD_TASK_ALREADY_CREATED");
        }
        A2ARequestStatus before = request.getRequestStatus();
        long expectedVersion = request.getVersion();
        A2ATransitionDecision rejection = stateMachine.decide(
                request, A2ATransitionCommand.REJECT, A2ARequestStatus.REJECTED,
                A2ABlockerCode.NONE, expectedVersion,
                firstNonBlank(reason, "A2A Request rejected"),
                "rejection:" + idempotencyKey, now());
        request.setApprovalStatus(A2AApprovalStatus.REJECTED);
        applyDecision(request, rejection, null);
        request.setRejectionReasonCode(firstNonBlank(reasonCode, "A2A_REJECTED"));
        request = requests.saveExpectedVersion(request, expectedVersion);
        record(request, rejection, request.getRejectionReasonCode(),
                actorType, actorId, "reject:" + idempotencyKey);
        publish(request, A2ADomainEventType.A2A_REJECTED,
                Map.of("reasonCode", request.getRejectionReasonCode()));
        completeClaim(tenantId, idempotencyKey, "A2A_REJECT", requestId);
        return request;
    }

    @Transactional(readOnly = true)
    public A2ARequest get(String tenantId, String requestId) {
        return requireRequest(tenantId, requestId);
    }

    @Override
    @Transactional
    public A2ARequest recordDispatchProgress(A2ADispatchProgressCommand command) {
        if (command == null || blank(command.tenantId()) || blank(command.childTaskId())
                || blank(command.stage()) || blank(command.evidenceReference())) {
            throw new IllegalArgumentException("tenantId, childTaskId, stage and evidenceReference are required");
        }
        A2ARequest request = requests.findByChildTask(command.tenantId(), command.childTaskId())
                .orElse(null);
        if (request == null || request.getRequestStatus() == null || isTerminal(request.getRequestStatus())) {
            return request;
        }
        String stage = command.stage().trim().toUpperCase();
        if ("CLAIMED".equals(stage) || "DISPATCHING".equals(stage)) {
            // A Child Task may have been BLOCKED before a Dispatch Request existed (for
            // example no eligible Pool member / capacity). Task-level recovery can later
            // create the canonical Dispatch Request without replaying the original A2A
            // dispatch-intent event. The first durable Dispatch claim is therefore also
            // valid reconciliation evidence: recover the A2A request first, then enter
            // DISPATCHING using distinct evidence ids for both state transitions.
            if (request.getOperationalStage() == A2AOperationalStage.BLOCKED) {
                A2ADispatchProgressCommand recover = new A2ADispatchProgressCommand(
                        command.tenantId(), command.childTaskId(), command.dispatchRequestId(),
                        "RECOVERED", A2ABlockerCode.NONE,
                        firstNonBlank(command.reason(), "Canonical Dispatch became available after A2A was blocked."),
                        command.evidenceReference() + ":recover", command.actorId(), command.occurredAt());
                request = applyDispatchProgress(request, A2ATransitionCommand.RECOVER,
                        request.getRequestStatus(), A2ABlockerCode.NONE, recover, "A2A_DISPATCH_RECOVERED");
            }
            if (request.getOperationalStage() == A2AOperationalStage.DISPATCH_REQUESTED) {
                A2ADispatchProgressCommand start = new A2ADispatchProgressCommand(
                        command.tenantId(), command.childTaskId(), command.dispatchRequestId(),
                        stage, A2ABlockerCode.NONE, command.reason(),
                        command.evidenceReference() + ":start", command.actorId(), command.occurredAt());
                return applyDispatchProgress(request, A2ATransitionCommand.START_DISPATCH,
                        A2ARequestStatus.DISPATCHING, A2ABlockerCode.NONE, start, "A2A_DISPATCHING");
            }
        }
        if ("FAILED_RETRYABLE".equals(stage)
                && (request.getRequestStatus() == A2ARequestStatus.CHILD_TASK_CREATED
                    || request.getRequestStatus() == A2ARequestStatus.DISPATCHING)) {
            return applyDispatchProgress(request, A2ATransitionCommand.MARK_BLOCKED,
                    request.getRequestStatus(), firstNonNone(command.blockerCode(), A2ABlockerCode.DISPATCH_RETRY_WAITING),
                    command, "A2A_DISPATCH_RETRY_WAITING");
        }
        if ("DEAD_LETTER".equals(stage)
                && (request.getRequestStatus() == A2ARequestStatus.CHILD_TASK_CREATED
                    || request.getRequestStatus() == A2ARequestStatus.DISPATCHING)) {
            return applyDispatchProgress(request, A2ATransitionCommand.MARK_BLOCKED,
                    request.getRequestStatus(), A2ABlockerCode.DISPATCH_DEAD_LETTER, command, "A2A_DISPATCH_DEAD_LETTER");
        }
        if ("RECOVERED".equals(stage) && request.getOperationalStage() == A2AOperationalStage.BLOCKED) {
            return applyDispatchProgress(request, A2ATransitionCommand.RECOVER, request.getRequestStatus(),
                    A2ABlockerCode.NONE, command, "A2A_DISPATCH_RECOVERED");
        }
        if ("ACK".equals(stage)
                && (request.getRequestStatus() == A2ARequestStatus.DISPATCHING
                    || request.getRequestStatus() == A2ARequestStatus.CHILD_TASK_CREATED)) {
            return applyDispatchProgress(request, A2ATransitionCommand.MARK_RUNNING, A2ARequestStatus.RUNNING,
                    A2ABlockerCode.NONE, command, "A2A_RUNNING");
        }
        if ("WAITING_RESULT".equals(stage) && request.getRequestStatus() == A2ARequestStatus.RUNNING
                && request.getOperationalStage() != A2AOperationalStage.WAITING_RESULT) {
            return applyDispatchProgress(request, A2ATransitionCommand.WAIT_FOR_RESULT, A2ARequestStatus.RUNNING,
                    A2ABlockerCode.NONE, command, "A2A_WAITING_RESULT");
        }
        return request;
    }

    private A2ARequest applyDispatchProgress(A2ARequest request, A2ATransitionCommand transitionCommand,
            A2ARequestStatus targetStatus, A2ABlockerCode blockerCode, A2ADispatchProgressCommand command,
            String reasonCode) {
        long expectedVersion = request.getVersion();
        A2ATransitionDecision decision = stateMachine.decide(request, transitionCommand, targetStatus, blockerCode,
                expectedVersion, firstNonBlank(command.reason(), reasonCode), command.evidenceReference(),
                command.occurredAt() == null ? now() : command.occurredAt());
        applyDecision(request, decision, command.reason());
        request = requests.saveExpectedVersion(request, expectedVersion);
        record(request, decision, reasonCode, "SYSTEM", firstNonBlank(command.actorId(), "DISPATCH_AUTHORITY"),
                "dispatch-progress:" + command.evidenceReference());
        A2ADomainEventType eventType = switch (decision.toStage()) {
            case DISPATCHING -> A2ADomainEventType.A2A_DISPATCHING;
            case RUNNING -> A2ADomainEventType.A2A_RUNNING;
            case WAITING_RESULT -> A2ADomainEventType.A2A_WAITING_RESULT;
            case BLOCKED -> A2ADomainEventType.A2A_BLOCKED;
            default -> A2ADomainEventType.A2A_RECOVERED;
        };
        publish(request, eventType,
                Map.of("dispatchRequestId", firstNonBlank(command.dispatchRequestId(), "-"),
                        "stage", command.stage(), "evidenceReference", command.evidenceReference()));
        return request;
    }

    private boolean isTerminal(A2ARequestStatus status) {
        return status == A2ARequestStatus.COMPLETED
                || status == A2ARequestStatus.FAILED
                || status == A2ARequestStatus.REJECTED
                || status == A2ARequestStatus.EXPIRED
                || status == A2ARequestStatus.CANCELLED_CONFIRMED;
    }

    private A2ABlockerCode firstNonNone(A2ABlockerCode value, A2ABlockerCode fallback) {
        return value == null || value == A2ABlockerCode.NONE ? fallback : value;
    }

    @Transactional(readOnly = true)
    public List<A2ARequest> history(String tenantId, String taskId, int limit) {
        TaskRecord task = requireTask(tenantId, taskId);
        return requests.findByRootTask(
                tenantId,
                firstNonBlank(task.getRootTaskId(), taskId),
                cap(limit));
    }

    @Transactional(readOnly = true)
    public List<A2AStateHistoryEntry> stateHistory(String tenantId, String requestId, int limit) {
        requireRequest(tenantId, requestId);
        return history.findByRequest(tenantId, requestId, cap(limit));
    }

    private A2ARequest createChildTask(
            A2ARequest request,
            TaskRecord source,
            A2APolicy policy,
            String actorType,
            String actorId) {
        if (request.getChildTaskId() != null) {
            return request;
        }
        OffsetDateTime changedAt = now();
        if (request.getOperationalStage() != A2AOperationalStage.CHILD_TASK_CREATING) {
            long expectedVersion = request.getVersion();
            A2ATransitionDecision creating = stateMachine.decide(
                    request,
                    A2ATransitionCommand.BEGIN_CHILD_CREATION,
                    A2ARequestStatus.APPROVED,
                    A2ABlockerCode.NONE,
                    expectedVersion,
                    "Begin authoritative Child Task creation",
                    "policy-snapshot:" + firstNonBlank(
                            request.getPolicySnapshotHash(),
                            request.getPolicyId() + ":" + request.getPolicyVersion()),
                    changedAt);
            applyDecision(request, creating, null);
            request = requests.saveExpectedVersion(request, expectedVersion);
            record(request, creating, "A2A_CHILD_TASK_CREATION_STARTED",
                    actorType, actorId, "child-start:" + request.getIdempotencyKey());
            publish(request, A2ADomainEventType.A2A_CHILD_TASK_CREATION_STARTED,
                    Map.of("targetPoolId", request.getTargetAgentPoolId()));
        }

        TaskRecord child = taskAuthority.createChildTask(request, source, policy);
        long expectedVersion = request.getVersion();
        A2ATransitionDecision childCreated = stateMachine.decide(
                request,
                A2ATransitionCommand.CREATE_CHILD,
                A2ARequestStatus.CHILD_TASK_CREATED,
                A2ABlockerCode.NONE,
                expectedVersion,
                "Task Authority created the Child Task",
                "task:" + child.getTaskId(),
                now());
        request.setChildTaskId(child.getTaskId());
        applyDecision(request, childCreated, null);
        request = requests.saveExpectedVersion(request, expectedVersion);
        record(request, childCreated, "A2A_CHILD_TASK_CREATED",
                actorType, actorId, "child:" + request.getIdempotencyKey());
        publish(request, A2ADomainEventType.A2A_CHILD_TASK_CREATED,
                Map.of("childTaskId", child.getTaskId(),
                        "targetPoolId", request.getTargetAgentPoolId()));

        expectedVersion = request.getVersion();
        if (child.getStatus() != null && child.getStatus().isDispatchReady()) {
            var dispatchReceipt = dispatchAuthority.requestDispatch(request);
            A2ATransitionDecision dispatchRequested = stateMachine.decide(
                    request,
                    A2ATransitionCommand.REQUEST_DISPATCH,
                    A2ARequestStatus.CHILD_TASK_CREATED,
                    A2ABlockerCode.NONE,
                    expectedVersion,
                    "Dispatch Authority accepted the dispatch intent",
                    "dispatch:" + dispatchReceipt.dispatchTokenReference(),
                    now());
            applyDecision(request, dispatchRequested, null);
            request = requests.saveExpectedVersion(request, expectedVersion);
            record(request, dispatchRequested, "A2A_DISPATCH_REQUESTED",
                    actorType, actorId, "dispatch:" + request.getIdempotencyKey());
            publish(request, A2ADomainEventType.A2A_DISPATCH_REQUESTED, Map.of(
                    "childTaskId", child.getTaskId(),
                    "targetPoolId", request.getTargetAgentPoolId(),
                    "dispatchOutboxReference", dispatchReceipt.dispatchTokenReference(),
                    "dispatchState", "REQUESTED"));
        } else {
            A2ATransitionDecision blocked = stateMachine.decide(
                    request,
                    A2ATransitionCommand.MARK_BLOCKED,
                    A2ARequestStatus.CHILD_TASK_CREATED,
                    A2ABlockerCode.HANDOFF_REQUIRED,
                    expectedVersion,
                    "Child Task is waiting for required handoff context",
                    "task-status:" + child.getStatus(),
                    now());
            applyDecision(request, blocked, "Child Task is not dispatch-ready.");
            request = requests.saveExpectedVersion(request, expectedVersion);
            record(request, blocked, "A2A_BLOCKED",
                    actorType, actorId, "dispatch-blocked:" + request.getIdempotencyKey());
            publish(request, A2ADomainEventType.A2A_BLOCKED, Map.of(
                    "childTaskId", child.getTaskId(),
                    "blockerCode", request.getBlockerCode().name()));
        }
        return request;
    }


    private void validateMachineRequestIdentity(A2ARequestCommand command) {
        MachineExecutionContext execution = command.machineExecutionContext();
        if (command.requesterType() != A2ARequesterType.AGENT) {
            if (execution != null) {
                reject(A2AReasonCode.A2A_AGENT_IDENTITY_MISMATCH,
                        "A machine execution context may request A2A work only through requesterType AGENT.");
            }
            return;
        }
        if (execution == null) {
            reject(A2AReasonCode.A2A_MACHINE_IDENTITY_REQUIRED,
                    "AGENT A2A requests require an authenticated machine execution context.");
        }
        MachinePrincipal principal = execution.authentication().principal();
        if (!execution.delegation().executingPrincipal().equals(principal)
                || !execution.delegation().originPrincipal().activeTenant().equals(principal.activeTenant())) {
            reject(A2AReasonCode.A2A_DELEGATION_CHAIN_INVALID,
                    "Machine delegation chain is not contiguous within the authenticated Tenant.");
        }
        if (principal.principalType() != MachinePrincipalType.AGENT
                && principal.principalType() != MachinePrincipalType.A2A_AGENT) {
            reject(A2AReasonCode.A2A_AGENT_IDENTITY_MISMATCH,
                    "Authenticated machine principal is not an Agent identity.");
        }
        if (!same(principal.activeTenant().tenantId(), command.tenantId())) {
            reject(A2AReasonCode.A2A_MACHINE_TENANT_MISMATCH,
                    "Authenticated Agent Tenant does not match the A2A request Tenant.");
        }
        if (blank(command.requestingAgentId()) || !same(principal.principalId(), command.requestingAgentId())) {
            reject(A2AReasonCode.A2A_AGENT_IDENTITY_MISMATCH,
                    "requestingAgentId must match the authenticated Agent principal.");
        }
        if (!blank(command.actorId()) && !same(principal.principalId(), command.actorId())) {
            reject(A2AReasonCode.A2A_AGENT_IDENTITY_MISMATCH,
                    "actorId may not impersonate another Agent identity.");
        }
    }

    private void validateSourceAgentAgainstPolicy(A2ARequestCommand command, A2APolicy policy) {
        if (command.requesterType() != A2ARequesterType.AGENT || blank(policy.getSourceAgentId())) {
            return;
        }
        if (!same(policy.getSourceAgentId(), command.requestingAgentId())) {
            reject(A2AReasonCode.A2A_SOURCE_AGENT_NOT_ALLOWED,
                    "Selected A2A Policy is bound to a different source Agent.");
        }
    }

    private String effectiveActorType(A2ARequestCommand command) {
        if (command.machineExecutionContext() == null) return command.actorType();
        return command.machineExecutionContext().authentication().principal().principalType().name();
    }

    private String effectiveActorId(A2ARequestCommand command) {
        if (command.machineExecutionContext() == null) return command.actorId();
        return command.machineExecutionContext().authentication().principal().principalId();
    }

    private void applyMachineDelegationEvidence(A2ARequest request, A2ARequestCommand command) {
        MachineExecutionContext execution = command.machineExecutionContext();
        if (execution == null) return;
        MachinePrincipal origin = execution.delegation().originPrincipal();
        MachinePrincipal delegating = execution.delegation().delegatingPrincipal();
        MachinePrincipal executing = execution.authentication().principal();
        request.setOriginPrincipalType(origin.principalType().name());
        request.setOriginPrincipalId(origin.principalId());
        request.setDelegatingPrincipalType(delegating.principalType().name());
        request.setDelegatingPrincipalId(delegating.principalId());
        request.setExecutingPrincipalType(executing.principalType().name());
        request.setExecutingPrincipalId(executing.principalId());
        request.setDelegationDepth(execution.delegation().hops().size());
    }

    private void validateTargetScopeAgainstPolicy(A2ARequestCommand command, A2APolicy policy) {
        if (!sameOptionalScope(command.targetDepartmentId(), policy.getTargetDepartmentId())
                || !sameOptionalScope(command.targetGroupId(), policy.getTargetGroupId())) {
            reject(A2AReasonCode.A2A_TARGET_SCOPE_NOT_ALLOWED,
                    "Requested target organization scope must match the selected governed A2A Policy.");
        }
    }

    private boolean sameOptionalScope(String requested, String policyValue) {
        if (blank(requested)) return true; // client may omit; canonical scope always comes from Policy
        if (blank(policyValue)) return false; // payload may never widen a Tenant/wildcard Policy into an invented organization scope
        return requested.trim().equals(policyValue.trim());
    }

    private void validateRequestingAgent(A2ARequestCommand command, TaskRecord source) {
        if (command.requesterType() != A2ARequesterType.AGENT) {
            return;
        }
        if (blank(command.requestingAgentId())) {
            reject(A2AReasonCode.REQUESTING_AGENT_NOT_CURRENT_ASSIGNEE,
                    "requestingAgentId is required for AGENT requests");
        }
        validateAssignedAgent(command.tenantId(), source, command.requestingAgentId());
        if (agentRuntimeAuthorization != null) {
            A2AAgentRuntimeAuthorizationPort.Decision decision = agentRuntimeAuthorization.authorize(
                    command.tenantId(), source.getTaskId(), command.requestingAgentId(), command.correlationId());
            if (decision.enforced() && !decision.allowed()) {
                reject(A2AReasonCode.A2A_AGENT_RUNTIME_AUTHORIZATION_DENIED,
                        decision.reason().isBlank() ? "Agent Service Principal is not authorized for the source Task." : decision.reason());
            }
        }
    }

    private void validateAssignedAgent(String tenantId, TaskRecord task, String agentId) {
        if (!taskAuthority.isCurrentAssignedAgent(tenantId, task.getTaskId(), agentId)) {
            reject(A2AReasonCode.REQUESTING_AGENT_NOT_CURRENT_ASSIGNEE,
                    "Agent is not the current assigned Agent for Task " + task.getTaskId());
        }
    }

    private void validateCycle(TaskRecord source, A2ARequestCommand command, A2APolicy policy) {
        if (policy.isAllowReturnToExistingDomain()) {
            return;
        }
        String rootTaskId = firstNonBlank(source.getRootTaskId(), source.getTaskId());
        boolean cycle = taskAuthority.findChain(command.tenantId(), rootTaskId, MAX_QUERY_LIMIT).stream()
                .anyMatch(task -> same(task.getExecutorDomainId(), command.targetDomainId())
                        && same(task.getEffectiveTaskTypeCode(), command.requestedTaskType())
                        && same(
                                firstNonBlank(task.getObjectId(), "-"),
                                firstNonBlank(source.getObjectId(), "-")));
        if (cycle) {
            publishRejected(source, command, A2ADomainEventType.A2A_CYCLE_REJECTED,
                    A2AReasonCode.A2A_CYCLE_DETECTED);
            reject(A2AReasonCode.A2A_CYCLE_DETECTED,
                    "The same domain, Task Type and business object already exist in this A2A chain.");
        }
    }

    private void applyDecision(
            A2ARequest request,
            A2ATransitionDecision decision,
            String blockerReason) {
        request.setRequestStatus(decision.toStatus());
        request.setOperationalStage(decision.toStage());
        request.setBlockerCode(decision.blockerCode());
        request.setBlockerReason(decision.blockerCode() == A2ABlockerCode.NONE
                ? null
                : firstNonBlank(blockerReason, decision.auditReason()));
        request.setUpdatedAt(now());
        request.setVersion(decision.resultingVersion());
    }

    private void record(
            A2ARequest request,
            A2ATransitionDecision decision,
            String reasonCode,
            String actorType,
            String actorId,
            String idempotencyKey) {
        Optional<A2AStateHistoryEntry> replay = history.findByIdempotencyKey(
                request.getTenantId(), idempotencyKey);
        if (replay.isPresent()) {
            return;
        }
        A2AStateHistoryEntry entry = new A2AStateHistoryEntry();
        entry.setTenantId(request.getTenantId());
        entry.setHistoryId("a2ah-" + UUID.randomUUID());
        entry.setRequestId(request.getRequestId());
        entry.setFromStatus(decision.fromStatus());
        entry.setFromOperationalStage(decision.fromStage());
        entry.setToStatus(decision.toStatus());
        entry.setToOperationalStage(decision.toStage());
        entry.setBlockerCode(decision.blockerCode());
        entry.setTransitionCommand(decision.command());
        entry.setRequiredPermission(decision.requiredPermission());
        entry.setEvidenceType(decision.evidenceType());
        entry.setEvidenceReference(decision.evidenceReference());
        entry.setDomainEventCode(decision.domainEventCode());
        entry.setFailureHandling(decision.failureHandling());
        entry.setTimeoutPolicy(decision.timeoutPolicy());
        entry.setExpectedVersion(decision.expectedVersion());
        entry.setResultingVersion(decision.resultingVersion());
        entry.setRecovery(decision.recovery());
        entry.setReasonCode(firstNonBlank(reasonCode, decision.ruleId()));
        entry.setReason(decision.auditReason());
        entry.setActorType(firstNonBlank(actorType, "SYSTEM"));
        entry.setActorId(firstNonBlank(actorId, "CORE"));
        entry.setCorrelationId(request.getCorrelationId());
        entry.setTransitionAt(now());
        entry.setRequestVersion(request.getVersion());
        entry.setIdempotencyKey(idempotencyKey);
        history.save(entry);
    }

    private void record(
            A2ARequest request,
            A2ARequestStatus from,
            A2ARequestStatus to,
            String reasonCode,
            String reason,
            String actorType,
            String actorId,
            String idempotencyKey) {
        Optional<A2AStateHistoryEntry> replay = history.findByIdempotencyKey(
                request.getTenantId(), idempotencyKey);
        if (replay.isPresent()) {
            return;
        }
        A2AStateHistoryEntry entry = new A2AStateHistoryEntry();
        entry.setTenantId(request.getTenantId());
        entry.setHistoryId("a2ah-" + UUID.randomUUID());
        entry.setRequestId(request.getRequestId());
        entry.setFromStatus(from);
        entry.setFromOperationalStage(from == null ? null : A2AOperationalStage.defaultFor(from));
        entry.setToStatus(to);
        entry.setToOperationalStage(request.getOperationalStage() == null
                ? A2AOperationalStage.defaultFor(to)
                : request.getOperationalStage());
        entry.setBlockerCode(request.getBlockerCode() == null
                ? A2ABlockerCode.NONE
                : request.getBlockerCode());
        entry.setExpectedVersion(Math.max(0, request.getVersion() - 1));
        entry.setResultingVersion(request.getVersion());
        entry.setReasonCode(firstNonBlank(reasonCode, "A2A_STATE_CHANGED"));
        entry.setReason(trim(reason));
        entry.setActorType(firstNonBlank(actorType, "SYSTEM"));
        entry.setActorId(firstNonBlank(actorId, "CORE"));
        entry.setCorrelationId(request.getCorrelationId());
        entry.setTransitionAt(now());
        entry.setRequestVersion(request.getVersion());
        entry.setIdempotencyKey(idempotencyKey);
        history.save(entry);
    }

    private void publish(A2ARequest request, A2ADomainEventType type, Map<String, Object> payload) {
        events.publish(new A2ADomainEvent(
                "evt-" + UUID.randomUUID(),
                type,
                request.getTenantId(),
                request.getRequestId(),
                request.getRootTaskId(),
                request.getCorrelationId(),
                request.getSourceTaskId(),
                request.getRequestedByType(),
                request.getRequestedById(),
                now(),
                1,
                new LinkedHashMap<>(payload)));
    }

    private void publishRejected(
            TaskRecord source,
            A2ARequestCommand command,
            A2ADomainEventType eventType,
            A2AReasonCode reasonCode) {
        A2ARequest transientRequest = new A2ARequest();
        transientRequest.setTenantId(command.tenantId());
        transientRequest.setRequestId("rejected-" + command.idempotencyKey());
        transientRequest.setRootTaskId(firstNonBlank(source.getRootTaskId(), source.getTaskId()));
        transientRequest.setSourceTaskId(source.getTaskId());
        transientRequest.setCorrelationId(firstNonBlank(command.correlationId(), source.getCorrelationId()));
        transientRequest.setRequestedByType(firstNonBlank(effectiveActorType(command), command.requesterType().name()));
        transientRequest.setRequestedById(firstNonBlank(effectiveActorId(command), command.requestingAgentId(), "CORE"));
        applyMachineDelegationEvidence(transientRequest, command);
        publish(transientRequest, eventType, Map.of("reasonCode", reasonCode.name()));
    }

    private A2AIdempotencyClaim claim(
            String tenantId,
            String idempotencyKey,
            String operationType,
            String requestHash,
            OffsetDateTime claimedAt) {
        if (blank(idempotencyKey)) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        A2AIdempotencyClaim claim = idempotency.claim(
                tenantId,
                idempotencyKey.trim(),
                operationType,
                requestHash,
                claimedAt,
                claimedAt.plusHours(24));
        if (!requestHash.equals(claim.record().getRequestHash())) {
            reject(A2AReasonCode.A2A_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was already used with a different payload.");
        }
        return claim;
    }

    private void completeClaim(
            String tenantId,
            String idempotencyKey,
            String operationType,
            String resourceId) {
        idempotency.complete(
                tenantId,
                idempotencyKey,
                operationType,
                operationType,
                resourceId,
                now());
    }

    private TaskRecord requireTask(String tenantId, String taskId) {
        if (blank(tenantId) || blank(taskId)) {
            throw new IllegalArgumentException("tenantId and taskId are required");
        }
        return taskAuthority.requireTask(tenantId.trim(), taskId.trim());
    }

    private A2ARequest requireRequest(String tenantId, String requestId) {
        if (blank(tenantId) || blank(requestId)) {
            throw new IllegalArgumentException("tenantId and requestId are required");
        }
        return requests.findById(tenantId.trim(), requestId.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "A2A Request not found in Tenant: " + requestId));
    }

    /**
     * Resolve the domain that is actually delegating this A2A request.
     *
     * <p>For AGENT requests, the current executor Service Domain is authoritative;
     * falling back to sourceSystem would allow an Agent with unresolved governance
     * metadata to impersonate the business source domain. For human/system requests,
     * unresolved placeholder domains are ignored and the Task source system is a
     * valid final fallback for root work created from an external system.</p>
     */
    private ResolvedSourceDomain resolveSourceDomain(A2ARequestCommand command, TaskRecord source) {
        String executor = resolvedDomainValue(source.getExecutorDomainId());
        if (command.requesterType() == A2ARequesterType.AGENT) {
            return new ResolvedSourceDomain(executor, executor == null ? "AGENT_EXECUTOR_DOMAIN_UNRESOLVED" : "EXECUTOR_DOMAIN");
        }
        if (executor != null) {
            return new ResolvedSourceDomain(executor, "EXECUTOR_DOMAIN");
        }
        String requester = resolvedDomainValue(source.getRequesterDomainId());
        if (requester != null) {
            return new ResolvedSourceDomain(requester, "REQUESTER_DOMAIN");
        }
        String sourceSystem = resolvedDomainValue(source.getSourceSystem());
        return new ResolvedSourceDomain(sourceSystem, sourceSystem == null ? "UNRESOLVED" : "SOURCE_SYSTEM_FALLBACK");
    }

    private String resolvedDomainValue(String value) {
        if (blank(value)) return null;
        String normalized = value.trim();
        if ("UNASSIGNED".equalsIgnoreCase(normalized)
                || "UNKNOWN".equalsIgnoreCase(normalized)
                || "NONE".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private record ResolvedSourceDomain(String domainId, String source) {}

    private void logA2ASourceContext(TaskRecord source, A2ARequestCommand command, String resolvedSourceDomain, String resolutionSource) {
        logInfo("a2a_policy_journey stage=SOURCE_CONTEXT_RESOLVED tenantId={} sourceTaskId={} requesterType={} taskStatus={} sourceSystem={} requesterDomainId={} executorDomainId={} resolvedSourceDomainId={} sourceDomainResolution={} requesterDepartmentId={} requesterGroupId={} executorDepartmentId={} executorGroupId={} assignedPoolId={} targetPoolId={} requestedTargetDomainId={} requestedTaskType={} requestedServiceCode={} requestedCapabilityCodes={} correlationId={} idempotencyKey={}",
                safe(command.tenantId()), safe(source.getTaskId()), command.requesterType(), source.getStatus(), safe(source.getSourceSystem()),
                safe(source.getRequesterDomainId()), safe(source.getExecutorDomainId()), safe(resolvedSourceDomain), safe(resolutionSource),
                safe(source.getRequesterDepartmentId()), safe(source.getRequesterGroupId()),
                safe(source.getExecutorDepartmentId()), safe(source.getExecutorGroupId()),
                safe(source.getAssignedPoolId()), safe(source.getTargetPoolId()), safe(command.targetDomainId()),
                safe(command.requestedTaskType()), safe(command.requestedServiceCode()), safeList(command.requestedCapabilityCodes()),
                safe(command.correlationId()), safe(command.idempotencyKey()));
    }

    private void logPolicyInventory(
            String stage,
            List<A2APolicy> values,
            A2ARequestCommand command,
            String resolvedSourceDomain,
            OffsetDateTime at) {
        List<A2APolicy> safeValues = values == null ? List.of() : values;
        logInfo("a2a_policy_journey stage={} tenantId={} sourceDomainId={} targetDomainId={} policyCount={} at={} correlationId={} idempotencyKey={}",
                stage, safe(command.tenantId()), safe(resolvedSourceDomain), safe(command.targetDomainId()),
                safeValues.size(), at, safe(command.correlationId()), safe(command.idempotencyKey()));
        for (A2APolicy policy : safeValues) {
            logInfo("a2a_policy_journey stage=POLICY_INVENTORY_ITEM tenantId={} requestedSourceDomainId={} requestedTargetDomainId={} policyId={} policyCode={} policySourceDomainId={} policyTargetDomainId={} enabled={} activeNow={} effectiveAt={} expiresAt={} targetAgentPoolId={} sourceAgentPoolId={} sourceAgentId={} allowedTaskTypes={} allowedServiceCodes={} allowedCapabilityCodes={} maxSensitivityLevel={} approvalMode={} correlationId={} idempotencyKey={}",
                    safe(command.tenantId()), safe(resolvedSourceDomain), safe(command.targetDomainId()),
                    safe(policy.getPolicyId()), safe(policy.getPolicyCode()), safe(policy.getSourceDomainId()), safe(policy.getTargetDomainId()),
                    policy.isEnabled(), policy.activeAt(at), policy.getEffectiveAt(), policy.getExpiresAt(),
                    safe(policy.getTargetAgentPoolId()), safe(policy.getSourceAgentPoolId()), safe(policy.getSourceAgentId()),
                    safeList(policy.getAllowedTaskTypes()), safeList(policy.getAllowedServiceCodes()), safeList(policy.getAllowedCapabilityCodes()),
                    safe(policy.getMaxSensitivityLevel()), policy.getApprovalMode(), safe(command.correlationId()), safe(command.idempotencyKey()));
        }
    }

    private void logPolicyCompatibility(List<A2APolicy> values, A2ARequestCommand command, OffsetDateTime at) {
        if (values == null) return;
        for (A2APolicy policy : values) {
            boolean taskAllowed = policy.allowsTaskType(command.requestedTaskType());
            boolean serviceAllowed = policy.allowsServiceCode(command.requestedServiceCode());
            boolean capabilitiesAllowed = policy.allowsCapabilities(command.requestedCapabilityCodes());
            logInfo("a2a_policy_journey stage=POLICY_COMPATIBILITY tenantId={} policyId={} policyCode={} activeNow={} taskTypeAllowed={} serviceCodeAllowed={} capabilitiesAllowed={} requestedTaskType={} requestedServiceCode={} requestedCapabilityCodes={} targetAgentPoolId={} correlationId={} idempotencyKey={}",
                    safe(command.tenantId()), safe(policy.getPolicyId()), safe(policy.getPolicyCode()), policy.activeAt(at),
                    taskAllowed, serviceAllowed, capabilitiesAllowed, safe(command.requestedTaskType()),
                    safe(command.requestedServiceCode()), safeList(command.requestedCapabilityCodes()), safe(policy.getTargetAgentPoolId()),
                    safe(command.correlationId()), safe(command.idempotencyKey()));
        }
    }

    private String safe(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).replace('\n', ' ').replace('\r', ' ').trim();
        return text.isEmpty() ? "" : text;
    }

    private String safeList(List<?> values) {
        return values == null ? "[]" : safe(values);
    }

    private A2APolicy requirePolicy(String tenantId, String policyId) {
        if (blank(tenantId) || blank(policyId)) {
            throw new IllegalArgumentException("tenantId and policyId are required");
        }
        return policies.findById(tenantId.trim(), policyId.trim())
                .orElseThrow(() -> new IllegalStateException(
                        "A2A Policy not found: " + policyId));
    }

    private void validateCommand(A2ARequestCommand command) {
        if (command == null
                || blank(command.tenantId())
                || blank(command.sourceTaskId())
                || command.requesterType() == null
                || blank(command.targetDomainId())
                || blank(command.requestedTaskType())
                || blank(command.idempotencyKey())) {
            throw new IllegalArgumentException(
                    "tenantId, sourceTaskId, requesterType, targetDomainId, requestedTaskType and idempotencyKey are required");
        }
    }

    private void reject(A2AReasonCode reasonCode, String message) {
        throw new A2ARejectedException(reasonCode, message);
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit, MAX_QUERY_LIMIT));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String trim(String value) {
        return blank(value) ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (!blank(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }


    /**
     * Keeps A2A journey diagnostics inside the application module without adding a
     * logging-facade dependency. The module intentionally has no SLF4J API on its
     * compile classpath; the hosting runtime may route System.Logger through its
     * configured logging backend.
     */
    private static void logInfo(String template, Object... args) {
        if (!LOG.isLoggable(System.Logger.Level.INFO)) {
            return;
        }
        LOG.log(System.Logger.Level.INFO, renderLogTemplate(template, args));
    }

    private static String renderLogTemplate(String template, Object... args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }
        StringBuilder rendered = new StringBuilder(template.length() + (args.length * 16));
        int from = 0;
        int argumentIndex = 0;
        while (argumentIndex < args.length) {
            int marker = template.indexOf("{}", from);
            if (marker < 0) {
                break;
            }
            rendered.append(template, from, marker);
            rendered.append(String.valueOf(args[argumentIndex++]));
            from = marker + 2;
        }
        rendered.append(template, from, template.length());
        while (argumentIndex < args.length) {
            rendered.append(" arg").append(argumentIndex).append('=')
                    .append(String.valueOf(args[argumentIndex]));
            argumentIndex++;
        }
        return rendered.toString();
    }

}