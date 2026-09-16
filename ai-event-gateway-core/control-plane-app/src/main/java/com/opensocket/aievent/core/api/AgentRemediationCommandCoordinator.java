package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentProfileUpdateCommand;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowActionExecutionRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowStore;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkillSyncCommand;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkillSyncResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.observability.AgentRemediationWorkflowMetricsService;
import com.opensocket.aievent.core.runtime.CoreRuntimeDisconnectClient;
import com.opensocket.aievent.core.runtime.RuntimeDisconnectException;
import com.opensocket.aievent.core.runtime.RuntimeDisconnectResult;

import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationActionView;
import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationWorkflowActionExecutionResult;
import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationWorkflowDecisionRequest;
import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationWorkflowHistoryEntry;
import com.opensocket.aievent.core.api.AgentRemediationController.AgentRemediationWorkflowResponse;

/**
 * Application command coordinator for remediation workflow execution.
 *
 * <p>This class owns lease acquisition/release, action-level idempotency,
 * partial-failure handling, execution recording and workflow completion while
 * the REST controller remains the HTTP boundary. It is intentionally
 * package-private and is not a Spring component, so it does not create a
 * second remediation authority.</p>
 */
final class AgentRemediationCommandCoordinator {
    private final AgentGovernanceService agentGovernanceService;
    private final AgentDirectoryService agentDirectoryService;
    private final AgentSkillRegistryService skillRegistryService;
    private final AgentRemediationWorkflowStore remediationWorkflowDao;
    private final CoreRuntimeDisconnectClient runtimeDisconnectClient;
    private final AgentRemediationWorkflowMetricsService remediationWorkflowMetrics;
    private final AgentRemediationWorkflowPersistence workflowPersistence;

    AgentRemediationCommandCoordinator(AgentGovernanceService agentGovernanceService,
                                       AgentDirectoryService agentDirectoryService,
                                       AgentSkillRegistryService skillRegistryService,
                                       AgentRemediationWorkflowStore remediationWorkflowDao,
                                       CoreRuntimeDisconnectClient runtimeDisconnectClient,
                                       AgentRemediationWorkflowMetricsService remediationWorkflowMetrics,
                                       AgentRemediationWorkflowPersistence workflowPersistence) {
        this.agentGovernanceService = agentGovernanceService;
        this.agentDirectoryService = agentDirectoryService;
        this.skillRegistryService = skillRegistryService;
        this.remediationWorkflowDao = remediationWorkflowDao;
        this.runtimeDisconnectClient = runtimeDisconnectClient;
        this.remediationWorkflowMetrics = remediationWorkflowMetrics;
        this.workflowPersistence = workflowPersistence;
    }

