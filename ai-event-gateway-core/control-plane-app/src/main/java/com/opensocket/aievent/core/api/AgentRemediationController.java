package com.opensocket.aievent.core.api;


import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.api.security.ServerActorAuthority;
import com.opensocket.aievent.core.agent.AgentRuntimeCapabilityItem;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;

import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;
import com.opensocket.aievent.core.agent.skill.AgentSkillRemediationAction;
import com.opensocket.aievent.core.agent.skill.AgentSkillRemediationProposal;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;


import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.observability.AgentRemediationWorkflowMetricsService;
import com.opensocket.aievent.core.runtime.CoreRuntimeDisconnectClient;


import com.opensocket.aievent.core.agent.remediation.AgentRemediationWorkflowStore;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;




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
    private final AgentGovernanceService agentGovernanceService;
    private final AgentDirectoryService agentDirectoryService;
    private final AgentSkillRegistryService skillRegistryService;
    private final RoutingProperties routingProperties;
    private final AgentRemediationWorkflowStore remediationWorkflowDao;
    private final CoreRuntimeDisconnectClient runtimeDisconnectClient;
    private final AgentRemediationWorkflowMetricsService remediationWorkflowMetrics;
    private final AgentRemediationWorkflowPersistence workflowPersistence;
    private final AgentRemediationCommandCoordinator remediationCommandCoordinator;
    private final ScopedBusinessResourceAccessCoordinator scopedAccess;

    public AgentRemediationController(AgentGovernanceService agentGovernanceService,
                                      AgentDirectoryService agentDirectoryService,
                                      AgentSkillRegistryService skillRegistryService,
                                      RoutingProperties routingProperties,
                                      AgentRemediationWorkflowStore remediationWorkflowDao,
                                      CoreRuntimeDisconnectClient runtimeDisconnectClient,
                                      AgentRemediationWorkflowMetricsService remediationWorkflowMetrics,
                                      ObjectMapper objectMapper) {
        this(agentGovernanceService, agentDirectoryService, skillRegistryService, routingProperties, remediationWorkflowDao,
                runtimeDisconnectClient, remediationWorkflowMetrics, objectMapper,
                (ScopedBusinessResourceAccessCoordinator) null);
    }

    private AgentRemediationController(AgentGovernanceService agentGovernanceService,
                                      AgentDirectoryService agentDirectoryService,
                                      AgentSkillRegistryService skillRegistryService,
                                      RoutingProperties routingProperties,
                                      AgentRemediationWorkflowStore remediationWorkflowDao,
                                      CoreRuntimeDisconnectClient runtimeDisconnectClient,
                                      AgentRemediationWorkflowMetricsService remediationWorkflowMetrics,
                                      ObjectMapper objectMapper, ScopedBusinessResourceAccessCoordinator scopedAccess) {
        this.agentGovernanceService = agentGovernanceService;
        this.agentDirectoryService = agentDirectoryService;
        this.skillRegistryService = skillRegistryService;
        this.routingProperties = routingProperties;
        this.remediationWorkflowDao = remediationWorkflowDao;
        this.runtimeDisconnectClient = runtimeDisconnectClient;
        this.remediationWorkflowMetrics = remediationWorkflowMetrics;
        this.workflowPersistence = new AgentRemediationWorkflowPersistence(remediationWorkflowDao, objectMapper);
        this.remediationCommandCoordinator = new AgentRemediationCommandCoordinator(
                agentGovernanceService, agentDirectoryService, skillRegistryService, remediationWorkflowDao,
                runtimeDisconnectClient, remediationWorkflowMetrics, workflowPersistence);
        this.scopedAccess = scopedAccess;
    }

    @Autowired
    public AgentRemediationController(AgentGovernanceService agentGovernanceService,
                                      AgentDirectoryService agentDirectoryService,
                                      AgentSkillRegistryService skillRegistryService,
                                      RoutingProperties routingProperties,
                                      AgentRemediationWorkflowStore remediationWorkflowDao,
                                      CoreRuntimeDisconnectClient runtimeDisconnectClient,
                                      AgentRemediationWorkflowMetricsService remediationWorkflowMetrics,
                                      ObjectMapper objectMapper,
                                      ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccessProvider) {
        this(agentGovernanceService, agentDirectoryService, skillRegistryService, routingProperties, remediationWorkflowDao,
                runtimeDisconnectClient, remediationWorkflowMetrics, objectMapper, scopedAccessProvider.getIfAvailable());
    }



    @GetMapping("/{agentId}/remediation/workflows")
    public List<AgentRemediationWorkflowResponse> listAgentRemediationWorkflows(@PathVariable String agentId) {
        authorizeAgent(agentId, "admin.agent.remediation.list.agent.remediation.workflows", ResourceAction.ActionKind.READ, false, "RS3_AGENT_REMEDIATION_LISTAGENTREMEDIATIONWORKFLOWS");
        return workflowPersistence.listByAgentId(agentId, 50);
    }

    @GetMapping("/{agentId}/remediation/workflows/{workflowId}")
    public AgentRemediationWorkflowResponse getAgentRemediationWorkflow(@PathVariable String agentId,
                                                                        @PathVariable String workflowId) {
        authorizeAgent(agentId, "admin.agent.remediation.get.agent.remediation.workflow", ResourceAction.ActionKind.READ, false, "RS3_AGENT_REMEDIATION_GETAGENTREMEDIATIONWORKFLOW");
        AgentRemediationWorkflowResponse workflow = workflowPersistence.require(agentId, workflowId);
        return workflow;
    }

    @PostMapping("/{agentId}/remediation/workflows")
    @Transactional
    public AgentRemediationWorkflowResponse createAgentRemediationWorkflow(@PathVariable String agentId,
                                                                           @RequestBody(required = false) AgentRemediationWorkflowCreateRequest request) {
        authorizeAgent(agentId, "admin.agent.remediation.create.agent.remediation.workflow", ResourceAction.ActionKind.MANAGE, true, "RS3_AGENT_REMEDIATION_CREATEAGENTREMEDIATIONWORKFLOW");
        AgentRemediationWorkflowCreateRequest requested = request == null
                ? new AgentRemediationWorkflowCreateRequest(null, List.of(), null, "Agent remediation workflow created.", false)
                : request;
        AgentRemediationWorkflowCreateRequest body = new AgentRemediationWorkflowCreateRequest(
                requested.proposalId(), requested.actionIds(), authoritativeActor(requested.operatorId()), requested.reason(), requested.riskAcknowledged());
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
                body.operatorId(),
                null,
                now,
                now,
                map("proposalSummary", proposal.summary(), "context", proposal.context()),
                null,
                null,
                null,
                0,
                false);
        workflowPersistence.insert(workflow);
        workflowPersistence.ensureActionExecutionRows(workflow);
        AgentRemediationWorkflowResponse persistedWorkflow = workflowPersistence.require(agentId, workflowId);
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
        authorizeAgent(agentId, "admin.agent.remediation.approve.agent.remediation.workflow", ResourceAction.ActionKind.APPROVE, true, "RS3_AGENT_REMEDIATION_APPROVEAGENTREMEDIATIONWORKFLOW");
        AgentRemediationWorkflowDecisionRequest requested = request == null
                ? new AgentRemediationWorkflowDecisionRequest(null, "Agent remediation workflow approved.", false)
                : request;
        AgentRemediationWorkflowDecisionRequest body = authoritativeDecision(requested);
        AgentRemediationWorkflowResponse current = workflowPersistence.require(agentId, workflowId);
        if (!"PENDING_APPROVAL".equals(current.status())) {
            throw new IllegalStateException("Only PENDING_APPROVAL remediation workflows can be approved.");
        }
        AgentRemediationWorkflowResponse updated = workflowPersistence.updateStatus(current, "APPROVED", body.operatorId(),
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
        authorizeAgent(agentId, "admin.agent.remediation.reject.agent.remediation.workflow", ResourceAction.ActionKind.APPROVE, true, "RS3_AGENT_REMEDIATION_REJECTAGENTREMEDIATIONWORKFLOW");
        AgentRemediationWorkflowDecisionRequest requested = request == null
                ? new AgentRemediationWorkflowDecisionRequest(null, "Agent remediation workflow rejected.", false)
                : request;
        AgentRemediationWorkflowDecisionRequest body = authoritativeDecision(requested);
        AgentRemediationWorkflowResponse current = workflowPersistence.require(agentId, workflowId);
        if (!"PENDING_APPROVAL".equals(current.status())) {
            throw new IllegalStateException("Only PENDING_APPROVAL remediation workflows can be rejected.");
        }
        AgentRemediationWorkflowResponse updated = workflowPersistence.updateStatus(current, "REJECTED", body.operatorId(),
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
        authorizeAgent(agentId, "admin.agent.remediation.cancel.agent.remediation.workflow", ResourceAction.ActionKind.MANAGE, true, "RS3_AGENT_REMEDIATION_CANCELAGENTREMEDIATIONWORKFLOW");
        AgentRemediationWorkflowDecisionRequest requested = request == null
                ? new AgentRemediationWorkflowDecisionRequest(null, "Agent remediation workflow cancelled.", false)
                : request;
        AgentRemediationWorkflowDecisionRequest body = authoritativeDecision(requested);
        AgentRemediationWorkflowResponse current = workflowPersistence.require(agentId, workflowId);
        if (AgentRemediationWorkflowExecutionPolicy.isTerminalWorkflowStatus(current.status())) {
            throw new IllegalStateException("Terminal remediation workflows cannot be cancelled.");
        }
        AgentRemediationWorkflowResponse updated = workflowPersistence.updateStatus(current, "CANCELLED", body.operatorId(),
                history("CANCELLED", body.operatorId(), body.reason(), Map.of()));
        remediationWorkflowMetrics.recordWorkflowDecision("CANCELLED", current.status(), updated.status(), updated.severity());
        persistWorkflowSecurityEvent(updated, AgentSecurityEventType.AGENT_REMEDIATION_WORKFLOW_CANCELLED, body.operatorId(), body.reason(), Map.of());
        return updated;
    }

    @PostMapping("/{agentId}/remediation/workflows/{workflowId}/execute")
    public AgentRemediationWorkflowResponse executeAgentRemediationWorkflow(@PathVariable String agentId,
                                                                            @PathVariable String workflowId,
                                                                            @RequestBody(required = false) AgentRemediationWorkflowDecisionRequest request) {
        authorizeAgent(agentId, "admin.agent.remediation.execute.agent.remediation.workflow", ResourceAction.ActionKind.EXECUTE, true, "RS3_AGENT_REMEDIATION_EXECUTEAGENTREMEDIATIONWORKFLOW");
        AgentRemediationWorkflowDecisionRequest requested = request == null
                ? new AgentRemediationWorkflowDecisionRequest(null, "Agent remediation workflow execution requested.", false)
                : request;
        return remediationCommandCoordinator.execute(agentId, workflowId, authoritativeDecision(requested));
    }

    @GetMapping("/{agentId}/remediation/proposal")
    public AgentRemediationProposalResponse previewAgentRemediation(@PathVariable String agentId) {
        authorizeAgent(agentId, "admin.agent.remediation.preview.agent.remediation", ResourceAction.ActionKind.READ, false, "RS3_AGENT_REMEDIATION_PREVIEWAGENTREMEDIATION");
        return buildProposal(agentId, new AgentRemediationProposalRequest(null, null, null, null, false));
    }

    @PostMapping("/{agentId}/remediation/proposal")
    public AgentRemediationProposalResponse createAgentRemediationProposal(@PathVariable String agentId,
                                                                           @RequestBody(required = false) AgentRemediationProposalRequest request) {
        authorizeAgent(agentId, "admin.agent.remediation.create.agent.remediation.proposal", ResourceAction.ActionKind.MANAGE, true, "RS3_AGENT_REMEDIATION_CREATEAGENTREMEDIATIONPROPOSAL");
        AgentRemediationProposalRequest requested = request == null
                ? new AgentRemediationProposalRequest(null, null, null, null, true)
                : request;
        AgentRemediationProposalRequest body = new AgentRemediationProposalRequest(
                authoritativeActor(requested.operatorId()), requested.reason(), requested.sourceTaskId(), requested.sourceRoutingDecisionId(), requested.persistEvent());
        AgentRemediationProposalResponse proposal = buildProposal(agentId, body);
        if (body.persistEvent() == null || body.persistEvent()) {
            persistProposalSecurityEvent(proposal, body);
        }
        return proposal;
    }

    private void authorizeAgent(String agentId, String permission, ResourceAction.ActionKind kind, boolean sideEffecting, String purpose) {
        if (scopedAccess == null) return;
        scopedAccess.authorize(ResourceType.AGENT, agentId, permission, kind, sideEffecting, VisibilityLevel.SENSITIVE, purpose);
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


    private String authoritativeActor(String requestedActorId) {
        String actorId = ServerActorAuthority.requireActorId();
        ServerActorAuthority.rejectSpoofedActor(requestedActorId, actorId);
        return actorId;
    }

    private AgentRemediationWorkflowDecisionRequest authoritativeDecision(AgentRemediationWorkflowDecisionRequest request) {
        AgentRemediationWorkflowDecisionRequest body = request == null
                ? new AgentRemediationWorkflowDecisionRequest(null, null, false)
                : request;
        return new AgentRemediationWorkflowDecisionRequest(authoritativeActor(body.operatorId()), body.reason(), body.dryRun());
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
                firstNonBlank(operatorId, "system"),
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

    private void persistProposalSecurityEvent(AgentRemediationProposalResponse proposal, AgentRemediationProposalRequest request) {
        AgentSecurityEvent event = new AgentSecurityEvent();
        event.setAgentId(proposal.agentId());
        event.setClaimedAgentId(proposal.agentId());
        event.setEventType(AgentSecurityEventType.AGENT_REMEDIATION_PROPOSAL_GENERATED);
        event.setReason(firstNonBlank(request == null ? null : request.reason(), "Agent remediation proposal generated."));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("proposalId", proposal.proposalId());
        metadata.put("operatorId", firstNonBlank(request == null ? null : request.operatorId(), "system"));
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
