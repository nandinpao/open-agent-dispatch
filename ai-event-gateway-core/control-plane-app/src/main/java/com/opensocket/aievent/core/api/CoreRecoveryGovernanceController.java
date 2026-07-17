package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.assignment.AssignmentDecisionResult;
import com.opensocket.aievent.core.config.RecoveryGovernanceProperties;
import com.opensocket.aievent.core.dispatch.DispatchAttemptHistoryService;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestService;
import com.opensocket.aievent.core.dispatch.DispatchRequestStatus;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.dispatch.RecoveryApprovalRequest;
import com.opensocket.aievent.core.dispatch.RecoveryApprovalStatus;
import com.opensocket.aievent.core.dispatch.RecoveryApprovalWorkflowService;
import com.opensocket.aievent.core.dispatch.RecoveryOperatorAuditMetadata;
import com.opensocket.aievent.core.security.CoreInternalSecurityProperties;
import com.opensocket.aievent.core.security.CoreInternalSecurityRole;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;

/**
 * P10.7 operator-facing recovery governance actions.
 *
 * <p>P10.6 added RBAC, reason and confirmation. P10.7 adds dual-control approval for
 * high-risk recovery operations, so destructive/large-impact actions can be requested by one
 * operator and approved/executed by a different recovery approver.</p>
 */