    AgentRemediationWorkflowResponse execute(String agentId,
                                             String workflowId,
                                             AgentRemediationWorkflowDecisionRequest request) {
        AgentRemediationWorkflowDecisionRequest body = request == null
                ? new AgentRemediationWorkflowDecisionRequest("system", "Agent remediation workflow execution requested.", false)
                : request;
        AgentRemediationWorkflowResponse current = workflowPersistence.require(agentId, workflowId);
        workflowPersistence.ensureActionExecutionRows(current);
        current = workflowPersistence.require(agentId, workflowId);
        if (!"APPROVED".equals(current.status())) {
            throw new IllegalStateException("Only APPROVED remediation workflows can be executed.");
        }

        boolean dryRun = Boolean.TRUE.equals(body.dryRun());
        String leaseOwner = executionLeaseOwner(body);
        OffsetDateTime leaseAcquiredAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime leaseExpiresAt = leaseAcquiredAt.plus(AgentRemediationWorkflowExecutionPolicy.WORKFLOW_EXECUTION_LEASE_DURATION);
        boolean leaseAcquired = false;
        if (!dryRun) {
            int acquired = remediationWorkflowDao.acquireWorkflowExecutionLease(
                    current.workflowId(),
                    "APPROVED",
                    leaseOwner,
                    firstNonBlank(body.operatorId(), "system"),
                    leaseAcquiredAt,
                    leaseExpiresAt);
            if (acquired != 1) {
                AgentRemediationWorkflowResponse latest = workflowPersistence.require(agentId, workflowId);
                Map<String, Object> busyMetadata = map(
                        "executionMode", AgentRemediationWorkflowExecutionPolicy.EXECUTION_MODE_LEASE_BUSY,
                        "leaseOwner", latest.executionLeaseOwner(),
                        "leaseExpiresAt", latest.executionLeaseExpiresAt(),
                        "leaseActive", latest.executionLeaseActive(),
                        "leaseRemainingSeconds", latest.executionLeaseRemainingSeconds());
                remediationWorkflowDao.insertHistory(workflowPersistence.toHistoryRecord(current.workflowId(), current.agentId(),
                        history("EXECUTION_LEASE_BUSY", body.operatorId(), body.reason(), busyMetadata)));
                remediationWorkflowMetrics.recordWorkflowLeaseEvent("EXECUTION_LEASE_BUSY");
                throw new IllegalStateException("Remediation workflow is already executing or its execution lease has not expired.");
            }
            leaseAcquired = true;
            remediationWorkflowDao.insertHistory(workflowPersistence.toHistoryRecord(current.workflowId(), current.agentId(),
                    history("EXECUTION_LEASE_ACQUIRED", body.operatorId(), body.reason(), map(
                            "leaseOwner", leaseOwner,
                            "leaseAcquiredAt", leaseAcquiredAt,
                            "leaseExpiresAt", leaseExpiresAt,
                            "leaseTtlSeconds", AgentRemediationWorkflowExecutionPolicy.WORKFLOW_EXECUTION_LEASE_DURATION.toSeconds()))));
            remediationWorkflowMetrics.recordWorkflowLeaseEvent("EXECUTION_LEASE_ACQUIRED");
            current = workflowPersistence.require(agentId, workflowId);
        }

        try {
            List<AgentRemediationWorkflowActionExecutionResult> results = executeWorkflowActions(current, body, dryRun);
            recordActionExecutionMetrics(results);
            boolean hasFailure = results.stream().anyMatch(result -> !result.success() && !result.skipped());
            Map<String, Object> executionMetadata = map(
                    "executionMode", dryRun ? AgentRemediationWorkflowExecutionPolicy.EXECUTION_MODE_DRY_RUN : AgentRemediationWorkflowExecutionPolicy.EXECUTION_MODE_LEASED_ACTION_LEVEL,
                    "dryRun", dryRun,
                    "workflowLeaseOwner", dryRun ? null : leaseOwner,
                    "workflowLeaseAcquiredAt", dryRun ? null : leaseAcquiredAt,
                    "workflowLeaseExpiresAt", dryRun ? null : leaseExpiresAt,
                    "workflowLeaseTtlSeconds", dryRun ? null : AgentRemediationWorkflowExecutionPolicy.WORKFLOW_EXECUTION_LEASE_DURATION.toSeconds(),
                    "actionTypes", current.actions().stream().map(AgentRemediationActionView::actionType).toList(),
                    "results", results,
                    "successCount", results.stream().filter(AgentRemediationWorkflowActionExecutionResult::success).count(),
                    "skippedCount", results.stream().filter(AgentRemediationWorkflowActionExecutionResult::skipped).count(),
                    "failureCount", results.stream().filter(result -> !result.success() && !result.skipped()).count());

            if (hasFailure) {
                remediationWorkflowMetrics.recordWorkflowExecution("FAILED", current.severity(), dryRun,
                        longValue(executionMetadata.get("successCount")),
                        longValue(executionMetadata.get("skippedCount")),
                        longValue(executionMetadata.get("failureCount")));
                remediationWorkflowDao.insertHistory(workflowPersistence.toHistoryRecord(current.workflowId(), current.agentId(),
                        history("EXECUTION_FAILED", body.operatorId(), body.reason(), executionMetadata)));
                AgentRemediationWorkflowResponse failedAttempt = workflowPersistence.require(current.agentId(), current.workflowId());
                persistWorkflowSecurityEvent(failedAttempt, AgentSecurityEventType.AGENT_REMEDIATION_WORKFLOW_EXECUTION_FAILED, body.operatorId(), body.reason(), executionMetadata);
                return failedAttempt;
            }

            AgentRemediationWorkflowResponse updated = workflowPersistence.updateStatus(current, "EXECUTED", firstNonBlank(body.operatorId(), "system"),
                    history("EXECUTED", body.operatorId(), body.reason(), executionMetadata));
            remediationWorkflowMetrics.recordWorkflowExecution(updated.status(), updated.severity(), dryRun,
                    longValue(executionMetadata.get("successCount")),
                    longValue(executionMetadata.get("skippedCount")),
                    longValue(executionMetadata.get("failureCount")));
            remediationWorkflowMetrics.recordWorkflowDecision("EXECUTED", current.status(), updated.status(), updated.severity());
            persistWorkflowSecurityEvent(updated, AgentSecurityEventType.AGENT_REMEDIATION_WORKFLOW_EXECUTED, body.operatorId(), body.reason(),
                    map("rollbackSuggestions", updated.rollbackSuggestions(), "executionResults", results, "dryRun", dryRun, "workflowLeaseOwner", leaseOwner));
            return updated;
        } finally {
            if (leaseAcquired) {
                releaseWorkflowExecutionLease(current.workflowId(), current.agentId(), leaseOwner, body);
            }
        }
    }

