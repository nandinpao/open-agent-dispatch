package com.opensocket.aievent.core.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentProfileUpdateCommand;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;
import com.opensocket.aievent.core.agent.skill.AgentSkillRemediationAction;
import com.opensocket.aievent.core.agent.skill.AgentSkillRemediationProposal;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkillSyncCommand;
import com.opensocket.aievent.core.agent.skill.AgentApprovedSkillSyncResult;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.observability.AgentRemediationWorkflowMetricsService;
import com.opensocket.aievent.core.runtime.CoreRuntimeDisconnectClient;
import com.opensocket.aievent.core.runtime.RuntimeDisconnectException;
import com.opensocket.aievent.core.runtime.RuntimeDisconnectResult;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowStore;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowActionExecutionRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowHistoryRecord;
import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowRecord;

/**
 * P5/P6/P7/P8/P9/P10 operator-facing Agent remediation workflow.
 *
 * <p>P4 made routing decisions explainable. P5 turns the same signals into auditable
 * remediation proposals, P6 adds approval guardrails, and P7 persists workflow
 * state through MyBatis/PostgreSQL so multi-instance Core deployments, restarts,
 * and rolling updates do not lose approval or execution history. P8 integrates approved workflow execution with guarded Core governance handlers. P9 adds action-level idempotency rows so partial success can be safely retried without repeating completed governance actions. P10 adds workflow-level execution leases so only one Core instance/operator can run the workflow at a time.</p>
 */
@RestController
@RequestMapping("/admin/agents")
public class AgentRemediationController {
    private static final TypeReference<List<AgentRemediationActionView>> ACTION_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AgentGovernanceService agentGovernanceService;
    private final AgentDirectoryService agentDirectoryService;
    private final AgentSkillRegistryService skillRegistryService;
    private final RoutingProperties routingProperties;
    private final AgentRemediationWorkflowStore remediationWorkflowDao;
    private final CoreRuntimeDisconnectClient runtimeDisconnectClient;
    private final AgentRemediationWorkflowMetricsService remediationWorkflowMetrics;
    private final ObjectMapper objectMapper;

    public AgentRemediationController(AgentGovernanceService agentGovernanceService,
                                      AgentDirectoryService agentDirectoryService,
                                      AgentSkillRegistryService skillRegistryService,
                                      RoutingProperties routingProperties,
                                      AgentRemediationWorkflowStore remediationWorkflowDao,
                                      CoreRuntimeDisconnectClient runtimeDisconnectClient,
                                      AgentRemediationWorkflowMetricsService remediationWorkflowMetrics,
                                      ObjectMapper objectMapper) {
        this.agentGovernanceService = agentGovernanceService;
        this.agentDirectoryService = agentDirectoryService;
        this.skillRegistryService = skillRegistryService;
        this.routingProperties = routingProperties;
        this.remediationWorkflowDao = remediationWorkflowDao;
        this.runtimeDisconnectClient = runtimeDisconnectClient;
        this.remediationWorkflowMetrics = remediationWorkflowMetrics;
        this.objectMapper = objectMapper;
    }



    @GetMapping("/{agentId}/remediation/workflows")
    public List<AgentRemediationWorkflowResponse> listAgentRemediationWorkflows(@PathVariable String agentId) {
        return remediationWorkflowDao.findWorkflowsByAgentId(agentId, 50).stream()
                .map(this::hydrateWorkflow)
                .toList();
    }

    @GetMapping("/{agentId}/remediation/workflows/{workflowId}")
    public AgentRemediationWorkflowResponse getAgentRemediationWorkflow(@PathVariable String agentId,
                                                                        @PathVariable String workflowId) {
        AgentRemediationWorkflowResponse workflow = requireWorkflow(agentId, workflowId);
        return workflow;
    }

    @PostMapping("/{agentId}/remediation/workflows")
    @Transactional
    public AgentRemediationWorkflowResponse createAgentRemediationWorkflow(@PathVariable String agentId,
                                                                           @RequestBody(required = false) AgentRemediationWorkflowCreateRequest request) {
        AgentRemediationWorkflowCreateRequest body = request == null
                ? new AgentRemediationWorkflowCreateRequest(null, List.of(), "admin-ui", "Agent remediation workflow created.", false)
                : request;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AgentRemediationProposalResponse proposal = buildProposal(agentId, new AgentRemediationProposalRequest(
                body.operatorId(), body.reason(), null, null, false));
        List<String> selectedActionIds = normalizeActionIds(body.actionIds());
        List<AgentRemediationActionView> selectedActions = proposal.actions().stream()
                .filter(action -> selectedActionIds.isEmpty() || selectedActionIds.contains(action.actionId()) || selectedActionIds.contains(action.actionType()))
                .toList();
        if (selectedActions.isEmpty()) {
            throw new IllegalArgumentException("At least one valid remediation action is required to create a workflow.");
        }
        boolean highRisk = selectedActions.stream().anyMatch(this::requiresApproval);
        boolean riskAcknowledged = Boolean.TRUE.equals(body.riskAcknowledged());
        boolean requiresApproval = highRisk || !riskAcknowledged;
        String status = requiresApproval ? "PENDING_APPROVAL" : "APPROVED";
        List<AgentRemediationWorkflowHistoryEntry> history = new ArrayList<>();
        history.add(history("CREATED", body.operatorId(), body.reason(), map(
                "requiresApproval", requiresApproval,
                "riskAcknowledged", riskAcknowledged,
                "selectedActionIds", selectedActions.stream().map(AgentRemediationActionView::actionId).toList())));
        if (!requiresApproval) {
            history.add(history("AUTO_APPROVED", body.operatorId(), "Low/moderate-risk workflow auto-approved after risk acknowledgement.", Map.of()));
        }
        String workflowId = "agent-remediation-workflow-" + UUID.randomUUID();
        AgentRemediationWorkflowResponse workflow = new AgentRemediationWorkflowResponse(
                workflowId,
                proposal.proposalId(),
                agentId,
                status,
                highRisk ? "HIGH" : proposal.severity(),
                requiresApproval,
                selectedActions,
                rollbackSuggestions(selectedActions),
                history,
                List.of(),
                firstNonBlank(body.operatorId(), "admin-ui"),
                null,
                now,
                now,
                map("proposalSummary", proposal.summary(), "context", proposal.context()),
                null,
                null,
                null,
                0,
                false);
        insertWorkflow(workflow);
        ensureActionExecutionRows(workflow);
        AgentRemediationWorkflowResponse persistedWorkflow = requireWorkflow(agentId, workflowId);
        remediationWorkflowMetrics.recordWorkflowCreated(persistedWorkflow.status(), persistedWorkflow.severity(),
                persistedWorkflow.approvalRequired(), selectedActions.size());
        persistWorkflowSecurityEvent(persistedWorkflow, AgentSecurityEventType.AGENT_REMEDIATION_WORKFLOW_CREATED, body.operatorId(), body.reason(), Map.of("status", status));
        return persistedWorkflow;
    }