@RestController
@RequestMapping("/admin/recovery")
public class CoreRecoveryGovernanceController {
    private static final String ROLE_RECOVERY_OPERATOR = CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.RECOVERY_OPERATOR);
    private static final String ROLE_RECOVERY_ADMIN = CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.RECOVERY_ADMIN);
    private static final String ROLE_RECOVERY_APPROVER = CoreInternalSecurityProperties.authority(CoreInternalSecurityRole.RECOVERY_APPROVER);

    private final AgentDirectoryFacade agentDirectory;
    private final TaskOrchestrationFacade taskOrchestration;
    private final ExecutionOperationalQuery executionQuery;
    private final DispatchRequestService dispatchRequestService;
    private final DispatchAttemptHistoryService attemptHistoryService;
    private final RecoveryApprovalWorkflowService approvalWorkflowService;
    private final CoreInternalSecurityProperties securityProperties;
    private final RecoveryGovernanceProperties recoveryGovernanceProperties;

    public CoreRecoveryGovernanceController(AgentDirectoryFacade agentDirectory,
                                            TaskOrchestrationFacade taskOrchestration,
                                            ExecutionOperationalQuery executionQuery,
                                            DispatchRequestService dispatchRequestService,
                                            DispatchAttemptHistoryService attemptHistoryService,
                                            RecoveryApprovalWorkflowService approvalWorkflowService,
                                            CoreInternalSecurityProperties securityProperties,
                                            RecoveryGovernanceProperties recoveryGovernanceProperties) {
        this.agentDirectory = agentDirectory;
        this.taskOrchestration = taskOrchestration;
        this.executionQuery = executionQuery;
        this.dispatchRequestService = dispatchRequestService;
        this.attemptHistoryService = attemptHistoryService;
        this.approvalWorkflowService = approvalWorkflowService;
        this.securityProperties = securityProperties;
        this.recoveryGovernanceProperties = recoveryGovernanceProperties;
    }

    @PostMapping("/actions/agents/{agentId}/clear-runtime-backoff")
    public RecoveryGovernanceActionResult<AgentSnapshot> clearRuntimeBackoff(@PathVariable String agentId,
                                                                             @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                             HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("CLEAR_RUNTIME_BACKOFF", RiskLevel.MODERATE, ROLE_RECOVERY_OPERATOR, request, httpRequest);
        OffsetDateTime now = now();
        AgentSnapshot before = agentDirectory.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        attemptHistoryService.recordOperatorRuntimeBackoffCleared(before, action.auditMetadata(), action.reason(), now);
        AgentSnapshot cleared = agentDirectory.clearRuntimeBackoff(agentId, action.reason())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        return RecoveryGovernanceActionResult.success(
                "CLEAR_RUNTIME_BACKOFF",
                "Runtime backoff cleared for agent " + agentId,
                cleared,
                action.auditSummary(),
                List.of("Check whether runtime delivery failures stop increasing.", "If failures continue, disconnect the session or quarantine the Agent."),
                "runtime-delivery-failures");
    }

    @PostMapping("/actions/tasks/{taskId}/recover-now")
    public RecoveryGovernanceActionResult<AssignmentDecisionResult> triggerTaskRecoveryNow(@PathVariable String taskId,
                                                                                          @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                                          HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("TRIGGER_TASK_RECOVERY_NOW", RiskLevel.MODERATE, ROLE_RECOVERY_OPERATOR, request, httpRequest);
        OffsetDateTime now = now();
        TaskRecord task = requireTask(taskId);
        if (isTerminal(task.getStatus())) {
            throw new IllegalStateException("Terminal task cannot be recovered immediately: " + taskId + " status=" + task.getStatus());
        }
        attemptHistoryService.recordOperatorRecoveryTriggered(task, action.auditMetadata(), action.reason(), now);
        AssignmentDecisionResult result = taskOrchestration.recoverTaskDispatchNow(taskId, action.reason(), now);
        return RecoveryGovernanceActionResult.success(
                "TRIGGER_TASK_RECOVERY_NOW",
                "Immediate recovery attempted for task " + taskId,
                result,
                action.auditSummary(),
                List.of("Refresh the task detail and inspect Core Dispatch Attempt History.", "If result is NO_CANDIDATE, check Agent runtime backoff, approved skills, capacity, and governance scope."),
                "delayed-requeue-volume");
    }

    @PostMapping("/actions/tasks/{taskId}/dead-letter")
    public RecoveryGovernanceActionResult<?> moveTaskLatestDispatchToDeadLetter(@PathVariable String taskId,
                                                                                @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                                HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("MOVE_TASK_LATEST_DISPATCH_TO_DEAD_LETTER", RiskLevel.HIGH, ROLE_RECOVERY_ADMIN, request, httpRequest);
        DispatchRequest dispatch = latestDispatchForTask(taskId);
        if (requiresApproval(action)) {
            return createApprovalRequest(action, "DISPATCH_REQUEST", dispatch.getDispatchRequestId(), dispatch,
                    "Task latest dispatch dead-letter requires second-person approval");
        }
        return deadLetterDispatch(dispatch.getDispatchRequestId(), action, "Task latest dispatch moved to dead-letter by operator");
    }

    @PostMapping("/actions/tasks/{taskId}/restore-dead-letter")
    public RecoveryGovernanceActionResult<?> restoreTaskLatestDeadLetter(@PathVariable String taskId,
                                                                         @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                         HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("RESTORE_TASK_LATEST_DEAD_LETTER", RiskLevel.HIGH, ROLE_RECOVERY_ADMIN, request, httpRequest);
        DispatchRequest dispatch = latestDeadLetterForTask(taskId);
        if (requiresApproval(action)) {
            return createApprovalRequest(action, "DISPATCH_REQUEST", dispatch.getDispatchRequestId(), dispatch,
                    "Task latest dead-letter restore requires second-person approval");
        }
        return restoreDispatch(dispatch.getDispatchRequestId(), action, "Task latest dead-letter dispatch restored by operator");
    }

    @PostMapping("/actions/dispatch-requests/{dispatchRequestId}/dead-letter")
    public RecoveryGovernanceActionResult<?> moveDispatchToDeadLetter(@PathVariable String dispatchRequestId,
                                                                      @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                      HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("MOVE_DISPATCH_TO_DEAD_LETTER", RiskLevel.HIGH, ROLE_RECOVERY_ADMIN, request, httpRequest);
        DispatchRequest dispatch = executionQuery.findDispatchRequest(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        if (requiresApproval(action)) {
            return createApprovalRequest(action, "DISPATCH_REQUEST", dispatchRequestId, dispatch,
                    "Dispatch dead-letter requires second-person approval");
        }
        return deadLetterDispatch(dispatchRequestId, action, "Dispatch moved to dead-letter by operator");
    }

    @PostMapping("/actions/dispatch-requests/{dispatchRequestId}/restore-dead-letter")
    public RecoveryGovernanceActionResult<?> restoreDispatchFromDeadLetter(@PathVariable String dispatchRequestId,
                                                                           @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                           HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("RESTORE_DISPATCH_FROM_DEAD_LETTER", RiskLevel.HIGH, ROLE_RECOVERY_ADMIN, request, httpRequest);
        DispatchRequest dispatch = executionQuery.findDispatchRequest(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        if (requiresApproval(action)) {
            return createApprovalRequest(action, "DISPATCH_REQUEST", dispatchRequestId, dispatch,
                    "Dispatch dead-letter restore requires second-person approval");
        }
        return restoreDispatch(dispatchRequestId, action, "Dispatch restored from dead-letter by operator");
    }

    @GetMapping("/approval-requests")
    public List<RecoveryApprovalRequest> approvalRequests(@RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "100") int limit) {
        RecoveryApprovalStatus parsed = parseStatus(status);
        return parsed == null ? approvalWorkflowService.recent(limit) : approvalWorkflowService.findByStatus(parsed, limit);
    }

    @GetMapping("/approval-requests/{approvalId}")
    public RecoveryApprovalRequest approvalRequest(@PathVariable String approvalId) {
        return approvalWorkflowService.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Recovery approval request not found: " + approvalId));
    }

    @PostMapping("/approval-requests/{approvalId}/approve")
    public RecoveryGovernanceActionResult<?> approveRecoveryRequest(@PathVariable String approvalId,
                                                                    @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                    HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("APPROVE_RECOVERY_APPROVAL", RiskLevel.HIGH, ROLE_RECOVERY_APPROVER, request, httpRequest);
        RecoveryApprovalRequest approval = requireApproval(approvalId);
        assertPendingApproval(approval);
        assertNotSelfApproval(approval, action);
        OffsetDateTime at = now();
        approval.setStatus(RecoveryApprovalStatus.APPROVED);
        approval.setApprovedBy(action.auditMetadata().operatorId());
        approval.setApproverPrincipal(action.auditMetadata().principal());
        approval.setApproverRole(action.auditMetadata().role());
        approval.setApprovalReason(action.reason());
        approval.setApprovalRequestId(action.auditMetadata().requestId());
        approval.setApprovalClientAddress(action.auditMetadata().clientAddress());
        approval.setApprovalUserAgent(action.auditMetadata().userAgent());
        approval.setApprovedAt(at);
        approval.setUpdatedAt(at);
        approvalWorkflowService.save(approval);
        attemptHistoryService.recordRecoveryApprovalEvent(approval, DispatchAttemptHistoryService.EVENT_RECOVERY_APPROVAL_APPROVED, action.auditMetadata(), action.reason(), at);
        try {
            RecoveryGovernanceActionResult<DispatchRequest> execution = executeApprovedRecovery(approval, action);
            OffsetDateTime executedAt = now();
            approval.setStatus(RecoveryApprovalStatus.EXECUTED);
            approval.setExecutionResult("EXECUTED");
            approval.setExecutedAt(executedAt);
            approval.setUpdatedAt(executedAt);
            approvalWorkflowService.save(approval);
            attemptHistoryService.recordRecoveryApprovalEvent(approval, DispatchAttemptHistoryService.EVENT_RECOVERY_APPROVAL_EXECUTED, action.auditMetadata(), action.reason(), executedAt);
            return RecoveryGovernanceActionResult.success(
                    "APPROVE_AND_EXECUTE_RECOVERY",
                    "Recovery approval request approved and executed: " + approvalId,
                    execution.payload(),
                    action.auditSummary(),
                    execution.nextActions(),
                    execution.runbookRef(),
                    false,
                    approval);
        } catch (RuntimeException ex) {
            OffsetDateTime failedAt = now();
            approval.setStatus(RecoveryApprovalStatus.FAILED);
            approval.setExecutionResult("FAILED");
            approval.setExecutionError(ex.getMessage());
            approval.setUpdatedAt(failedAt);
            approvalWorkflowService.save(approval);
            attemptHistoryService.recordRecoveryApprovalEvent(approval, DispatchAttemptHistoryService.EVENT_RECOVERY_APPROVAL_FAILED, action.auditMetadata(), ex.getMessage(), failedAt);
            throw ex;
        }
    }

    @PostMapping("/approval-requests/{approvalId}/reject")
    public RecoveryGovernanceActionResult<RecoveryApprovalRequest> rejectRecoveryRequest(@PathVariable String approvalId,
                                                                                         @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                                         HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("REJECT_RECOVERY_APPROVAL", RiskLevel.HIGH, ROLE_RECOVERY_APPROVER, request, httpRequest);
        RecoveryApprovalRequest approval = requireApproval(approvalId);
        assertPendingApproval(approval);
        assertNotSelfApproval(approval, action);
        OffsetDateTime at = now();
        approval.setStatus(RecoveryApprovalStatus.REJECTED);
        approval.setRejectedBy(action.auditMetadata().operatorId());
        approval.setRejectedReason(action.reason());
        approval.setRejectedAt(at);
        approval.setUpdatedAt(at);
        approvalWorkflowService.save(approval);
        attemptHistoryService.recordRecoveryApprovalEvent(approval, DispatchAttemptHistoryService.EVENT_RECOVERY_APPROVAL_REJECTED, action.auditMetadata(), action.reason(), at);
        return RecoveryGovernanceActionResult.success(
                "REJECT_RECOVERY_APPROVAL",
                "Recovery approval request rejected: " + approvalId,
                approval,
                action.auditSummary(),
                List.of("No recovery action was executed.", "Open a new approval request after the underlying risk is clarified."),
                "dead-letter-volume");
    }

    @PostMapping("/approval-requests/{approvalId}/cancel")
    public RecoveryGovernanceActionResult<RecoveryApprovalRequest> cancelRecoveryRequest(@PathVariable String approvalId,
                                                                                         @RequestBody(required = false) RecoveryGovernanceActionRequest request,
                                                                                         HttpServletRequest httpRequest) {
        ValidatedRecoveryAction action = validateAction("CANCEL_RECOVERY_APPROVAL", RiskLevel.MODERATE, ROLE_RECOVERY_OPERATOR, request, httpRequest);
        RecoveryApprovalRequest approval = requireApproval(approvalId);
        assertPendingApproval(approval);
        OffsetDateTime at = now();
        approval.setStatus(RecoveryApprovalStatus.CANCELLED);
        approval.setCancelledBy(action.auditMetadata().operatorId());
        approval.setCancelledReason(action.reason());
        approval.setCancelledAt(at);
        approval.setUpdatedAt(at);
        approvalWorkflowService.save(approval);
        attemptHistoryService.recordRecoveryApprovalEvent(approval, DispatchAttemptHistoryService.EVENT_RECOVERY_APPROVAL_CANCELLED, action.auditMetadata(), action.reason(), at);
        return RecoveryGovernanceActionResult.success(
                "CANCEL_RECOVERY_APPROVAL",
                "Recovery approval request cancelled: " + approvalId,
                approval,
                action.auditSummary(),
                List.of("No recovery action was executed.", "Create a new request if the action is still needed."),
                "dead-letter-volume");
    }

    @GetMapping("/runbook")
    public RecoveryOperatorRunbook runbook() {
        return new RecoveryOperatorRunbook(
                "P10.7",
                new RecoveryGovernancePolicyView(
                        recoveryGovernanceProperties.isRequireReason(),
                        recoveryGovernanceProperties.getMinReasonLength(),
                        recoveryGovernanceProperties.isRequireConfirmation(),
                        recoveryGovernanceProperties.getModerateConfirmationPhrase(),
                        recoveryGovernanceProperties.getHighRiskConfirmationPhrase(),
                        recoveryGovernanceProperties.getApprovalConfirmationPhrase(),
                        recoveryGovernanceProperties.isRequireDualControlForHighRisk(),
                        recoveryGovernanceProperties.isForbidSelfApproval(),
                        recoveryGovernanceProperties.getApprovalTtl().toString(),
                        "RECOVERY_OPERATOR",
                        "RECOVERY_ADMIN",
                        "RECOVERY_APPROVER"),
                List.of(
                        entry(
                                "runtime-delivery-failures",
                                "Runtime delivery failure / Agent backoff",
                                List.of("Open the failed task timeline and identify RUNTIME_DELIVERY_FAILED / RUNTIME_BACKOFF_APPLIED events.", "Open the Agent detail page and confirm runtime session, authorization state, heartbeat age, capacity, and duplicate runtime state."),
                                List.of("Requires RECOVERY_OPERATOR role, a human reason, risk acknowledgement, and confirmation phrase " + recoveryGovernanceProperties.getModerateConfirmationPhrase() + ".", "Clear runtime backoff only after the Agent is healthy or after disconnect/reconnect has completed.", "If failures continue after clearing backoff, disconnect all sessions and require credential/runtime redeploy."),
                                List.of("Escalate to platform/network owner if multiple agents on the same gateway fail delivery.", "Escalate to Agent owner if only one Agent repeatedly fails.")),
                        entry(
                                "delayed-requeue-volume",
                                "No assignable Agent / delayed task recovery",
                                List.of("Check delayed task nextDispatchAttemptAt and dispatchRetryReason.", "Verify approved skills, effective capability, scope, runtime backoff, and capacity."),
                                List.of("Requires RECOVERY_OPERATOR role, a human reason, risk acknowledgement, and confirmation phrase " + recoveryGovernanceProperties.getModerateConfirmationPhrase() + ".", "Use Trigger Recovery Now after a candidate Agent becomes available.", "Do not repeatedly force recovery while all candidates remain excluded; it only increases attempt count/noise."),
                                List.of("Escalate to governance owner if approved scope is missing.", "Escalate to Agent fleet owner if capacity is exhausted.")),
                        entry(
                                "dead-letter-volume",
                                "Dead-letter recovery with dual control",
                                List.of("Inspect the dispatch request lastError and attempt history before restoring.", "Decide whether the failure is transient, invalid command, or policy-related.", "For high-risk actions, first create a recovery approval request and have a different RECOVERY_APPROVER approve it."),
                                List.of("Request step requires RECOVERY_ADMIN, a reason, risk acknowledgement, and high-risk confirmation phrase " + recoveryGovernanceProperties.getHighRiskConfirmationPhrase() + ".", "Approval step requires RECOVERY_APPROVER, a different operator when self-approval is forbidden, and approval confirmation phrase " + recoveryGovernanceProperties.getApprovalConfirmationPhrase() + ".", "Restore dead-letter only for transient runtime or gateway failures."),
                                List.of("Escalate invalid command to Core/Agent contract owner.", "Escalate repeated dead-letter or bulk restore to release manager before approval.")),
                        entry(
                                "scanner-failures",
                                "Recovery scanner failure",
                                List.of("Check DELAYED_REQUEUE_FAILED records and workerId/claimUntil.", "Confirm DB lease/claim update and task assignment service errors."),
                                List.of("Run a manual recovery scan for a small batch after fixing the underlying error.", "Avoid increasing batch size until scanner failures return to zero."),
                                List.of("Escalate to Core persistence owner for SQL/lease failures.", "Escalate to routing owner for repeated assignment exceptions.")),
                        entry(
                                "recovery-exhausted",
                                "Task recovery exhausted",
                                List.of("Open the task timeline and count delayed attempts.", "Confirm whether the task requirements can be satisfied by any approved Agent."),
                                List.of("Fix the capability/scope/capacity issue, then manually trigger recovery or reassign.", "Cancel the task if the business event is obsolete."),
                                List.of("Escalate to business process owner before cancelling high-priority tasks.", "Escalate to governance owner if no Agent is authorized for required data class/system."))
                ));
    }

    private RecoveryGovernanceActionResult<RecoveryApprovalRequest> createApprovalRequest(ValidatedRecoveryAction action,
                                                                                          String targetType,
                                                                                          String targetId,
                                                                                          DispatchRequest dispatch,
                                                                                          String message) {
        OffsetDateTime at = now();
        RecoveryApprovalRequest approval = new RecoveryApprovalRequest();
        approval.setApprovalId("recovery-approval-" + UUID.randomUUID());
        approval.setStatus(RecoveryApprovalStatus.PENDING);
        approval.setAction(action.action());
        approval.setTargetType(targetType);
        approval.setTargetId(targetId);
        approval.setDispatchRequestId(dispatch == null ? null : dispatch.getDispatchRequestId());
        approval.setTaskId(dispatch == null ? null : dispatch.getTaskId());
        approval.setAgentId(dispatch == null ? null : dispatch.getAgentId());
        approval.setRiskLevel(action.riskLevel().name());
        approval.setRequestedBy(action.auditMetadata().operatorId());
        approval.setRequesterPrincipal(action.auditMetadata().principal());
        approval.setRequesterRole(action.auditMetadata().role());
        approval.setRequestReason(action.reason());
        approval.setRequestId(action.auditMetadata().requestId());
        approval.setRequestClientAddress(action.auditMetadata().clientAddress());
        approval.setRequestUserAgent(action.auditMetadata().userAgent());
        approval.setExpiresAt(at.plus(recoveryGovernanceProperties.getApprovalTtl()));
        approval.setCreatedAt(at);
        approval.setUpdatedAt(at);
        approval.setPayloadJson("{}");
        approvalWorkflowService.save(approval);
        attemptHistoryService.recordRecoveryApprovalEvent(approval, DispatchAttemptHistoryService.EVENT_RECOVERY_APPROVAL_REQUESTED, action.auditMetadata(), action.reason(), at);
        return RecoveryGovernanceActionResult.success(
                "REQUEST_RECOVERY_APPROVAL",
                message + ": " + approval.getApprovalId(),
                approval,
                action.auditSummary(),
                List.of("A different RECOVERY_APPROVER must approve this request before the recovery mutation executes.", "Open /admin/recovery/approval-requests?status=PENDING to review pending approvals."),
                "dead-letter-volume",
                true,
                approval);
    }

    private RecoveryGovernanceActionResult<DispatchRequest> deadLetterDispatch(String dispatchRequestId,
                                                                               ValidatedRecoveryAction action,
                                                                               String fallbackReason) {
        OffsetDateTime at = now();
        DispatchRequest current = executionQuery.findDispatchRequest(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        if (current.getStatus() == DispatchRequestStatus.COMPLETED) {
            throw new IllegalStateException("Completed dispatch request cannot be moved to dead-letter: " + dispatchRequestId);
        }
        DispatchRequest moved = dispatchRequestService.moveToDeadLetter(dispatchRequestId, firstNonBlank(action.reason(), fallbackReason));
        attemptHistoryService.recordOperatorDeadLetterMoved(moved, action.auditMetadata(), firstNonBlank(action.reason(), fallbackReason), at);
        return RecoveryGovernanceActionResult.success(
                "MOVE_TO_DEAD_LETTER",
                "Dispatch request moved to dead-letter: " + dispatchRequestId,
                moved,
                action.auditSummary(),
                List.of("Inspect attempt history and lastError before restoring.", "Restore only after the invalid contract or runtime cause is fixed."),
                "dead-letter-volume");
    }

    private RecoveryGovernanceActionResult<DispatchRequest> restoreDispatch(String dispatchRequestId,
                                                                            ValidatedRecoveryAction action,
                                                                            String fallbackReason) {
        OffsetDateTime at = now();
        DispatchRequest current = executionQuery.findDispatchRequest(dispatchRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch request not found: " + dispatchRequestId));
        if (current.getStatus() != DispatchRequestStatus.DEAD_LETTER) {
            throw new IllegalStateException("Only DEAD_LETTER dispatch request can be restored: " + dispatchRequestId + " status=" + current.getStatus());
        }
        DispatchRequest restored = dispatchRequestService.retry(
                dispatchRequestId,
                firstNonBlank(action.reason(), fallbackReason),
                action.request() == null || action.request().resetAttempts() == null || Boolean.TRUE.equals(action.request().resetAttempts()),
                action.request() == null || action.request().immediate() == null || Boolean.TRUE.equals(action.request().immediate())
        );
        attemptHistoryService.recordOperatorDeadLetterRestored(restored, action.auditMetadata(), firstNonBlank(action.reason(), fallbackReason), at);
        return RecoveryGovernanceActionResult.success(
                "RESTORE_DEAD_LETTER",
                "Dispatch request restored from dead-letter: " + dispatchRequestId,
                restored,
                action.auditSummary(),
                List.of("Execute approved dispatches or wait for the dispatch executor.", "Watch for another RUNTIME_DELIVERY_FAILED or DEAD_LETTERED event."),
                "dead-letter-volume");
    }

    private RecoveryGovernanceActionResult<DispatchRequest> executeApprovedRecovery(RecoveryApprovalRequest approval, ValidatedRecoveryAction approverAction) {
        ValidatedRecoveryAction executionAction = new ValidatedRecoveryAction(
                approval.getAction(),
                RiskLevel.HIGH,
                ROLE_RECOVERY_APPROVER,
                firstNonBlank(approverAction.reason(), approval.getRequestReason()),
                approverAction.request(),
                approverAction.auditMetadata(),
                approverAction.auditSummary());
        return switch (approval.getAction()) {
            case "MOVE_TASK_LATEST_DISPATCH_TO_DEAD_LETTER", "MOVE_DISPATCH_TO_DEAD_LETTER" ->
                    deadLetterDispatch(approval.getDispatchRequestId(), executionAction, approval.getRequestReason());
            case "RESTORE_TASK_LATEST_DEAD_LETTER", "RESTORE_DISPATCH_FROM_DEAD_LETTER" ->
                    restoreDispatch(approval.getDispatchRequestId(), executionAction, approval.getRequestReason());
            default -> throw new IllegalStateException("Unsupported recovery approval action: " + approval.getAction());
        };
    }

    private boolean requiresApproval(ValidatedRecoveryAction action) {
        return action != null
                && action.riskLevel() == RiskLevel.HIGH
                && recoveryGovernanceProperties.isRequireDualControlForHighRisk();
    }

    private ValidatedRecoveryAction validateAction(String actionName,
                                                   RiskLevel riskLevel,
                                                   String requiredAuthority,
                                                   RecoveryGovernanceActionRequest request,
                                                   HttpServletRequest httpRequest) {
        if (!recoveryGovernanceProperties.isEnabled()) {
            throw new IllegalStateException("Recovery governance actions are disabled by configuration");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (securityProperties.isEnabled() && !hasAuthority(authentication, requiredAuthority)) {
            throw new AccessDeniedException("Recovery action requires authority " + requiredAuthority + ": " + actionName);
        }

        String reason = request == null ? null : trimToNull(request.reason());
        if (recoveryGovernanceProperties.isRequireReason()) {
            if (reason == null) {
                throw new IllegalArgumentException("Recovery action reason is required: " + actionName);
            }
            if (reason.length() < recoveryGovernanceProperties.getMinReasonLength()) {
                throw new IllegalArgumentException("Recovery action reason must be at least " + recoveryGovernanceProperties.getMinReasonLength() + " characters: " + actionName);
            }
        }

        boolean riskAcknowledged = request != null && Boolean.TRUE.equals(request.riskAcknowledged());
        if (recoveryGovernanceProperties.isRequireConfirmation()) {
            if (!riskAcknowledged) {
                throw new IllegalArgumentException("Recovery action requires riskAcknowledged=true: " + actionName);
            }
            String expected = expectedConfirmationPhrase(actionName, riskLevel);
            String supplied = request == null ? null : trimToNull(request.confirmationPhrase());
            if (!expected.equals(supplied)) {
                throw new IllegalArgumentException("Recovery action requires confirmation phrase " + expected + ": " + actionName);
            }
        }

        String principal = authentication == null ? null : trimToNull(authentication.getName());
        String operatorId = operatorId(request, httpRequest, principal);
        String requestId = firstNonBlank(
                request == null ? null : request.requestId(),
                header(httpRequest, "X-Request-Id"),
                "recovery-action-" + UUID.randomUUID());
        String role = authorityToRole(requiredAuthority);
        RecoveryOperatorAuditMetadata metadata = new RecoveryOperatorAuditMetadata(
                operatorId,
                firstNonBlank(principal, operatorId),
                role,
                actionName,
                riskLevel.name(),
                requestId,
                clientAddress(httpRequest),
                header(httpRequest, "User-Agent"),
                recoveryGovernanceProperties.isRequireConfirmation() ? confirmationPolicy(actionName, riskLevel) : "NOT_REQUIRED");
        return new ValidatedRecoveryAction(actionName, riskLevel, requiredAuthority, reason, request, metadata,
                new RecoveryGovernanceAuditSummary(operatorId, role, riskLevel.name(), requestId));
    }

    private String expectedConfirmationPhrase(String actionName, RiskLevel riskLevel) {
        if (actionName != null && (actionName.startsWith("APPROVE_RECOVERY_APPROVAL") || actionName.startsWith("REJECT_RECOVERY_APPROVAL"))) {
            return recoveryGovernanceProperties.getApprovalConfirmationPhrase();
        }
        return riskLevel == RiskLevel.HIGH
                ? recoveryGovernanceProperties.getHighRiskConfirmationPhrase()
                : recoveryGovernanceProperties.getModerateConfirmationPhrase();
    }

    private String confirmationPolicy(String actionName, RiskLevel riskLevel) {
        if (actionName != null && (actionName.startsWith("APPROVE_RECOVERY_APPROVAL") || actionName.startsWith("REJECT_RECOVERY_APPROVAL"))) {
            return "DUAL_CONTROL_APPROVAL_PHRASE";
        }
        return riskLevel == RiskLevel.HIGH ? "HIGH_RISK_PHRASE" : "MODERATE_PHRASE";
    }

    private void assertPendingApproval(RecoveryApprovalRequest approval) {
        if (approval.getStatus() != RecoveryApprovalStatus.PENDING) {
            throw new IllegalStateException("Recovery approval request is not pending: " + approval.getApprovalId() + " status=" + approval.getStatus());
        }
        if (approval.getExpiresAt() != null && approval.getExpiresAt().isBefore(now())) {
            approval.setStatus(RecoveryApprovalStatus.EXPIRED);
            approval.setUpdatedAt(now());
            approvalWorkflowService.save(approval);
            throw new IllegalStateException("Recovery approval request expired: " + approval.getApprovalId());
        }
    }

    private void assertNotSelfApproval(RecoveryApprovalRequest approval, ValidatedRecoveryAction action) {
        if (!recoveryGovernanceProperties.isForbidSelfApproval()) {
            return;
        }
        String requester = normalizeIdentity(approval.getRequestedBy());
        String requesterPrincipal = normalizeIdentity(approval.getRequesterPrincipal());
        String approver = normalizeIdentity(action.auditMetadata().operatorId());
        String approverPrincipal = normalizeIdentity(action.auditMetadata().principal());
        if ((requester != null && requester.equals(approver)) || (requesterPrincipal != null && requesterPrincipal.equals(approverPrincipal))) {
            throw new AccessDeniedException("Recovery approval requires a different operator than the requester: " + approval.getApprovalId());
        }
    }

    private RecoveryApprovalRequest requireApproval(String approvalId) {
        return approvalWorkflowService.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Recovery approval request not found: " + approvalId));
    }

    private RecoveryApprovalStatus parseStatus(String status) {
        String normalized = trimToNull(status);
        return normalized == null ? null : RecoveryApprovalStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null || authority == null) return false;
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private String operatorId(RecoveryGovernanceActionRequest request, HttpServletRequest httpRequest, String principal) {
        String bodyOperator = request == null ? null : trimToNull(request.operatorId());
        if (recoveryGovernanceProperties.isAllowBodyOperatorIdOverride() && bodyOperator != null) {
            return bodyOperator;
        }
        return firstNonBlank(header(httpRequest, "X-Operator-Id"), principal, bodyOperator, "admin-ui");
    }

    private String clientAddress(HttpServletRequest request) {
        return firstNonBlank(header(request, "X-Forwarded-For"), request == null ? null : request.getRemoteAddr(), "unknown");
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? null : trimToNull(request.getHeader(name));
    }

    private String authorityToRole(String authority) {
        if (authority == null) return "UNKNOWN";
        return authority.replaceFirst("^ROLE_", "").toUpperCase(Locale.ROOT);
    }

    private DispatchRequest latestDispatchForTask(String taskId) {
        return executionQuery.findDispatchRequestsByTask(taskId, 100).stream()
                .max(Comparator.comparing(this::dispatchSortTime))
                .orElseThrow(() -> new IllegalArgumentException("No dispatch request found for task: " + taskId));
    }

    private DispatchRequest latestDeadLetterForTask(String taskId) {
        return executionQuery.findDispatchRequestsByTask(taskId, 100).stream()
                .filter(request -> request.getStatus() == DispatchRequestStatus.DEAD_LETTER)
                .max(Comparator.comparing(this::dispatchSortTime))
                .orElseThrow(() -> new IllegalArgumentException("No DEAD_LETTER dispatch request found for task: " + taskId));
    }

    private OffsetDateTime dispatchSortTime(DispatchRequest request) {
        if (request.getUpdatedAt() != null) return request.getUpdatedAt();
        if (request.getCreatedAt() != null) return request.getCreatedAt();
        return OffsetDateTime.MIN;
    }

    private TaskRecord requireTask(String taskId) {
        return taskOrchestration.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }

    private boolean isTerminal(TaskStatus status) {
        return status != null && status.isTerminal();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private RecoveryRunbookEntry entry(String alertCode, String title, List<String> diagnosis, List<String> safeActions, List<String> escalation) {
        return new RecoveryRunbookEntry(alertCode, title, diagnosis, safeActions, escalation);
    }

    private String normalizeIdentity(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) return normalized;
        }
        return null;
    }

    public enum RiskLevel { MODERATE, HIGH }

    public record RecoveryGovernanceActionRequest(String operatorId,
                                                  String reason,
                                                  Boolean resetAttempts,
                                                  Boolean immediate,
                                                  Boolean riskAcknowledged,
                                                  String confirmationPhrase,
                                                  String requestId) {}

    private record ValidatedRecoveryAction(String action,
                                           RiskLevel riskLevel,
                                           String requiredAuthority,
                                           String reason,
                                           RecoveryGovernanceActionRequest request,
                                           RecoveryOperatorAuditMetadata auditMetadata,
                                           RecoveryGovernanceAuditSummary auditSummary) {}

    public record RecoveryGovernanceAuditSummary(String operatorId,
                                                 String requiredRole,
                                                 String riskLevel,
                                                 String requestId) {}

    public record RecoveryGovernanceActionResult<T>(boolean success,
                                                    String action,
                                                    String message,
                                                    String timestamp,
                                                    T payload,
                                                    RecoveryGovernanceAuditSummary audit,
                                                    List<String> nextActions,
                                                    String runbookRef,
                                                    boolean approvalRequired,
                                                    RecoveryApprovalRequest approval) {
        static <T> RecoveryGovernanceActionResult<T> success(String action,
                                                             String message,
                                                             T payload,
                                                             RecoveryGovernanceAuditSummary audit,
                                                             List<String> nextActions,
                                                             String runbookRef) {
            return success(action, message, payload, audit, nextActions, runbookRef, false, null);
        }

        static <T> RecoveryGovernanceActionResult<T> success(String action,
                                                             String message,
                                                             T payload,
                                                             RecoveryGovernanceAuditSummary audit,
                                                             List<String> nextActions,
                                                             String runbookRef,
                                                             boolean approvalRequired,
                                                             RecoveryApprovalRequest approval) {
            return new RecoveryGovernanceActionResult<>(true, action, message, OffsetDateTime.now(ZoneOffset.UTC).toString(), payload, audit, nextActions, runbookRef, approvalRequired, approval);
        }
    }

    public record RecoveryOperatorRunbook(String version,
                                          RecoveryGovernancePolicyView policy,
                                          List<RecoveryRunbookEntry> entries) {}

    public record RecoveryGovernancePolicyView(boolean requireReason,
                                               int minReasonLength,
                                               boolean requireConfirmation,
                                               String moderateConfirmationPhrase,
                                               String highRiskConfirmationPhrase,
                                               String approvalConfirmationPhrase,
                                               boolean requireDualControlForHighRisk,
                                               boolean forbidSelfApproval,
                                               String approvalTtl,
                                               String recoveryOperatorRole,
                                               String recoveryAdminRole,
                                               String recoveryApproverRole) {}

    public record RecoveryRunbookEntry(String alertCode,
                                       String title,
                                       List<String> diagnosis,
                                       List<String> safeActions,
                                       List<String> escalation) {}
}