    private String executionLeaseOwner(AgentRemediationWorkflowDecisionRequest request) {
        return String.join(":",
                "core-remediation-executor",
                firstNonBlank(System.getenv("HOSTNAME"), System.getenv("COMPUTERNAME"), "local-core"),
                firstNonBlank(request == null ? null : request.operatorId(), "system"),
                UUID.randomUUID().toString());
    }

    private void releaseWorkflowExecutionLease(String workflowId,
                                               String agentId,
                                               String leaseOwner,
                                               AgentRemediationWorkflowDecisionRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int released = remediationWorkflowDao.releaseWorkflowExecutionLease(workflowId, leaseOwner, now);
        remediationWorkflowMetrics.recordWorkflowLeaseEvent(released == 1 ? "EXECUTION_LEASE_RELEASED" : "EXECUTION_LEASE_RELEASE_RACE");
        remediationWorkflowDao.insertHistory(workflowPersistence.toHistoryRecord(workflowId, agentId,
                history(released == 1 ? "EXECUTION_LEASE_RELEASED" : "EXECUTION_LEASE_RELEASE_RACE",
                        request == null ? null : request.operatorId(),
                        request == null ? null : request.reason(),
                        map("leaseOwner", leaseOwner, "released", released == 1, "releasedAt", now))));
    }