    @PostMapping("/{agentId}/remediation/workflows/{workflowId}/approve")
    @Transactional
    public AgentRemediationWorkflowResponse approveAgentRemediationWorkflow(@PathVariable String agentId,
                                                                            @PathVariable String workflowId,
                                                                            @RequestBody(required = false) AgentRemediationWorkflowDecisionRequest request) {
        AgentRemediationWorkflowDecisionRequest body = request == null
                ? new AgentRemediationWorkflowDecisionRequest("admin-ui", "Agent remediation workflow approved.", false)
                : request;
        AgentRemediationWorkflowResponse current = requireWorkflow(agentId, workflowId);
        if (!"PENDING_APPROVAL".equals(current.status())) {
            throw new IllegalStateException("Only PENDING_APPROVAL remediation workflows can be approved.");
        }
        AgentRemediationWorkflowResponse updated = updateWorkflow(current, "APPROVED", firstNonBlank(body.operatorId(), "admin-ui"),
                history("APPROVED", body.operatorId(), body.reason(), Map.of("approvalRequired", current.approvalRequired())));
        remediationWorkflowMetrics.recordWorkflowDecision("APPROVED", current.status(), updated.status(), updated.severity());
        remediationWorkflowMetrics.recordApprovalLatency(updated.severity(), current.createdAt(), OffsetDateTime.now(ZoneOffset.UTC));
        persistWorkflowSecurityEvent(updated, AgentSecurityEventType.AGENT_REMEDIATION_WORKFLOW_APPROVED, body.operatorId(), body.reason(), Map.of());
        return updated;
    }

    @PostMapping("/{agentId}/remediation/workflows/{workflowId}/reject")
    @Transactional
    public AgentRemediationWorkflowResponse rejectAgentRemediationWorkflow(@PathVariable String agentId,
                                                                           @PathVariable String workflowId,
                                                                           @RequestBody(required = false) AgentRemediationWorkflowDecisionRequest request) {
        AgentRemediationWorkflowDecisionRequest body = request == null
                ? new AgentRemediationWorkflowDecisionRequest("admin-ui", "Agent remediation workflow rejected.", false)
                : request;
        AgentRemediationWorkflowResponse current = requireWorkflow(agentId, workflowId);
        if (!"PENDING_APPROVAL".equals(current.status())) {
            throw new IllegalStateException("Only PENDING_APPROVAL remediation workflows can be rejected.");
        }
        AgentRemediationWorkflowResponse updated = updateWorkflow(current, "REJECTED", firstNonBlank(body.operatorId(), "admin-ui"),
                history("REJECTED", body.operatorId(), body.reason(), Map.of()));
        remediationWorkflowMetrics.recordWorkflowDecision("REJECTED", current.status(), updated.status(), updated.severity());
        remediationWorkflowMetrics.recordApprovalLatency(updated.severity(), current.createdAt(), OffsetDateTime.now(ZoneOffset.UTC));
        persistWorkflowSecurityEvent(updated, AgentSecurityEventType.AGENT_REMEDIATION_WORKFLOW_REJECTED, body.operatorId(), body.reason(), Map.of());
        return updated;
    }

    @PostMapping("/{agentId}/remediation/workflows/{workflowId}/cancel")
    @Transactional
    public AgentRemediationWorkflowResponse cancelAgentRemediationWorkflow(@PathVariable String agentId,
                                                                           @PathVariable String workflowId,
                                                                           @RequestBody(required = false) AgentRemediationWorkflowDecisionRequest request) {
        AgentRemediationWorkflowDecisionRequest body = request == null
                ? new AgentRemediationWorkflowDecisionRequest("admin-ui", "Agent remediation workflow cancelled.", false)
                : request;
        AgentRemediationWorkflowResponse current = requireWorkflow(agentId, workflowId);
        if (AgentRemediationWorkflowExecutionPolicy.isTerminalWorkflowStatus(current.status())) {
            throw new IllegalStateException("Terminal remediation workflows cannot be cancelled.");
        }
        AgentRemediationWorkflowResponse updated = updateWorkflow(current, "CANCELLED", firstNonBlank(body.operatorId(), "admin-ui"),
                history("CANCELLED", body.operatorId(), body.reason(), Map.of()));
        remediationWorkflowMetrics.recordWorkflowDecision("CANCELLED", current.status(), updated.status(), updated.severity());
        persistWorkflowSecurityEvent(updated, AgentSecurityEventType.AGENT_REMEDIATION_WORKFLOW_CANCELLED, body.operatorId(), body.reason(), Map.of());
        return updated;
    }

    @PostMapping("/{agentId}/remediation/workflows/{workflowId}/execute")
    public AgentRemediationWorkflowResponse executeAgentRemediationWorkflow(@PathVariable String agentId,
                                                                            @PathVariable String workflowId,
                                                                            @RequestBody(required = false) AgentRemediationWorkflowDecisionRequest request) {
        AgentRemediationWorkflowDecisionRequest body = request == null
                ? new AgentRemediationWorkflowDecisionRequest("admin-ui", "Agent remediation workflow execution requested.", false)
                : request;
        AgentRemediationWorkflowResponse current = requireWorkflow(agentId, workflowId);
        ensureActionExecutionRows(current);
        current = requireWorkflow(agentId, workflowId);
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
                    firstNonBlank(body.operatorId(), "admin-ui"),
                    leaseAcquiredAt,
                    leaseExpiresAt);
            if (acquired != 1) {
                AgentRemediationWorkflowResponse latest = requireWorkflow(agentId, workflowId);
                Map<String, Object> busyMetadata = map(
                        "executionMode", AgentRemediationWorkflowExecutionPolicy.EXECUTION_MODE_LEASE_BUSY,
                        "leaseOwner", latest.executionLeaseOwner(),
                        "leaseExpiresAt", latest.executionLeaseExpiresAt(),
                        "leaseActive", latest.executionLeaseActive(),
                        "leaseRemainingSeconds", latest.executionLeaseRemainingSeconds());
                remediationWorkflowDao.insertHistory(toHistoryPo(current.workflowId(), current.agentId(),
                        history("EXECUTION_LEASE_BUSY", body.operatorId(), body.reason(), busyMetadata)));
                remediationWorkflowMetrics.recordWorkflowLeaseEvent("EXECUTION_LEASE_BUSY");
                throw new IllegalStateException("Remediation workflow is already executing or its execution lease has not expired.");
            }
            leaseAcquired = true;
            remediationWorkflowDao.insertHistory(toHistoryPo(current.workflowId(), current.agentId(),
                    history("EXECUTION_LEASE_ACQUIRED", body.operatorId(), body.reason(), map(
                            "leaseOwner", leaseOwner,
                            "leaseAcquiredAt", leaseAcquiredAt,
                            "leaseExpiresAt", leaseExpiresAt,
                            "leaseTtlSeconds", AgentRemediationWorkflowExecutionPolicy.WORKFLOW_EXECUTION_LEASE_DURATION.toSeconds()))));
            remediationWorkflowMetrics.recordWorkflowLeaseEvent("EXECUTION_LEASE_ACQUIRED");
            current = requireWorkflow(agentId, workflowId);
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
                remediationWorkflowDao.insertHistory(toHistoryPo(current.workflowId(), current.agentId(),
                        history("EXECUTION_FAILED", body.operatorId(), body.reason(), executionMetadata)));
                AgentRemediationWorkflowResponse failedAttempt = requireWorkflow(current.agentId(), current.workflowId());
                persistWorkflowSecurityEvent(failedAttempt, AgentSecurityEventType.AGENT_REMEDIATION_WORKFLOW_EXECUTION_FAILED, body.operatorId(), body.reason(), executionMetadata);
                return failedAttempt;
            }

            AgentRemediationWorkflowResponse updated = updateWorkflow(current, "EXECUTED", firstNonBlank(body.operatorId(), "admin-ui"),
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

    @GetMapping("/{agentId}/remediation/proposal")
    public AgentRemediationProposalResponse previewAgentRemediation(@PathVariable String agentId) {
        return buildProposal(agentId, new AgentRemediationProposalRequest(null, null, null, null, false));
    }

    @PostMapping("/{agentId}/remediation/proposal")
    public AgentRemediationProposalResponse createAgentRemediationProposal(@PathVariable String agentId,
                                                                           @RequestBody(required = false) AgentRemediationProposalRequest request) {
        AgentRemediationProposalRequest body = request == null
                ? new AgentRemediationProposalRequest(null, null, null, null, true)
                : request;
        AgentRemediationProposalResponse proposal = buildProposal(agentId, body);
        if (body.persistEvent() == null || body.persistEvent()) {
            persistProposalSecurityEvent(proposal, body);
        }
        return proposal;
    }

    private AgentRemediationProposalResponse buildProposal(String agentId, AgentRemediationProposalRequest request) {
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        AgentProfile profile = findProfile(agentId);
        AgentSnapshot runtime = agentDirectoryService.findById(agentId).orElse(null);
        List<AgentRuntimeCapabilityItem> runtimeItems = agentDirectoryService.findRuntimeCapabilityItems(agentId);
        AgentSkillRemediationProposal skillProposal = profile == null
                ? emptySkillProposal(agentId, generatedAt)
                : skillRegistryService.proposeAgentRemediation(profile, runtimeItems);

        List<AgentRemediationActionView> actions = new ArrayList<>();
        addRuntimeActions(agentId, runtime, actions);
        addGovernanceActions(agentId, profile, runtime, actions);
        addSkillActions(agentId, skillProposal, actions);

        int executable = (int) actions.stream().filter(AgentRemediationActionView::executable).count();
        int reviewOnly = Math.max(0, actions.size() - executable);
        String severity = summarizeSeverity(actions, runtime);
        List<String> summary = new ArrayList<>();
        summary.add(actions.size() + " remediation action(s) proposed; " + executable + " executable, " + reviewOnly + " review-only.");
        if (runtime != null && runtime.getRuntimeFailureCount() >= poisonThreshold()) {
            summary.add("Agent runtime failure count reached poison threshold; routing will exclude this Agent until failure count is cleared or governance state changes.");
        }
        if (skillProposal.getSourceDriftCount() > 0) {
            summary.add(skillProposal.getSourceDriftCount() + " skill drift item(s) detected; use skill sync only after runtime capabilityProfile is trusted.");
        }
        if (actions.isEmpty()) {
            summary.add("No immediate remediation action is required from the available Core signals.");
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("profilePresent", profile != null);
        context.put("approvalStatus", profile == null || profile.getApprovalStatus() == null ? null : profile.getApprovalStatus().name());
        context.put("enabled", profile == null ? null : profile.isEnabled());
        context.put("riskStatus", profile == null || profile.getRiskStatus() == null ? null : profile.getRiskStatus().name());
        context.put("runtimePresent", runtime != null);
        context.put("runtimeStatus", runtime == null || runtime.getStatus() == null ? null : runtime.getStatus().name());
        context.put("runtimeBackoffActive", runtime != null && runtime.isRuntimeBackoffActive());
        context.put("runtimeBackoffUntil", runtime == null ? null : runtime.getRuntimeBackoffUntil());
        context.put("runtimeFailureCount", runtime == null ? null : runtime.getRuntimeFailureCount());
        context.put("poisonAgentFailureThreshold", poisonThreshold());
        context.put("ownerGatewayNodeId", runtime == null ? null : runtime.getOwnerGatewayNodeId());
        context.put("sourceTaskId", request == null ? null : request.sourceTaskId());
        context.put("sourceRoutingDecisionId", request == null ? null : request.sourceRoutingDecisionId());

        return new AgentRemediationProposalResponse(
                "agent-remediation-" + UUID.randomUUID(),
                agentId,
                severity,
                executable,
                reviewOnly,
                actions,
                skillProposal,
                context,
                summary,
                generatedAt);
    }

    private void addRuntimeActions(String agentId, AgentSnapshot runtime, List<AgentRemediationActionView> actions) {
        if (runtime == null) {
            actions.add(action("runtime-not-observed", agentId, "REQUEST_AGENT_RECONNECT", "MEDIUM", false,
                    "Core has no runtime snapshot for this Agent. Ask the Agent owner to reconnect and verify heartbeat/capabilityProfile.",
                    List.of("Confirm Netty gateway is reachable", "Confirm Agent credential is valid"),
                    Map.of("ui", "/agents/runtime", "category", "runtime"), Map.of()));
            return;
        }
        if (runtime.isRuntimeBackoffActive() || runtime.getRuntimeFailureCount() > 0) {
            actions.add(action("clear-runtime-backoff", agentId, "CLEAR_RUNTIME_BACKOFF", "MEDIUM", true,
                    "Clear runtime backoff only after the Gateway session and Agent writer path are healthy.",
                    List.of("Inspect delivery failure history", "Confirm Agent heartbeat and capacity", "Enter recovery confirmation phrase"),
                    Map.of("api", "/admin/recovery/actions/agents/{agentId}/clear-runtime-backoff", "risk", "MODERATE", "confirmationPhrase", "CONFIRM_RECOVERY_ACTION"),
                    map("runtimeFailureCount", runtime.getRuntimeFailureCount(), "runtimeBackoffUntil", runtime.getRuntimeBackoffUntil())));
        }
        if (runtime.getStatus() != AgentStatus.OFFLINE && runtime.getStatus() != AgentStatus.EXPIRED) {
            actions.add(action("disconnect-all-runtime-sessions", agentId, "DISCONNECT_ALL_RUNTIME_SESSIONS", "MEDIUM", true,
                    "Disconnect all runtime sessions when the Agent keeps failing, reports stale capabilityProfile, or needs a clean reconnect.",
                    List.of("If the Agent remains approved/enabled it may reconnect", "Use suspend if reconnect must be blocked"),
                    Map.of("api", "/admin/agents/{agentId}/disconnect-all", "gatewayNodeId", nullToEmpty(runtime.getOwnerGatewayNodeId())),
                    map("runtimeStatus", runtime.getStatus() == null ? null : runtime.getStatus().name(), "ownerGatewayNodeId", runtime.getOwnerGatewayNodeId())));
        }
    }

    private void addGovernanceActions(String agentId, AgentProfile profile, AgentSnapshot runtime, List<AgentRemediationActionView> actions) {
        if (profile == null) {
            actions.add(action("create-or-review-enrollment", agentId, "REVIEW_AGENT_ENROLLMENT", "HIGH", false,
                    "Runtime exists without a Core Agent profile. Review enrollment before allowing dispatch.",
                    List.of("Open Agent Governance", "Create or approve enrollment with explicit capabilities/scopes"),
                    Map.of("ui", "/agent-enrollments"), Map.of()));
            return;
        }
        boolean poison = runtime != null && runtime.getRuntimeFailureCount() >= poisonThreshold();
        if (poison || profile.isEnabled()) {
            actions.add(action("suspend-agent", agentId, "SUSPEND_AGENT", poison ? "HIGH" : "MEDIUM", true,
                    poison
                            ? "Runtime failure count reached poison threshold. Suspend to block reconnect/dispatch until remediation is complete."
                            : "Suspend Agent when remediation requires a controlled outage or credential/capability redeploy.",
                    List.of("Notify Agent owner", "Disconnect runtime sessions after suspension", "Document business impact"),
                    Map.of("api", "/admin/agents/{agentId}/suspend"),
                    map("poisonThreshold", poisonThreshold(), "runtimeFailureCount", runtime == null ? null : runtime.getRuntimeFailureCount())));
        }
    }

    private void addSkillActions(String agentId, AgentSkillRemediationProposal skillProposal, List<AgentRemediationActionView> actions) {
        List<AgentSkillRemediationAction> skillActions = skillProposal == null || skillProposal.getActions() == null
                ? List.of()
                : skillProposal.getActions();
        List<String> syncSkillCodes = skillActions.stream()
                .filter(AgentSkillRemediationAction::isExecutable)
                .filter(action -> contains(commandHintText(action, "api"), "sync-approved-capabilities")
                        || contains(action.getActionType(), "APPROVE")
                        || contains(action.getActionType(), "MIGRATE")
                        || contains(action.getActionType(), "DEPENDENCY"))
                .flatMap(action -> skillCodesFor(action).stream())
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (!syncSkillCodes.isEmpty()) {
            actions.add(action("sync-approved-skills", agentId, "SYNC_APPROVED_SKILLS_AND_CAPABILITIES", "MEDIUM", true,
                    "Synchronize approved skills and governance capabilities according to the remediation proposal.",
                    List.of("Review runtime capabilityProfile", "Confirm skill taxonomy and dependency graph", "Avoid blindly approving unknown runtime signals"),
                    Map.of("api", "/admin/agents/{agentId}/skills/sync-approved-capabilities", "skillCodes", syncSkillCodes, "syncProfileCapabilities", true),
                    map("skillCodes", syncSkillCodes)));
        }
        boolean needsRegistryWork = skillActions.stream().anyMatch(action -> contains(action.getActionType(), "CREATE_SKILL")
                || contains(action.getActionType(), "UPDATE_RUNTIME_PROFILE")
                || contains(action.getActionType(), "PUBLISH")
                || contains(action.getActionType(), "DEPRECATED")
                || contains(action.getActionType(), "DISABLED"));
        if (needsRegistryWork) {
            actions.add(action("skill-registry-review", agentId, "REVIEW_OR_PUBLISH_SKILL_VERSION", "MEDIUM", false,
                    "One or more skill drift actions require taxonomy or version work before dispatch can be trusted.",
                    List.of("Open Skill Registry", "Create/approve/publish replacement version if needed", "Ask Agent owner to update OpenClaw capabilityProfile"),
                    Map.of("ui", "/skills", "apiPattern", "/admin/agent-skills/{skillCode}/versions/{version}/publish"),
                    map("driftCount", skillProposal == null ? 0 : skillProposal.getSourceDriftCount())));
        }
    }

    private String commandHintText(AgentSkillRemediationAction action, String key) {
        if (action == null || action.getCommandHint() == null || key == null) return null;
        Object value = action.getCommandHint().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private List<String> skillCodesFor(AgentSkillRemediationAction action) {
        List<String> values = new ArrayList<>();
        if (action == null) return values;
        if (action.getSkillCode() != null && !action.getSkillCode().isBlank()) values.add(action.getSkillCode());
        if (action.getTargetSkillCode() != null && !action.getTargetSkillCode().isBlank()) values.add(action.getTargetSkillCode());
        Object commandSkillCodes = action.getCommandHint() == null ? null : action.getCommandHint().get("skillCodes");
        if (commandSkillCodes instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !String.valueOf(item).isBlank()) values.add(String.valueOf(item));
            }
        }
        return values;
    }


    private String executionLeaseOwner(AgentRemediationWorkflowDecisionRequest request) {
        return String.join(":",
                "core-remediation-executor",
                firstNonBlank(System.getenv("HOSTNAME"), System.getenv("COMPUTERNAME"), "local-core"),
                firstNonBlank(request == null ? null : request.operatorId(), "admin-ui"),
                UUID.randomUUID().toString());
    }

    private void releaseWorkflowExecutionLease(String workflowId,
                                               String agentId,
                                               String leaseOwner,
                                               AgentRemediationWorkflowDecisionRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int released = remediationWorkflowDao.releaseWorkflowExecutionLease(workflowId, leaseOwner, now);
        remediationWorkflowMetrics.recordWorkflowLeaseEvent(released == 1 ? "EXECUTION_LEASE_RELEASED" : "EXECUTION_LEASE_RELEASE_RACE");
        remediationWorkflowDao.insertHistory(toHistoryPo(workflowId, agentId,
                history(released == 1 ? "EXECUTION_LEASE_RELEASED" : "EXECUTION_LEASE_RELEASE_RACE",
                        request == null ? null : request.operatorId(),
                        request == null ? null : request.reason(),
                        map("leaseOwner", leaseOwner, "released", released == 1, "releasedAt", now))));
    }

    private boolean executionLeaseActive(AgentRemediationWorkflowRecord po) {
        return AgentRemediationWorkflowExecutionPolicy.executionLeaseActive(po);
    }

    private Integer executionLeaseRemainingSeconds(AgentRemediationWorkflowRecord po) {
        return AgentRemediationWorkflowExecutionPolicy.executionLeaseRemainingSeconds(po);
    }

    private List<AgentRemediationWorkflowActionExecutionResult> executeWorkflowActions(AgentRemediationWorkflowResponse workflow,
                                                                                       AgentRemediationWorkflowDecisionRequest request,
                                                                                       boolean dryRun) {
        ensureActionExecutionRows(workflow);
        Map<String, AgentRemediationWorkflowActionExecutionRecord> executionsByActionId = actionExecutionsByActionId(workflow.workflowId());
        List<AgentRemediationWorkflowActionExecutionResult> results = new ArrayList<>();
        for (AgentRemediationActionView action : workflow.actions() == null ? List.<AgentRemediationActionView>of() : workflow.actions()) {
            if (action == null) {
                continue;
            }
            AgentRemediationWorkflowActionExecutionRecord execution = executionsByActionId.get(firstNonBlank(action.actionId(), action.actionType(), "UNKNOWN_ACTION"));
            if (execution == null) {
                ensureActionExecutionRows(workflow);
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
                writeJson(result.details()),
                lastError,
                OffsetDateTime.now(ZoneOffset.UTC));
        if (updated != 1) {
            remediationWorkflowDao.insertHistory(toHistoryPo(execution.getWorkflowId(), execution.getAgentId(),
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
        return firstNonBlank(request == null ? null : request.operatorId(), "admin-ui");
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




    private AgentRemediationWorkflowResponse requireWorkflow(String agentId, String workflowId) {
        AgentRemediationWorkflowRecord workflow = remediationWorkflowDao.findWorkflowById(workflowId);
        if (workflow == null || !agentId.equals(workflow.getAgentId())) {
            throw new IllegalArgumentException("Remediation workflow not found: " + workflowId);
        }
        return hydrateWorkflow(workflow);
    }

    private void insertWorkflow(AgentRemediationWorkflowResponse workflow) {
        remediationWorkflowDao.insertWorkflow(toWorkflowPo(workflow));
        for (AgentRemediationWorkflowHistoryEntry entry : workflow.history()) {
            remediationWorkflowDao.insertHistory(toHistoryPo(workflow.workflowId(), workflow.agentId(), entry));
        }
    }


    private void ensureActionExecutionRows(AgentRemediationWorkflowResponse workflow) {
        if (workflow == null || workflow.actions() == null) return;
        for (AgentRemediationActionView action : workflow.actions()) {
            if (action == null) continue;
            remediationWorkflowDao.insertActionExecutionIfAbsent(toActionExecutionPo(workflow, action));
        }
    }

    private AgentRemediationWorkflowActionExecutionRecord toActionExecutionPo(AgentRemediationWorkflowResponse workflow,
                                                                         AgentRemediationActionView action) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String actionId = firstNonBlank(action.actionId(), action.actionType(), "UNKNOWN_ACTION");
        String idempotencyKey = remediationActionIdempotencyKey(workflow.workflowId(), workflow.agentId(), actionId, action.actionType(), action.commandHint());
        AgentRemediationWorkflowActionExecutionRecord po = new AgentRemediationWorkflowActionExecutionRecord();
        po.setActionExecutionId("agent-remediation-action-" + idempotencyKey.substring(Math.max(0, idempotencyKey.length() - 32)));
        po.setWorkflowId(workflow.workflowId());
        po.setAgentId(workflow.agentId());
        po.setActionId(actionId);
        po.setActionType(firstNonBlank(action.actionType(), "UNKNOWN"));
        po.setIdempotencyKey(idempotencyKey);
        po.setStatus(action.executable() ? "PENDING" : "SKIPPED");
        po.setAttemptCount(0);
        po.setLastResultJson(writeJson(Map.of("createdBy", "P9_ACTION_IDEMPOTENCY", "executable", action.executable())));
        po.setCompletedAt(action.executable() ? null : now);
        po.setCreatedAt(now);
        po.setUpdatedAt(now);
        return po;
    }

    private AgentRemediationWorkflowActionExecutionResponse toActionExecutionResponse(AgentRemediationWorkflowActionExecutionRecord po) {
        return new AgentRemediationWorkflowActionExecutionResponse(
                po.getActionExecutionId(),
                po.getWorkflowId(),
                po.getAgentId(),
                po.getActionId(),
                po.getActionType(),
                po.getIdempotencyKey(),
                po.getStatus(),
                po.getAttemptCount(),
                po.getLastOperatorId(),
                po.getLastReason(),
                readJson(po.getLastResultJson(), MAP_TYPE, Map.of()),
                po.getLastError(),
                po.getFirstAttemptAt(),
                po.getLastAttemptAt(),
                po.getCompletedAt(),
                po.getCreatedAt(),
                po.getUpdatedAt());
    }

    private String remediationActionIdempotencyKey(String workflowId,
                                                   String agentId,
                                                   String actionId,
                                                   String actionType,
                                                   Map<String, Object> commandHint) {
        String raw = String.join("|",
                safe(workflowId),
                safe(agentId),
                safe(actionId),
                safe(actionType),
                safe(writeJson(commandHint == null ? Map.of() : commandHint)));
        return "remediation-action:v1:" + sha256(raw);
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(safe(raw).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private AgentRemediationWorkflowResponse updateWorkflow(AgentRemediationWorkflowResponse current,
                                                            String status,
                                                            String operatorId,
                                                            AgentRemediationWorkflowHistoryEntry entry) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updatedRows = remediationWorkflowDao.updateWorkflowStatusIfCurrent(
                current.workflowId(), current.status(), status, operatorId, now);
        if (updatedRows != 1) {
            throw new IllegalStateException("Remediation workflow transition was rejected because another Core instance changed the workflow state.");
        }
        remediationWorkflowDao.insertHistory(toHistoryPo(current.workflowId(), current.agentId(), entry));
        return requireWorkflow(current.agentId(), current.workflowId());
    }

    private AgentRemediationWorkflowResponse hydrateWorkflow(AgentRemediationWorkflowRecord po) {
        List<AgentRemediationActionView> actions = readJson(po.getActionsJson(), ACTION_LIST_TYPE, List.of());
        List<String> rollbackSuggestions = readJson(po.getRollbackSuggestionsJson(), STRING_LIST_TYPE, List.of());
        Map<String, Object> metadata = readJson(po.getMetadataJson(), MAP_TYPE, Map.of());
        List<AgentRemediationWorkflowHistoryEntry> history = remediationWorkflowDao.findHistoryByWorkflowId(po.getWorkflowId()).stream()
                .map(this::toHistoryEntry)
                .toList();
        return new AgentRemediationWorkflowResponse(
                po.getWorkflowId(),
                po.getProposalId(),
                po.getAgentId(),
                po.getStatus(),
                po.getSeverity(),
                Boolean.TRUE.equals(po.getApprovalRequired()),
                actions,
                rollbackSuggestions,
                history,
                remediationWorkflowDao.findActionExecutionsByWorkflowId(po.getWorkflowId()).stream()
                        .map(this::toActionExecutionResponse)
                        .toList(),
                po.getCreatedBy(),
                po.getLastOperatorId(),
                po.getCreatedAt(),
                po.getUpdatedAt(),
                metadata,
                po.getExecutionLeaseOwner(),
                po.getExecutionLeaseAcquiredAt(),
                po.getExecutionLeaseExpiresAt(),
                executionLeaseRemainingSeconds(po),
                executionLeaseActive(po));
    }

    private AgentRemediationWorkflowRecord toWorkflowPo(AgentRemediationWorkflowResponse workflow) {
        AgentRemediationWorkflowRecord po = new AgentRemediationWorkflowRecord();
        po.setWorkflowId(workflow.workflowId());
        po.setProposalId(workflow.proposalId());
        po.setAgentId(workflow.agentId());
        po.setStatus(workflow.status());
        po.setSeverity(workflow.severity());
        po.setApprovalRequired(workflow.approvalRequired());
        po.setCreatedBy(workflow.createdBy());
        po.setLastOperatorId(workflow.lastOperatorId());
        po.setRollbackSuggestionsJson(writeJson(workflow.rollbackSuggestions()));
        po.setActionsJson(writeJson(workflow.actions()));
        po.setMetadataJson(writeJson(workflow.metadata()));
        po.setVersion(0L);
        po.setCreatedAt(workflow.createdAt());
        po.setUpdatedAt(workflow.updatedAt());
        return po;
    }

    private AgentRemediationWorkflowHistoryRecord toHistoryPo(String workflowId, String agentId, AgentRemediationWorkflowHistoryEntry entry) {
        AgentRemediationWorkflowHistoryRecord po = new AgentRemediationWorkflowHistoryRecord();
        po.setHistoryId(entry.historyId());
        po.setWorkflowId(workflowId);
        po.setAgentId(agentId);
        po.setEventType(entry.eventType());
        po.setOperatorId(entry.operatorId());
        po.setReason(entry.reason());
        po.setMetadataJson(writeJson(entry.metadata()));
        po.setOccurredAt(entry.occurredAt());
        return po;
    }

    private AgentRemediationWorkflowHistoryEntry toHistoryEntry(AgentRemediationWorkflowHistoryRecord po) {
        return new AgentRemediationWorkflowHistoryEntry(
                po.getHistoryId(),
                po.getEventType(),
                po.getOperatorId(),
                po.getReason(),
                readJson(po.getMetadataJson(), MAP_TYPE, Map.of()),
                po.getOccurredAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize remediation workflow JSON payload.", ex);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to deserialize remediation workflow JSON payload.", ex);
        }
    }

    private List<String> normalizeActionIds(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean requiresApproval(AgentRemediationActionView action) {
        if (action == null) return true;
        if ("HIGH".equalsIgnoreCase(action.severity())) return true;
        String type = action.actionType();
        return contains(type, "SUSPEND")
                || contains(type, "REVOKE")
                || contains(type, "PUBLISH")
                || contains(type, "ENROLLMENT")
                || contains(type, "DISCONNECT_ALL");
    }

    private boolean isTerminalWorkflowStatus(String status) {
        return AgentRemediationWorkflowExecutionPolicy.isTerminalWorkflowStatus(status);
    }

    private AgentRemediationWorkflowHistoryEntry history(String eventType, String operatorId, String reason, Map<String, Object> metadata) {
        return new AgentRemediationWorkflowHistoryEntry(
                "agent-remediation-history-" + UUID.randomUUID(),
                eventType,
                firstNonBlank(operatorId, "admin-ui"),
                firstNonBlank(reason, eventType),
                metadata == null ? Map.of() : metadata,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private List<String> rollbackSuggestions(List<AgentRemediationActionView> actions) {
        List<String> suggestions = new ArrayList<>();
        for (AgentRemediationActionView action : actions == null ? List.<AgentRemediationActionView>of() : actions) {
            String type = action.actionType();
            if (contains(type, "CLEAR_RUNTIME_BACKOFF")) {
                suggestions.add("If failures recur after clearing backoff, restore protection by suspending the Agent or letting runtime backoff re-enter naturally.");
            } else if (contains(type, "DISCONNECT_ALL")) {
                suggestions.add("If disconnect causes service degradation, allow the approved/enabled Agent to reconnect after validating credentials and heartbeat.");
            } else if (contains(type, "SUSPEND_AGENT")) {
                suggestions.add("Rollback suspend by explicitly re-enabling/approving the Agent only after credential, capability, and runtime health checks pass.");
            } else if (contains(type, "SYNC_APPROVED_SKILLS")) {
                suggestions.add("Rollback skill sync by disabling the newly approved skill mapping or publishing a corrected skill version.");
            } else if (contains(type, "SKILL_VERSION")) {
                suggestions.add("Rollback skill version work by using the Skill Registry rollback workflow and re-running dispatch contract evaluation.");
            }
        }
        if (suggestions.isEmpty()) {
            suggestions.add("No automatic rollback is available; document manual remediation and re-run routing diagnostics after execution.");
        }
        return suggestions.stream().distinct().toList();
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
        metadata.put("operatorId", firstNonBlank(operatorId, "admin-ui"));
        metadata.put("status", workflow.status());
        metadata.put("severity", workflow.severity());
        metadata.put("approvalRequired", workflow.approvalRequired());
        metadata.put("actionTypes", workflow.actions().stream().map(AgentRemediationActionView::actionType).toList());
        metadata.put("historyCount", workflow.history().size());
        if (extraMetadata != null) metadata.putAll(extraMetadata);
        event.setMetadata(metadata);
        agentGovernanceService.saveSecurityEvent(event);
    }

    private void persistProposalSecurityEvent(AgentRemediationProposalResponse proposal, AgentRemediationProposalRequest request) {
        AgentSecurityEvent event = new AgentSecurityEvent();
        event.setAgentId(proposal.agentId());
        event.setClaimedAgentId(proposal.agentId());
        event.setEventType(AgentSecurityEventType.AGENT_REMEDIATION_PROPOSAL_GENERATED);
        event.setReason(firstNonBlank(request == null ? null : request.reason(), "Agent remediation proposal generated."));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("proposalId", proposal.proposalId());
        metadata.put("operatorId", firstNonBlank(request == null ? null : request.operatorId(), "admin-ui"));
        metadata.put("severity", proposal.severity());
        metadata.put("actionCount", proposal.actions().size());
        metadata.put("executableActionCount", proposal.executableActionCount());
        metadata.put("reviewOnlyActionCount", proposal.reviewOnlyActionCount());
        metadata.put("sourceTaskId", request == null ? null : request.sourceTaskId());
        metadata.put("sourceRoutingDecisionId", request == null ? null : request.sourceRoutingDecisionId());
        metadata.put("context", proposal.context());
        metadata.put("summary", proposal.summary());
        event.setMetadata(metadata);
        agentGovernanceService.saveSecurityEvent(event);
    }

    private AgentProfile findProfile(String agentId) {
        try {
            return agentGovernanceService.getProfile(agentId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private AgentSkillRemediationProposal emptySkillProposal(String agentId, OffsetDateTime generatedAt) {
        AgentSkillRemediationProposal proposal = new AgentSkillRemediationProposal();
        proposal.setAgentId(agentId);
        proposal.setTaxonomyVersion(AgentSkillRegistryService.TAXONOMY_VERSION);
        proposal.setSourceDriftCount(0);
        proposal.setHighSeverityCount(0);
        proposal.setActions(List.of());
        proposal.setSummary(List.of("Core Agent profile is missing; skill drift cannot be evaluated."));
        proposal.setMetadata(Map.of("generatedFrom", "P5_AGENT_REMEDIATION", "profilePresent", false));
        proposal.setGeneratedAt(generatedAt);
        return proposal;
    }

    private AgentRemediationActionView action(String actionId,
                                              String agentId,
                                              String actionType,
                                              String severity,
                                              boolean executable,
                                              String reason,
                                              List<String> prerequisites,
                                              Map<String, Object> commandHint,
                                              Map<String, Object> metadata) {
        return new AgentRemediationActionView(actionId, agentId, actionType, severity, executable, reason,
                prerequisites == null ? List.of() : prerequisites,
                commandHint == null ? Map.of() : commandHint,
                metadata == null ? Map.of() : metadata);
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (pairs == null) return result;
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    private String summarizeSeverity(List<AgentRemediationActionView> actions, AgentSnapshot runtime) {
        if (runtime != null && runtime.getRuntimeFailureCount() >= poisonThreshold()) return "HIGH";
        if (actions.stream().anyMatch(action -> "HIGH".equalsIgnoreCase(action.severity()))) return "HIGH";
        if (actions.stream().anyMatch(action -> "MEDIUM".equalsIgnoreCase(action.severity()))) return "MEDIUM";
        return "LOW";
    }

    private int poisonThreshold() {
        return Math.max(1, routingProperties.getPoisonAgentFailureThreshold());
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }



    public record AgentRemediationWorkflowCreateRequest(
            String proposalId,
            List<String> actionIds,
            String operatorId,
            String reason,
            Boolean riskAcknowledged
    ) {}

    public record AgentRemediationWorkflowDecisionRequest(
            String operatorId,
            String reason,
            Boolean dryRun
    ) {}

    public record AgentRemediationWorkflowActionExecutionResponse(
            String actionExecutionId,
            String workflowId,
            String agentId,
            String actionId,
            String actionType,
            String idempotencyKey,
            String status,
            Integer attemptCount,
            String lastOperatorId,
            String lastReason,
            Map<String, Object> lastResult,
            String lastError,
            OffsetDateTime firstAttemptAt,
            OffsetDateTime lastAttemptAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record AgentRemediationWorkflowActionExecutionResult(
            String actionId,
            String actionType,
            boolean success,
            boolean skipped,
            String status,
            String message,
            Map<String, Object> details
    ) {}

    public record AgentRemediationWorkflowResponse(
            String workflowId,
            String proposalId,
            String agentId,
            String status,
            String severity,
            boolean approvalRequired,
            List<AgentRemediationActionView> actions,
            List<String> rollbackSuggestions,
            List<AgentRemediationWorkflowHistoryEntry> history,
            List<AgentRemediationWorkflowActionExecutionResponse> actionExecutions,
            String createdBy,
            String lastOperatorId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Map<String, Object> metadata,
            String executionLeaseOwner,
            OffsetDateTime executionLeaseAcquiredAt,
            OffsetDateTime executionLeaseExpiresAt,
            Integer executionLeaseRemainingSeconds,
            boolean executionLeaseActive
    ) {}

    public record AgentRemediationWorkflowHistoryEntry(
            String historyId,
            String eventType,
            String operatorId,
            String reason,
            Map<String, Object> metadata,
            OffsetDateTime occurredAt
    ) {}

    public record AgentRemediationProposalRequest(
            String operatorId,
            String reason,
            String sourceTaskId,
            String sourceRoutingDecisionId,
            Boolean persistEvent
    ) {}

    public record AgentRemediationProposalResponse(
            String proposalId,
            String agentId,
            String severity,
            int executableActionCount,
            int reviewOnlyActionCount,
            List<AgentRemediationActionView> actions,
            AgentSkillRemediationProposal skillProposal,
            Map<String, Object> context,
            List<String> summary,
            OffsetDateTime generatedAt
    ) {}

    public record AgentRemediationActionView(
            String actionId,
            String agentId,
            String actionType,
            String severity,
            boolean executable,
            String reason,
            List<String> prerequisites,
            Map<String, Object> commandHint,
            Map<String, Object> metadata
    ) {}
}