    private List<AgentRemediationWorkflowActionExecutionResult> executeWorkflowActions(AgentRemediationWorkflowResponse workflow,
                                                                                       AgentRemediationWorkflowDecisionRequest request,
                                                                                       boolean dryRun) {
        workflowPersistence.ensureActionExecutionRows(workflow);
        Map<String, AgentRemediationWorkflowActionExecutionRecord> executionsByActionId = actionExecutionsByActionId(workflow.workflowId());
        List<AgentRemediationWorkflowActionExecutionResult> results = new ArrayList<>();
        for (AgentRemediationActionView action : workflow.actions() == null ? List.<AgentRemediationActionView>of() : workflow.actions()) {
            if (action == null) {
                continue;
            }
            AgentRemediationWorkflowActionExecutionRecord execution = executionsByActionId.get(firstNonBlank(action.actionId(), action.actionType(), "UNKNOWN_ACTION"));
            if (execution == null) {
                workflowPersistence.ensureActionExecutionRows(workflow);
                execution = actionExecutionsByActionId(workflow.workflowId()).get(firstNonBlank(action.actionId(), action.actionType(), "UNKNOWN_ACTION"));
            }
            if (execution == null) {
                results.add(actionResult(action, false, false, "MISSING_ACTION_EXECUTION", "P9 action execution row is missing.", Map.of()));
                continue;
            }
            if (AgentRemediationWorkflowExecutionPolicy.isCompletedActionStatus(execution.getStatus())) {
                results.add(alreadyCompletedActionResult(action, execution));
                continue;
            }
            if (!action.executable()) {
                results.add(skipActionExecution(action, execution, request, "REVIEW_ONLY", "Action is review-only and was not executed."));
                continue;
            }
            if (dryRun) {
                results.add(actionResult(action, true, true, "DRY_RUN", "Dry run accepted; no governance action was executed.", map(
                        "actionExecutionId", execution.getActionExecutionId(),
                        "idempotencyKey", execution.getIdempotencyKey(),
                        "currentStatus", execution.getStatus(),
                        "attemptCount", execution.getAttemptCount(),
                        "commandHint", action.commandHint())));
                continue;
            }
            int claimed = remediationWorkflowDao.claimActionExecutionForRun(execution.getActionExecutionId(), operatorId(request), executionReason(request, action), OffsetDateTime.now(ZoneOffset.UTC));
            if (claimed != 1) {
                AgentRemediationWorkflowActionExecutionRecord latest = remediationWorkflowDao.findActionExecutionById(execution.getActionExecutionId());
                results.add(actionResult(action, true, true, "ACTION_NOT_CLAIMED", "Action was not claimed because another Core instance already changed its state.", map(
                        "actionExecutionId", execution.getActionExecutionId(),
                        "idempotencyKey", execution.getIdempotencyKey(),
                        "latestStatus", latest == null ? null : latest.getStatus(),
                        "attemptCount", latest == null ? null : latest.getAttemptCount())));
                continue;
            }
            try {
                AgentRemediationWorkflowActionExecutionResult result = executeSingleWorkflowAction(workflow, action, request);
                completeActionExecution(execution, result);
                results.add(withActionExecutionDetails(result, remediationWorkflowDao.findActionExecutionById(execution.getActionExecutionId())));
            } catch (Exception ex) {
                AgentRemediationWorkflowActionExecutionResult failed = actionResult(action, false, false, "FAILED", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), Map.of(
                        "exceptionType", ex.getClass().getName()));
                completeActionExecution(execution, failed);
                results.add(withActionExecutionDetails(failed, remediationWorkflowDao.findActionExecutionById(execution.getActionExecutionId())));
            }
        }
        if (results.isEmpty()) {
            results.add(new AgentRemediationWorkflowActionExecutionResult(
                    "no-actions", "NO_ACTIONS", false, true, "NOOP", "Workflow contains no executable actions.", Map.of()));
        }
        return results;
    }

    private void recordActionExecutionMetrics(List<AgentRemediationWorkflowActionExecutionResult> results) {
        if (results == null) return;
        for (AgentRemediationWorkflowActionExecutionResult result : results) {
            if (result == null) continue;
            Integer attemptCount = null;
            Object attemptValue = result.details() == null ? null : result.details().get("attemptCount");
            if (attemptValue instanceof Number number) {
                attemptCount = number.intValue();
            }
            remediationWorkflowMetrics.recordActionExecution(result.actionType(), result.status(), result.success(), result.skipped(), attemptCount);
        }
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Map<String, AgentRemediationWorkflowActionExecutionRecord> actionExecutionsByActionId(String workflowId) {
        Map<String, AgentRemediationWorkflowActionExecutionRecord> values = new LinkedHashMap<>();
        for (AgentRemediationWorkflowActionExecutionRecord po : remediationWorkflowDao.findActionExecutionsByWorkflowId(workflowId)) {
            values.put(po.getActionId(), po);
        }
        return values;
    }

    private boolean isCompletedActionStatus(String status) {
        return AgentRemediationWorkflowExecutionPolicy.isCompletedActionStatus(status);
    }

    private AgentRemediationWorkflowActionExecutionResult alreadyCompletedActionResult(AgentRemediationActionView action,
                                                                                      AgentRemediationWorkflowActionExecutionRecord execution) {
        boolean skipped = "SKIPPED".equals(execution.getStatus());
        return actionResult(action, true, true,
                skipped ? "ALREADY_SKIPPED" : "ALREADY_SUCCEEDED",
                "P9 idempotency guard skipped this action because it already reached a terminal action state.",
                actionExecutionDetails(execution));
    }

    private AgentRemediationWorkflowActionExecutionResult skipActionExecution(AgentRemediationActionView action,
                                                                             AgentRemediationWorkflowActionExecutionRecord execution,
                                                                             AgentRemediationWorkflowDecisionRequest request,
                                                                             String status,
                                                                             String message) {
        int claimed = remediationWorkflowDao.claimActionExecutionForRun(execution.getActionExecutionId(), operatorId(request), executionReason(request, action), OffsetDateTime.now(ZoneOffset.UTC));
        if (claimed == 1) {
            AgentRemediationWorkflowActionExecutionResult result = actionResult(action, true, true, "SKIPPED", message, Map.of("skipReason", status));
            completeActionExecution(execution, result);
            return withActionExecutionDetails(result, remediationWorkflowDao.findActionExecutionById(execution.getActionExecutionId()));
        }
        AgentRemediationWorkflowActionExecutionRecord latest = remediationWorkflowDao.findActionExecutionById(execution.getActionExecutionId());
        return actionResult(action, true, true, "ACTION_NOT_CLAIMED", "Action was not claimed because another Core instance already changed its state.", actionExecutionDetails(latest));
    }

    private void completeActionExecution(AgentRemediationWorkflowActionExecutionRecord execution,
                                         AgentRemediationWorkflowActionExecutionResult result) {
        if (execution == null || result == null) return;
        String nextStatus = result.success() && !result.skipped()
                ? "SUCCEEDED"
                : result.skipped() ? "SKIPPED" : "FAILED";
        String lastError = "FAILED".equals(nextStatus) ? result.message() : null;
        int updated = remediationWorkflowDao.completeActionExecutionIfRunning(
                execution.getActionExecutionId(),
                nextStatus,
                workflowPersistence.writeJson(result.details()),
                lastError,
                OffsetDateTime.now(ZoneOffset.UTC));
        if (updated != 1) {
            remediationWorkflowDao.insertHistory(workflowPersistence.toHistoryRecord(execution.getWorkflowId(), execution.getAgentId(),
                    history("ACTION_EXECUTION_COMPLETION_RACE", operatorId(null), "P9 action completion race detected.", map(
                            "actionExecutionId", execution.getActionExecutionId(),
                            "actionId", execution.getActionId(),
                            "nextStatus", nextStatus))));
        }
    }

    private AgentRemediationWorkflowActionExecutionResult withActionExecutionDetails(AgentRemediationWorkflowActionExecutionResult result,
                                                                                    AgentRemediationWorkflowActionExecutionRecord execution) {
        if (result == null || execution == null) return result;
        Map<String, Object> merged = new LinkedHashMap<>();
        if (result.details() != null) merged.putAll(result.details());
        merged.putAll(actionExecutionDetails(execution));
        return new AgentRemediationWorkflowActionExecutionResult(
                result.actionId(), result.actionType(), result.success(), result.skipped(), result.status(), result.message(), merged);
    }

    private Map<String, Object> actionExecutionDetails(AgentRemediationWorkflowActionExecutionRecord execution) {
        if (execution == null) return Map.of();
        return map(
                "actionExecutionId", execution.getActionExecutionId(),
                "idempotencyKey", execution.getIdempotencyKey(),
                "actionExecutionStatus", execution.getStatus(),
                "attemptCount", execution.getAttemptCount(),
                "lastOperatorId", execution.getLastOperatorId(),
                "lastAttemptAt", execution.getLastAttemptAt(),
                "completedAt", execution.getCompletedAt(),
                "lastError", execution.getLastError());
    }

    private AgentRemediationWorkflowActionExecutionResult executeSingleWorkflowAction(AgentRemediationWorkflowResponse workflow,
                                                                                      AgentRemediationActionView action,
                                                                                      AgentRemediationWorkflowDecisionRequest request) {
        if (action == null) {
            return new AgentRemediationWorkflowActionExecutionResult(
                    "unknown-action", "UNKNOWN", false, true, "SKIPPED",
                    "Null remediation action was skipped.", Map.of("skipReason", "NULL_ACTION"));
        }
        if (workflow == null) {
            return actionResult(action, false, false, "MISSING_WORKFLOW", "Workflow context is required for remediation action execution.", Map.of());
        }

        String agentId = firstNonBlank(action.agentId(), workflow.agentId());
        String actionType = firstNonBlank(action.actionType(), "UNKNOWN");

        if (contains(actionType, "CLEAR_RUNTIME_BACKOFF")) {
            AgentSnapshot snapshot = agentDirectoryService.clearRuntimeBackoff(agentId, executionReason(request, action))
                    .orElse(null);
            if (snapshot == null) {
                return actionResult(action, false, false, "FAILED",
                        "Runtime backoff could not be cleared because the Agent runtime snapshot was not found.",
                        map("agentId", agentId, "operation", "clearRuntimeBackoff"));
            }
            return actionResult(action, true, false, "EXECUTED",
                    "Runtime backoff and failure counter were cleared.",
                    map("agentId", agentId,
                            "operation", "clearRuntimeBackoff",
                            "runtimeFailureCount", snapshot.getRuntimeFailureCount(),
                            "runtimeBackoffUntil", snapshot.getRuntimeBackoffUntil(),
                            "runtimeBackoffReason", snapshot.getRuntimeBackoffReason()));
        }

        if (contains(actionType, "SUSPEND_AGENT")) {
            AgentProfile profile = agentGovernanceService.suspendAgent(agentId, operatorId(request), executionReason(request, action));
            return actionResult(action, true, false, "EXECUTED",
                    "Agent was suspended by the remediation workflow.",
                    map("agentId", agentId,
                            "operation", "suspendAgent",
                            "approvalStatus", profile.getApprovalStatus() == null ? null : profile.getApprovalStatus().name(),
                            "riskStatus", profile.getRiskStatus() == null ? null : profile.getRiskStatus().name(),
                            "enabled", profile.isEnabled(),
                            "policyVersion", profile.getPolicyVersion()));
        }

        if (contains(actionType, "SYNC_APPROVED_SKILLS") || contains(actionType, "SYNC_APPROVED_SKILLS_AND_CAPABILITIES")) {
            AgentApprovedSkillSyncResult result = executeSkillSync(agentId, action, request);
            return actionResult(action, true, false, "EXECUTED",
                    "Approved skills and governance capabilities were synchronized.",
                    map("agentId", agentId,
                            "operation", "syncApprovedSkillsAndCapabilities",
                            "approvedSkillCodes", result.getApprovedSkillCodes(),
                            "profileCapabilityCodes", result.getProfileCapabilityCodes(),
                            "addedToApprovedSkills", result.getAddedToApprovedSkills(),
                            "addedToProfileCapabilities", result.getAddedToProfileCapabilities(),
                            "profileCapabilitiesSynced", result.isProfileCapabilitiesSynced(),
                            "syncedAt", result.getSyncedAt()));
        }

        if (contains(actionType, "DISCONNECT_ALL_RUNTIME_SESSIONS") || contains(actionType, "DISCONNECT_ALL")) {
            RuntimeDisconnectResult result = executeRuntimeDisconnect(agentId, action, request);
            boolean success = result != null && (result.closed() || "SUCCESS".equalsIgnoreCase(result.status()) || "DISCONNECTED".equalsIgnoreCase(result.status()));
            return actionResult(action, success, false, success ? "EXECUTED" : "FAILED",
                    result == null ? "Runtime disconnect returned no result." : result.message(),
                    map("agentId", agentId,
                            "operation", "disconnectAllRuntimeSessions",
                            "gatewayNodeId", result == null ? null : result.gatewayNodeId(),
                            "status", result == null ? null : result.status(),
                            "requested", result == null ? null : result.requested(),
                            "closed", result == null ? null : result.closed(),
                            "httpStatus", result == null ? null : result.httpStatus(),
                            "occurredAt", result == null ? null : result.occurredAt(),
                            "details", result == null ? Map.of() : result.details()));
        }

        return actionResult(action, true, true, "SKIPPED",
                "Action is not supported by automatic P8/P9/P10 remediation execution and was left for manual review.",
                map("agentId", agentId, "actionType", actionType, "skipReason", "UNSUPPORTED_AUTOMATION"));
    }

    private AgentApprovedSkillSyncResult executeSkillSync(String agentId,
                                                          AgentRemediationActionView action,
                                                          AgentRemediationWorkflowDecisionRequest request) {
        AgentProfile profile = agentGovernanceService.getProfile(agentId);
        AgentApprovedSkillSyncCommand command = new AgentApprovedSkillSyncCommand();
        command.setSkillCodes(skillCodesFromAction(action));
        command.setEnabled(true);
        command.setSyncProfileCapabilities(true);
        command.setOperatorId(operatorId(request));
        command.setReason(executionReason(request, action));
        AgentApprovedSkillSyncResult preview = skillRegistryService.buildSyncResult(agentId, profile, command.getSkillCodes(), false,
                "Preview approved skill/profile capability union before P8 workflow execution.");
        command.setSkillCodes(preview.getApprovedSkillCodes());
        skillRegistryService.replaceApprovedSkills(agentId, command, profile);
        syncProfileCapabilities(agentId, preview, command);
        AgentProfile updated = agentGovernanceService.getProfile(agentId);
        return skillRegistryService.buildSyncResult(agentId, updated, preview.getApprovedSkillCodes(), true,
                "Approved skill table and governance capabilities synchronized by P8 remediation workflow execution.");
    }

    private void syncProfileCapabilities(String agentId, AgentApprovedSkillSyncResult result, AgentApprovedSkillSyncCommand command) {
        AgentProfileUpdateCommand update = new AgentProfileUpdateCommand();
        update.setCapabilities(result.getProfileCapabilityCodes());
        update.setOperatorId(command == null || command.getOperatorId() == null ? "agent-remediation-workflow" : command.getOperatorId());
        update.setReason(command == null || command.getReason() == null
                ? "Synchronize Agent approved skills with governance capabilities from remediation workflow."
                : command.getReason());
        agentGovernanceService.updateProfile(agentId, update);
    }

    private RuntimeDisconnectResult executeRuntimeDisconnect(String agentId,
                                                            AgentRemediationActionView action,
                                                            AgentRemediationWorkflowDecisionRequest request) {
        String ownerGatewayNodeId = firstNonBlank(stringValue(action.commandHint(), "gatewayNodeId"), ownerGatewayNodeId(agentId));
        if (ownerGatewayNodeId == null || ownerGatewayNodeId.isBlank()) {
            return RuntimeDisconnectResult.failed(agentId, null, 0, "No owner gateway node is available for P8 workflow runtime disconnect.");
        }
        try {
            return runtimeDisconnectClient.disconnectAgent(agentId, ownerGatewayNodeId, executionReason(request, action), operatorId(request));
        } catch (RuntimeDisconnectException ex) {
            if (ex.getResult() != null) return ex.getResult();
            return RuntimeDisconnectResult.failed(agentId, ownerGatewayNodeId, 0, ex.getMessage());
        }
    }

    private String ownerGatewayNodeId(String agentId) {
        try {
            return agentDirectoryService.findById(agentId)
                    .map(AgentSnapshot::getOwnerGatewayNodeId)
                    .filter(value -> value != null && !value.isBlank())
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> skillCodesFromAction(AgentRemediationActionView action) {
        List<String> values = new ArrayList<>();
        addStringValues(values, action == null || action.commandHint() == null ? null : action.commandHint().get("skillCodes"));
        addStringValues(values, action == null || action.metadata() == null ? null : action.metadata().get("skillCodes"));
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private void addStringValues(List<String> target, Object value) {
        if (target == null || value == null) return;
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !String.valueOf(item).isBlank()) target.add(String.valueOf(item));
            }
        } else if (!String.valueOf(value).isBlank()) {
            target.add(String.valueOf(value));
        }
    }

    private String executionReason(AgentRemediationWorkflowDecisionRequest request, AgentRemediationActionView action) {
        return firstNonBlank(request == null ? null : request.reason(), "P8 remediation workflow executed action " + (action == null ? "UNKNOWN" : action.actionType()));
    }

    private String operatorId(AgentRemediationWorkflowDecisionRequest request) {
        return firstNonBlank(request == null ? null : request.operatorId(), "system");
    }

    private AgentRemediationWorkflowActionExecutionResult actionResult(AgentRemediationActionView action,
                                                                       boolean success,
                                                                       boolean skipped,
                                                                       String status,
                                                                       String message,
                                                                       Map<String, Object> details) {
        return new AgentRemediationWorkflowActionExecutionResult(
                action.actionId(),
                action.actionType(),
                success,
                skipped,
                firstNonBlank(status, success ? "EXECUTED" : "FAILED"),
                firstNonBlank(message, status),
                details == null ? Map.of() : details);
    }

    private AgentRemediationWorkflowHistoryEntry history(String eventType, String operatorId, String reason, Map<String, Object> metadata) {
        return new AgentRemediationWorkflowHistoryEntry(
                "agent-remediation-history-" + UUID.randomUUID(),
                eventType,
                firstNonBlank(operatorId, "system"),
                firstNonBlank(reason, eventType),
                metadata == null ? Map.of() : metadata,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void persistWorkflowSecurityEvent(AgentRemediationWorkflowResponse workflow,
                                              AgentSecurityEventType eventType,
                                              String operatorId,
                                              String reason,
                                              Map<String, Object> extraMetadata) {
        AgentSecurityEvent event = new AgentSecurityEvent();
        event.setAgentId(workflow.agentId());
        event.setClaimedAgentId(workflow.agentId());
        event.setEventType(eventType);
        event.setReason(firstNonBlank(reason, eventType.name()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workflowId", workflow.workflowId());
        metadata.put("proposalId", workflow.proposalId());
        metadata.put("operatorId", firstNonBlank(operatorId, "system"));
        metadata.put("status", workflow.status());
        metadata.put("severity", workflow.severity());
        metadata.put("approvalRequired", workflow.approvalRequired());
        metadata.put("actionTypes", workflow.actions().stream().map(AgentRemediationActionView::actionType).toList());
        metadata.put("historyCount", workflow.history().size());
        if (extraMetadata != null) metadata.putAll(extraMetadata);
        event.setMetadata(metadata);
        agentGovernanceService.saveSecurityEvent(event);
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (pairs == null) return result;
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    private boolean contains(String value, String token) {
        return value != null && token != null && value.toUpperCase(Locale.ROOT).contains(token.toUpperCase(Locale.ROOT));
    }

    private String stringValue(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
