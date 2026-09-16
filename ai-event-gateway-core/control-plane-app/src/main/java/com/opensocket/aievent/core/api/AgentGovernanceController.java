package com.opensocket.aievent.core.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.api.security.ServerActorAuthority;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentConnectionAuthorizationRequest;
import com.opensocket.aievent.core.agent.governance.AgentConnectionRepairActionCommand;
import com.opensocket.aievent.core.agent.governance.AgentConnectionRepairActionResult;
import com.opensocket.aievent.core.agent.governance.AgentConnectionRepairActionsResponse;
import com.opensocket.aievent.core.agent.governance.AgentCredentialStatus;
import com.opensocket.aievent.core.agent.governance.AgentConnectionAuthorizationResult;
import com.opensocket.aievent.core.agent.governance.AgentDuplicateRuntimeResolveCommand;
import com.opensocket.aievent.core.agent.governance.AgentDuplicateRuntimeSecurityCommand;
import com.opensocket.aievent.core.agent.governance.AgentCredentialIssueCommand;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentApprovalCommand;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRejectCommand;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentRequest;
import com.opensocket.aievent.core.agent.governance.AgentEnrollmentStatus;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceTableDiagnostic;
import com.opensocket.aievent.core.agent.governance.AgentLatestAuthFailureResponse;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentProfileUpdateCommand;
import com.opensocket.aievent.core.agent.governance.AgentProfileApprovalCommand;
import com.opensocket.aievent.core.agent.governance.AgentRiskStatus;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEvent;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEnforcementPolicy;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEnforcementPolicyUpdateCommand;
import com.opensocket.aievent.core.agent.governance.AgentSecurityEventType;
import com.opensocket.aievent.core.runtime.CoreRuntimeDisconnectClient;
import com.opensocket.aievent.core.runtime.RuntimeDisconnectException;
import com.opensocket.aievent.core.runtime.RuntimeDisconnectResult;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedAgentQueryService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;

@RestController
public class AgentGovernanceController {
    private static final Logger log = LoggerFactory.getLogger(AgentGovernanceController.class);

    private final AgentGovernanceService agentGovernanceService;
    private final AgentDirectoryService agentDirectoryService;
    private final CoreRuntimeDisconnectClient runtimeDisconnectClient;
    private final ScopedBusinessResourceAccessCoordinator scopedAccess;
    private final ScopedAgentQueryService scopedAgents;

    /** Test/backward-compatible constructor; production Spring uses the scoped constructor below when available. */
    public AgentGovernanceController(AgentGovernanceService agentGovernanceService,
                                     AgentDirectoryService agentDirectoryService,
                                     CoreRuntimeDisconnectClient runtimeDisconnectClient) {
        this(agentGovernanceService, agentDirectoryService, runtimeDisconnectClient,
                (ScopedBusinessResourceAccessCoordinator) null, (ScopedAgentQueryService) null);
    }

    private AgentGovernanceController(AgentGovernanceService agentGovernanceService,
                                      AgentDirectoryService agentDirectoryService,
                                      CoreRuntimeDisconnectClient runtimeDisconnectClient,
                                      ScopedBusinessResourceAccessCoordinator scopedAccess,
                                      ScopedAgentQueryService scopedAgents) {
        this.agentGovernanceService = agentGovernanceService;
        this.agentDirectoryService = agentDirectoryService;
        this.runtimeDisconnectClient = runtimeDisconnectClient;
        this.scopedAccess = scopedAccess;
        this.scopedAgents = scopedAgents;
    }

    @Autowired
    public AgentGovernanceController(AgentGovernanceService agentGovernanceService,
                                     AgentDirectoryService agentDirectoryService,
                                     CoreRuntimeDisconnectClient runtimeDisconnectClient,
                                     ObjectProvider<ScopedBusinessResourceAccessCoordinator> scopedAccessProvider,
                                     ObjectProvider<ScopedAgentQueryService> scopedAgentsProvider) {
        this(agentGovernanceService, agentDirectoryService, runtimeDisconnectClient,
                scopedAccessProvider.getIfAvailable(), scopedAgentsProvider.getIfAvailable());
    }

    @PostMapping("/internal/agents/enrollments")
    public AgentEnrollmentRequest submitEnrollment(@RequestBody AgentEnrollmentRequest request) {
        return agentGovernanceService.submitEnrollment(request);
    }

    @PostMapping("/internal/agents/authorize-connection")
    public AgentConnectionAuthorizationResult authorizeConnection(@RequestBody AgentConnectionAuthorizationRequest request) {
        return agentGovernanceService.authorizeConnection(request);
    }

    @PostMapping("/internal/agents/security-events")
    public AgentSecurityEvent saveSecurityEvent(@RequestBody AgentSecurityEvent event) {
        AgentSecurityEvent saved = agentGovernanceService.saveSecurityEvent(event);
        maybeAutoEnforceDuplicateRuntimeEvent(saved);
        return saved;
    }

    @GetMapping("/admin/agent-enrollments")
    public List<AgentEnrollmentRequest> searchEnrollments(@RequestParam(required = false) AgentEnrollmentStatus status,
                                                          @RequestParam(defaultValue = "100") int limit) {
        requireTenantWide("admin.agent.governance.search.enrollments", ResourceType.AGENT, VisibilityLevel.SENSITIVE, "RS3_AGENT_ENROLLMENTS");
        return agentGovernanceService.searchEnrollments(status, limit);
    }

    @PostMapping("/admin/agent-enrollments")
    public AgentEnrollmentRequest createEnrollment(@RequestBody AgentEnrollmentRequest request) {
        if (scopedAccess != null && request != null) request.setTenantId(scopedAccess.activeTenantId());
        String claimedAgentId = request == null ? null : firstNonBlank(request.getClaimedAgentId(), request.getAgentName());
        String tenantId = request == null ? null : request.getTenantId();
        log.info("admin_agent_enrollment_create_received tenantId={} claimedAgentId={} agentName={} agentType={}",
                safeForLog(tenantId), safeForLog(claimedAgentId), safeForLog(request == null ? null : request.getAgentName()),
                safeForLog(request == null ? null : request.getAgentType()));
        try {
            AgentEnrollmentRequest saved = agentGovernanceService.submitEnrollment(request);
            log.info("admin_agent_enrollment_create_completed tenantId={} claimedAgentId={} enrollmentId={} status={}",
                    safeForLog(saved == null ? tenantId : saved.getTenantId()),
                    safeForLog(claimedAgentId),
                    safeForLog(saved == null ? null : saved.getEnrollmentId()),
                    safeForLog(saved == null || saved.getStatus() == null ? null : saved.getStatus().name()));
            return saved;
        } catch (RuntimeException ex) {
            log.error("admin_agent_enrollment_create_failed tenantId={} claimedAgentId={} agentName={} agentType={} exception={} message={}",
                    safeForLog(tenantId), safeForLog(claimedAgentId),
                    safeForLog(request == null ? null : request.getAgentName()),
                    safeForLog(request == null ? null : request.getAgentType()),
                    ex.getClass().getName(), safeForLog(ex.getMessage()), ex);
            throw ex;
        }
    }

    @PostMapping("/admin/agent-enrollments/{enrollmentId}/approve")
    public AgentProfile approveEnrollment(@PathVariable String enrollmentId,
                                          @RequestBody(required = false) AgentEnrollmentApprovalCommand request) {
        AgentEnrollmentApprovalCommand body = request == null ? new AgentEnrollmentApprovalCommand() : request;
        body.setApprovedBy(authoritativeActor(body.getApprovedBy()));
        if (scopedAccess != null) {
            body.setTenantId(scopedAccess.activeTenantId());
            if (scopedAgents != null && (body.getOwnerDepartmentId() != null || body.getOwnerGroupId() != null)) {
                String candidateAgentId = body.getAgentId() == null || body.getAgentId().isBlank() ? "pending-" + enrollmentId : body.getAgentId();
                ResourceListScopeQueryPlan plan = scopedAccess.plan("admin.agent.governance.approve.enrollment", ResourceType.AGENT, VisibilityLevel.SENSITIVE, "RS3_AGENT_ENROLLMENT_OWNER");
                scopedAgents.requireAssignableOwner(plan, scopedAccess.activeTenantId(), candidateAgentId, body.getOwnerDepartmentId(), body.getOwnerGroupId());
            } else {
                requireTenantWide("admin.agent.governance.approve.enrollment", ResourceType.AGENT, VisibilityLevel.SENSITIVE, "RS3_AGENT_ENROLLMENT_APPROVAL");
            }
        }
        return agentGovernanceService.approveEnrollment(enrollmentId, body);
    }

    @PostMapping("/admin/agent-enrollments/{enrollmentId}/reject")
    public AgentEnrollmentRequest rejectEnrollment(@PathVariable String enrollmentId,
                                                   @RequestBody(required = false) AgentEnrollmentRejectCommand request) {
        requireTenantWide("admin.agent.governance.reject.enrollment", ResourceType.AGENT, VisibilityLevel.SENSITIVE, "RS3_AGENT_ENROLLMENT_REJECT");
        AgentEnrollmentRejectCommand requested = request == null ? new AgentEnrollmentRejectCommand(null, null) : request;
        AgentEnrollmentRejectCommand body = new AgentEnrollmentRejectCommand(authoritativeActor(requested.rejectedBy()), requested.reason());
        AgentEnrollmentRequest saved = agentGovernanceService.rejectEnrollment(enrollmentId, body);
        String agentId = firstNonBlank(saved.getClaimedAgentId(), saved.getAgentName());
        enforceRuntimeDisconnect(agentId, null, body.rejectedBy(), firstNonBlank(body.reason(), "Enrollment rejected by Core governance"));
        return saved;
    }

    @GetMapping("/admin/agents")
    public List<AgentProfile> searchAgents(@RequestParam(required = false) AgentApprovalStatus approvalStatus,
                                           @RequestParam(defaultValue = "100") int limit) {
        if (scopedAccess == null || scopedAgents == null) return agentGovernanceService.searchProfiles(approvalStatus, limit);
        ResourceListScopeQueryPlan plan = scopedAccess.plan("admin.agent.governance.search.agents", ResourceType.AGENT, VisibilityLevel.STANDARD, "RS3_AGENT_LIST");
        return scopedAgents.search(approvalStatus, limit, plan);
    }

    @GetMapping("/admin/agents/{agentId}")
    public AgentProfile getAgent(@PathVariable String agentId) {
        authorizeAgent(ResourceType.AGENT, agentId, "admin.agent.governance.get.agent", ResourceAction.ActionKind.READ, false, VisibilityLevel.STANDARD, "RS3_AGENT_DETAIL");
        return agentGovernanceService.getProfile(agentId);
    }

    @PutMapping("/admin/agents/{agentId}")
    public AgentProfile updateAgent(@PathVariable String agentId, @RequestBody(required = false) AgentProfileUpdateCommand request) {
        authorizeAgent(ResourceType.AGENT, agentId, "admin.agent.governance.update.agent", ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_UPDATE");
        AgentProfileUpdateCommand body = request == null ? new AgentProfileUpdateCommand() : request;
        body.setOperatorId(authoritativeActor(body.getOperatorId()));
        request = body;
        if (scopedAccess != null) {
            String activeTenant = scopedAccess.activeTenantId();
            request.setTenantId(activeTenant);
            if (scopedAgents != null && (request.getOwnerDepartmentId() != null || request.getOwnerGroupId() != null)) {
                ResourceListScopeQueryPlan plan = scopedAccess.plan("admin.agent.governance.update.agent", ResourceType.AGENT, VisibilityLevel.SENSITIVE, "RS3_AGENT_REASSIGN_OWNER");
                AgentProfile current = agentGovernanceService.getProfile(agentId);
                String department = request.getOwnerDepartmentId() == null ? current.getOwnerDepartmentId() : request.getOwnerDepartmentId();
                String group = request.getOwnerGroupId() == null ? current.getOwnerGroupId() : request.getOwnerGroupId();
                scopedAgents.requireAssignableOwner(plan, activeTenant, agentId, department, group);
            }
        }
        AgentProfile saved = agentGovernanceService.updateProfile(agentId, request);
        if (!saved.allowsConnection()) {
            enforceRuntimeDisconnect(agentId, null, request == null ? null : request.getOperatorId(),
                    request == null ? "Agent profile updated to a non-connectable state" : firstNonBlank(request.getReason(), "Agent profile updated to a non-connectable state"));
        }
        return saved;
    }


    @PostMapping("/admin/agents/{agentId}/credentials/issue")
    public AgentProfile issueCredential(@PathVariable String agentId,
                                        @RequestBody(required = false) AgentCredentialIssueCommand request) {
        authorizeAgent(ResourceType.AGENT_CREDENTIAL_METADATA, agentId, "admin.agent.governance.issue.credential", ResourceAction.ActionKind.MANAGE, true, VisibilityLevel.SECRET_METADATA, "RS3_AGENT_CREDENTIAL_ISSUE");
        AgentCredentialIssueCommand body = request == null ? new AgentCredentialIssueCommand() : request;
        body.setOperatorId(authoritativeActor(body.getOperatorId()));
        return agentGovernanceService.issueCredential(agentId, body);
    }

    @PostMapping("/admin/agents/{agentId}/approve")
    public AgentProfile approveAgent(@PathVariable String agentId,
                                     @RequestBody(required = false) AgentProfileApprovalCommand request) {
        authorizeAgent(ResourceType.AGENT, agentId, "admin.agent.governance.approve.agent", ResourceAction.ActionKind.APPROVE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_APPROVE");
        AgentProfileApprovalCommand body = request == null ? new AgentProfileApprovalCommand() : request;
        body.setOperatorId(authoritativeActor(body.getOperatorId()));
        return agentGovernanceService.approveAgent(agentId, body);
    }

    @PostMapping("/admin/agents/{agentId}/enable")
    public AgentProfile enableAgent(@PathVariable String agentId, @RequestBody(required = false) AgentAdminActionRequest request) {
        authorizeAgent(ResourceType.AGENT, agentId, "admin.agent.governance.enable.agent", ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_ENABLE");
        AgentAdminActionRequest requested = request == null ? new AgentAdminActionRequest(null, null, null) : request;
        AgentAdminActionRequest body = new AgentAdminActionRequest(authoritativeActor(requested.operatorId()), requested.reason(), requested.gatewayNodeId());
        return agentGovernanceService.enableAgent(agentId, body.operatorId(), body.reason());
    }

    @PostMapping("/admin/agents/{agentId}/disable")
    public AgentProfile disableAgent(@PathVariable String agentId, @RequestBody(required = false) AgentAdminActionRequest request) {
        authorizeAgent(ResourceType.AGENT, agentId, "admin.agent.governance.disable.agent", ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_DISABLE");
        AgentAdminActionRequest requested = request == null ? new AgentAdminActionRequest(null, null, null) : request;
        AgentAdminActionRequest body = new AgentAdminActionRequest(authoritativeActor(requested.operatorId()), requested.reason(), requested.gatewayNodeId());
        AgentProfile saved = agentGovernanceService.disableAgent(agentId, body.operatorId(), body.reason());
        enforceRuntimeDisconnect(agentId, body.gatewayNodeId(), body.operatorId(), firstNonBlank(body.reason(), "Agent disabled by Core governance"));
        return saved;
    }

    @PostMapping("/admin/agents/{agentId}/suspend")
    public AgentProfile suspendAgent(@PathVariable String agentId, @RequestBody(required = false) AgentAdminActionRequest request) {
        authorizeAgent(ResourceType.AGENT, agentId, "admin.agent.governance.suspend.agent", ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_SUSPEND");
        AgentAdminActionRequest requested = request == null ? new AgentAdminActionRequest(null, null, null) : request;
        AgentAdminActionRequest body = new AgentAdminActionRequest(authoritativeActor(requested.operatorId()), requested.reason(), requested.gatewayNodeId());
        AgentProfile saved = agentGovernanceService.suspendAgent(agentId, body.operatorId(), body.reason());
        enforceRuntimeDisconnect(agentId, body.gatewayNodeId(), body.operatorId(), firstNonBlank(body.reason(), "Agent suspended by Core governance"));
        return saved;
    }

    @PostMapping("/admin/agents/{agentId}/revoke")
    public AgentProfile revokeAgent(@PathVariable String agentId, @RequestBody(required = false) AgentAdminActionRequest request) {
        authorizeAgent(ResourceType.AGENT, agentId, "admin.agent.governance.revoke.agent", ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_REVOKE");
        AgentAdminActionRequest requested = request == null ? new AgentAdminActionRequest(null, null, null) : request;
        AgentAdminActionRequest body = new AgentAdminActionRequest(authoritativeActor(requested.operatorId()), requested.reason(), requested.gatewayNodeId());
        AgentProfile saved = agentGovernanceService.revokeAgent(agentId, body.operatorId(), body.reason());
        enforceRuntimeDisconnect(agentId, body.gatewayNodeId(), body.operatorId(), firstNonBlank(body.reason(), "Agent revoked by Core governance"));
        return saved;
    }


    @PostMapping("/admin/agents/{agentId}/disconnect")
    public RuntimeDisconnectResult disconnectAgentRuntime(@PathVariable String agentId,
                                                         @RequestBody(required = false) AgentAdminActionRequest request) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.disconnect.agent.runtime", ResourceAction.ActionKind.EXECUTE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_RUNTIME_DISCONNECT");
        AgentAdminActionRequest requested = request == null ? new AgentAdminActionRequest(null, null, null) : request;
        AgentAdminActionRequest body = new AgentAdminActionRequest(authoritativeActor(requested.operatorId()), requested.reason(), requested.gatewayNodeId());
        return enforceRuntimeDisconnect(agentId, body.gatewayNodeId(), body.operatorId(), firstNonBlank(body.reason(), "Manual Core runtime disconnect request"));
    }

    @PostMapping("/admin/agents/runtime-disconnect/reconcile")
    public RuntimeDisconnectReconcileReport reconcileBlockedAgentRuntimes(@RequestBody(required = false) RuntimeDisconnectReconcileRequest request) {
        requireTenantWide("admin.agent.governance.reconcile.blocked.agent.runtimes", ResourceType.AGENT_SERVICE_SCOPE, VisibilityLevel.SENSITIVE, "RS3_AGENT_RUNTIME_RECONCILE");
        RuntimeDisconnectReconcileRequest requested = request == null ? new RuntimeDisconnectReconcileRequest(null, null, 500, false) : request;
        RuntimeDisconnectReconcileRequest body = new RuntimeDisconnectReconcileRequest(authoritativeActor(requested.operatorId()), requested.gatewayNodeId(), requested.limit(), requested.includeAgentsWithoutOwner());
        int limit = body.limit() == null ? 500 : Math.max(1, Math.min(5000, body.limit()));
        List<RuntimeDisconnectReconcileItem> items = new ArrayList<>();
        int evaluated = 0;
        int attempted = 0;
        int closed = 0;
        int failed = 0;
        for (AgentProfile profile : agentGovernanceService.searchProfiles(null, limit)) {
            if (profile == null || profile.getAgentId() == null || profile.getAgentId().isBlank()) {
                continue;
            }
            if (profile.allowsConnection()) {
                continue;
            }
            evaluated++;
            String ownerGatewayNodeId = firstNonBlank(body.gatewayNodeId(), ownerGatewayNodeId(profile.getAgentId()));
            if ((ownerGatewayNodeId == null || ownerGatewayNodeId.isBlank()) && !body.includeAgentsWithoutOwner()) {
                items.add(new RuntimeDisconnectReconcileItem(profile.getAgentId(), null, "SKIPPED_NO_OWNER", null));
                continue;
            }
            RuntimeDisconnectResult result = enforceRuntimeDisconnect(profile.getAgentId(), ownerGatewayNodeId, body.operatorId(),
                    "Runtime disconnect reconcile for non-connectable Core Agent profile");
            attempted++;
            if (result.closed()) closed++;
            if (!result.closed()) failed++;
            saveRuntimeDisconnectReconcileEvent(profile.getAgentId(), result);
            items.add(new RuntimeDisconnectReconcileItem(profile.getAgentId(), ownerGatewayNodeId, result.status(), result));
        }
        return new RuntimeDisconnectReconcileReport(evaluated, attempted, closed, failed, items, OffsetDateTime.now(ZoneOffset.UTC));
    }

    @PostMapping("/admin/agents/{agentId}/disconnect-all")
    public RuntimeDisconnectResult disconnectAllAgentRuntimeSessions(@PathVariable String agentId,
                                                                    @RequestBody(required = false) AgentClusterDisconnectRequest request) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.disconnect.all.agent.runtime.sessions", ResourceAction.ActionKind.EXECUTE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_RUNTIME_DISCONNECT_ALL");
        AgentClusterDisconnectRequest requested = request == null ? new AgentClusterDisconnectRequest(null, null, List.of()) : request;
        AgentClusterDisconnectRequest body = new AgentClusterDisconnectRequest(authoritativeActor(requested.operatorId()), requested.reason(), requested.gatewayNodeIds());
        List<String> requestedGatewayNodeIds = body.gatewayNodeIds() == null ? List.of() : body.gatewayNodeIds().stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (requestedGatewayNodeIds.isEmpty()) {
            String owner = ownerGatewayNodeId(agentId);
            requestedGatewayNodeIds = owner == null || owner.isBlank() ? List.of() : List.of(owner);
        }
        if (requestedGatewayNodeIds.isEmpty()) {
            return RuntimeDisconnectResult.failed(agentId, null, 0, "No gatewayNodeIds were provided and Core directory has no owner gateway node for cluster-aware disconnect.");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int closedCount = 0;
        int requestedCount = 0;
        for (String gatewayNodeId : requestedGatewayNodeIds) {
            RuntimeDisconnectResult result = enforceRuntimeDisconnect(agentId, gatewayNodeId, body.operatorId(),
                    firstNonBlank(body.reason(), "Manual Core cluster-aware disconnect-all request"));
            requestedCount += result.requested() ? 1 : 0;
            closedCount += result.closed() ? 1 : 0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("agentId", result.agentId());
            item.put("gatewayNodeId", result.gatewayNodeId());
            item.put("status", result.status());
            item.put("requested", result.requested());
            item.put("closed", result.closed());
            item.put("httpStatus", result.httpStatus());
            item.put("message", result.message());
            item.put("details", result.details());
            results.add(item);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requestedGatewayNodeIds", requestedGatewayNodeIds);
        details.put("results", results);
        details.put("requestedCount", requestedCount);
        details.put("closedCount", closedCount);
        String status = closedCount == requestedGatewayNodeIds.size() ? "DISCONNECTED_ALL" : closedCount > 0 ? "PARTIAL_DISCONNECT" : "DISCONNECT_ALL_FAILED";
        return new RuntimeDisconnectResult(
                agentId,
                String.join(",", requestedGatewayNodeIds),
                status,
                requestedCount > 0,
                closedCount > 0,
                200,
                "Cluster-aware runtime disconnect completed. closed=" + closedCount + "/" + requestedGatewayNodeIds.size(),
                details,
                OffsetDateTime.now()
        );
    }

    private void saveRuntimeDisconnectReconcileEvent(String agentId, RuntimeDisconnectResult result) {
        AgentSecurityEvent event = new AgentSecurityEvent();
        event.setAgentId(agentId);
        event.setClaimedAgentId(agentId);
        event.setGatewayNodeId(result == null ? null : result.gatewayNodeId());
        event.setEventType(result != null && result.closed()
                ? AgentSecurityEventType.RUNTIME_DISCONNECT_RECONCILE_COMPLETED
                : AgentSecurityEventType.RUNTIME_DISCONNECT_RECONCILE_FAILED);
        event.setReason(result == null ? "Runtime disconnect reconcile failed without a result." : result.message());
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (result != null) {
            metadata.put("status", result.status());
            metadata.put("requested", result.requested());
            metadata.put("closed", result.closed());
            metadata.put("httpStatus", result.httpStatus());
            metadata.put("details", result.details());
        }
        event.setMetadata(metadata);
        agentGovernanceService.saveSecurityEvent(event);
    }

    private RuntimeDisconnectResult enforceRuntimeDisconnect(String agentId, String gatewayNodeId, String operatorId, String reason) {
        if (agentId == null || agentId.isBlank()) {
            return RuntimeDisconnectResult.disabled(agentId, "No agent id was available for runtime disconnect enforcement.");
        }
        String ownerGatewayNodeId = firstNonBlank(gatewayNodeId, ownerGatewayNodeId(agentId));
        try {
            return runtimeDisconnectClient.disconnectAgent(agentId, ownerGatewayNodeId, reason, operatorId);
        } catch (RuntimeDisconnectException ex) {
            RuntimeDisconnectResult result = ex.getResult();
            if (result != null) {
                return result;
            }
            return RuntimeDisconnectResult.failed(agentId, ownerGatewayNodeId, 0,
                    "Core governance transition completed, but runtime disconnect failed: " + ex.getMessage());
        }
    }

    private String ownerGatewayNodeId(String agentId) {
        try {
            return agentDirectoryService.findById(agentId)
                    .map(snapshot -> snapshot.getOwnerGatewayNodeId())
                    .filter(value -> value != null && !value.isBlank())
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }


    private void maybeAutoEnforceDuplicateRuntimeEvent(AgentSecurityEvent event) {
        if (event == null || event.getEventType() != AgentSecurityEventType.DUPLICATE_RUNTIME_DETECTED) {
            return;
        }
        Map<String, Object> metadata = event.getMetadata() == null ? Map.of() : event.getMetadata();
        String agentId = firstNonBlank(event.getAgentId(), event.getClaimedAgentId());
        if (agentId == null || agentId.isBlank()) {
            return;
        }
        AgentSecurityEnforcementPolicy policy = agentGovernanceService.getSecurityEnforcementPolicy(agentId);
        Map<String, Object> evaluated = new LinkedHashMap<>(metadata);
        evaluated.put("policyId", policy.getPolicyId());
        evaluated.put("policyAgentId", policy.getAgentId());
        evaluated.put("duplicateRuntimeMode", policy.getDuplicateRuntimeMode().name());
        evaluated.put("policyEnabled", policy.isEnabled());
        AgentSecurityEvent policyEvent = new AgentSecurityEvent();
        policyEvent.setAgentId(agentId);
        policyEvent.setClaimedAgentId(agentId);
        policyEvent.setGatewayNodeId(event.getGatewayNodeId());
        policyEvent.setEventType(AgentSecurityEventType.DUPLICATE_RUNTIME_POLICY_EVALUATED);
        policyEvent.setReason("Core evaluated per-Agent duplicate runtime security enforcement policy.");
        policyEvent.setMetadata(evaluated);
        agentGovernanceService.saveSecurityEvent(policyEvent);
        agentGovernanceService.queueSecurityNotifications(agentId, event, policy);
        if (!policy.isEnabled() || !policy.shouldQuarantine()) {
            return;
        }
        try {
            AgentDuplicateRuntimeSecurityCommand command = new AgentDuplicateRuntimeSecurityCommand();
            command.setOperatorId(stringMetadata(metadata, "operatorId", "netty-auto-detection"));
            command.setReason(firstNonBlank(event.getReason(), "Netty duplicate runtime auto-detection triggered Core quarantine."));
            command.setGatewayNodeIds(stringListMetadata(metadata.get("gatewayNodeIds")));
            command.setConnectedCount(integerMetadata(metadata, "connectedCount"));
            command.setRequireCredentialRotation(policy.isRequireCredentialRotation());
            command.setRevokeCredentials(policy.shouldRevokeCredentials());
            command.setDisconnectAll(false);
            agentGovernanceService.enforceDuplicateRuntimeSecurity(agentId, command);
            if (policy.shouldDisconnectAll()) {
                disconnectAllAgentRuntimeSessions(agentId, new AgentClusterDisconnectRequest(
                        command.getOperatorId(),
                        "Per-Agent security policy requested duplicate-runtime disconnect-all",
                        command.getGatewayNodeIds()
                ));
            }
            AgentSecurityEvent autoEvent = new AgentSecurityEvent();
            autoEvent.setAgentId(agentId);
            autoEvent.setClaimedAgentId(agentId);
            autoEvent.setGatewayNodeId(event.getGatewayNodeId());
            autoEvent.setEventType(AgentSecurityEventType.DUPLICATE_RUNTIME_AUTO_ENFORCED);
            autoEvent.setReason("Core auto-enforced duplicate runtime event using per-Agent security policy.");
            autoEvent.setMetadata(evaluated);
            agentGovernanceService.saveSecurityEvent(autoEvent);
        } catch (Exception ex) {
            AgentSecurityEvent failed = new AgentSecurityEvent();
            failed.setAgentId(agentId);
            failed.setClaimedAgentId(agentId);
            failed.setGatewayNodeId(event.getGatewayNodeId());
            failed.setEventType(AgentSecurityEventType.DUPLICATE_RUNTIME_AUTO_ENFORCEMENT_FAILED);
            failed.setReason(ex.getMessage());
            failed.setMetadata(evaluated);
            agentGovernanceService.saveSecurityEvent(failed);
        }
    }

    private static boolean booleanMetadata(Map<String, Object> metadata, String key, boolean defaultValue) {
        Object value = metadata == null ? null : metadata.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value.toString());
    }

    private static String stringMetadata(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private static Integer integerMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null || value.toString().isBlank()) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> stringListMetadata(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(item -> item != null && !item.toString().isBlank()).map(Object::toString).distinct().toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.split(",")).stream().map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
        }
        return List.of();
    }

    @PostMapping("/admin/agents/{agentId}/security/duplicate-runtime/enforce")
    public AgentSecurityEnforcementResponse enforceDuplicateRuntimeSecurity(@PathVariable String agentId,
                                                                           @RequestBody(required = false) AgentDuplicateRuntimeSecurityCommand request) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.enforce.duplicate.runtime.security", ResourceAction.ActionKind.MANAGE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_DUPLICATE_RUNTIME_ENFORCE");
        AgentDuplicateRuntimeSecurityCommand body = request == null ? new AgentDuplicateRuntimeSecurityCommand() : request;
        AgentProfile profile = agentGovernanceService.enforceDuplicateRuntimeSecurity(agentId, body);
        RuntimeDisconnectResult disconnectResult = null;
        if (body.isDisconnectAll()) {
            disconnectResult = disconnectAllAgentRuntimeSessions(agentId, new AgentClusterDisconnectRequest(
                    body.getOperatorId(),
                    firstNonBlank(body.getReason(), "Duplicate runtime security enforcement disconnect-all"),
                    body.getGatewayNodeIds()
            ));
        }
        return new AgentSecurityEnforcementResponse(
                agentId,
                "DUPLICATE_RUNTIME_SECURITY_ENFORCED",
                profile,
                disconnectResult,
                body.isRequireCredentialRotation(),
                body.isRevokeCredentials(),
                new String[] {
                        "Deploy a newly issued credential token to exactly one authorized Agent runtime.",
                        "Stop any old Agent processes that still use the revoked or suspected credential.",
                        "Use Resolve Duplicate Runtime Security after rotation is deployed and old sessions are gone."
                },
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @PostMapping("/admin/agents/{agentId}/security/duplicate-runtime/resolve")
    public AgentSecurityEnforcementResponse resolveDuplicateRuntimeSecurity(@PathVariable String agentId,
                                                                           @RequestBody(required = false) AgentDuplicateRuntimeResolveCommand request) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.resolve.duplicate.runtime.security", ResourceAction.ActionKind.MANAGE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_DUPLICATE_RUNTIME_RESOLVE");
        AgentProfile profile = agentGovernanceService.resolveDuplicateRuntimeSecurity(agentId, request);
        return new AgentSecurityEnforcementResponse(
                agentId,
                "DUPLICATE_RUNTIME_SECURITY_RESOLVED",
                profile,
                null,
                false,
                false,
                new String[] {
                        "Confirm only one runtime session reconnects with the rotated credential.",
                        "Review Core security events for any further duplicate session or invalid credential attempts."
                },
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }


    @GetMapping("/admin/agents/{agentId}/security/enforcement-policy")
    public AgentSecurityEnforcementPolicy getAgentSecurityEnforcementPolicy(@PathVariable String agentId) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.get.agent.security.enforcement.policy", ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "RS3_AGENT_SECURITY_POLICY_READ");
        return agentGovernanceService.getSecurityEnforcementPolicy(agentId);
    }

    @PutMapping("/admin/agents/{agentId}/security/enforcement-policy")
    public AgentSecurityEnforcementPolicy updateAgentSecurityEnforcementPolicy(@PathVariable String agentId,
                                                                               @RequestBody(required = false) AgentSecurityEnforcementPolicyUpdateCommand request) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.update.agent.security.enforcement.policy", ResourceAction.ActionKind.UPDATE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_SECURITY_POLICY_UPDATE");
        return agentGovernanceService.updateSecurityEnforcementPolicy(agentId, request);
    }

    @GetMapping("/admin/agent-security-enforcement-policies")
    public List<AgentSecurityEnforcementPolicy> searchAgentSecurityEnforcementPolicies(@RequestParam(defaultValue = "100") int limit) {
        return agentGovernanceService.searchSecurityEnforcementPolicies(limit);
    }

    @GetMapping("/admin/agent-security-enforcement-policies/default")
    public AgentSecurityEnforcementPolicy getDefaultSecurityEnforcementPolicy() {
        return agentGovernanceService.getSecurityEnforcementPolicy("*");
    }

    @PutMapping("/admin/agent-security-enforcement-policies/default")
    public AgentSecurityEnforcementPolicy updateDefaultSecurityEnforcementPolicy(@RequestBody(required = false) AgentSecurityEnforcementPolicyUpdateCommand request) {
        return agentGovernanceService.updateSecurityEnforcementPolicy("*", request);
    }

    @GetMapping("/admin/agent-security-events")
    public List<AgentSecurityEvent> searchSecurityEvents(@RequestParam(required = false) String agentId,
                                                         @RequestParam(defaultValue = "100") int limit) {
        if (agentId == null || agentId.isBlank()) {
            requireTenantWide("admin.agent.governance.search.security.events", ResourceType.AGENT_SERVICE_SCOPE, VisibilityLevel.SENSITIVE, "RS3_AGENT_SECURITY_EVENTS_TENANT");
        } else {
            authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.search.security.events", ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "RS3_AGENT_SECURITY_EVENTS");
        }
        return agentGovernanceService.searchSecurityEvents(agentId, limit);
    }

    @GetMapping("/admin/agents/{agentId}/latest-auth-failure")
    public AgentLatestAuthFailureResponse latestAuthFailure(@PathVariable String agentId) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.latest.auth.failure", ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "RS3_AGENT_LATEST_AUTH_FAILURE");
        return agentGovernanceService.latestAuthFailure(agentId);
    }

    @GetMapping("/admin/agents/{agentId}/connection-repair-actions")
    public AgentConnectionRepairActionsResponse connectionRepairActions(@PathVariable String agentId) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.connection.repair.actions", ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "RS3_AGENT_REPAIR_ACTIONS");
        return agentGovernanceService.connectionRepairActions(agentId);
    }

    @PostMapping("/admin/agents/{agentId}/connection-repair-actions/{actionCode}")
    public AgentConnectionRepairActionResult executeConnectionRepairAction(@PathVariable String agentId,
                                                                           @PathVariable String actionCode,
                                                                           @RequestBody(required = false) AgentConnectionRepairActionCommand request) {
        authorizeAgent(ResourceType.AGENT_SERVICE_SCOPE, agentId, "admin.agent.governance.execute.connection.repair.action", ResourceAction.ActionKind.EXECUTE, true, VisibilityLevel.SENSITIVE, "RS3_AGENT_REPAIR_EXECUTE");
        return agentGovernanceService.executeConnectionRepairAction(agentId, actionCode, request);
    }

    @GetMapping("/admin/agent-governance/table-diagnostics")
    public List<AgentGovernanceTableDiagnostic> tableDiagnostics() {
        return agentGovernanceService.tableDiagnostics();
    }

    @GetMapping("/admin/agent-governance/metadata")
    public AgentGovernanceMetadata metadata() {
        return new AgentGovernanceMetadata(
                agentGovernanceService.mode(),
                AgentApprovalStatus.values(),
                AgentEnrollmentStatus.values(),
                AgentRiskStatus.values(),
                AgentCredentialStatus.values(),
                new String[] {
                        "READY",
                        "NO_CORE_PROFILE",
                        "PROFILE_NOT_APPROVED",
                        "PROFILE_DISABLED",
                        "RISK_BLOCKED",
                        "CREDENTIAL_MISSING",
                        "CREDENTIAL_NOT_ACTIVE",
                        "RUNTIME_OFFLINE",
                        "AUTH_UNKNOWN",
                        "AUTH_DENIED"
                },
                new String[] {
                        "OFFLINE",
                        "AUTH_UNKNOWN",
                        "AUTHORIZED",
                        "AUTH_DENIED",
                        "AUTH_REVOKED",
                        "AUTH_DISCONNECTED_BY_POLICY",
                        "AUTH_UNVERIFIED",
                        "AUTH_OTHER"
                },
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private String authoritativeActor(String requestedActorId) {
        String actorId = ServerActorAuthority.requireActorId();
        ServerActorAuthority.rejectSpoofedActor(requestedActorId, actorId);
        return actorId;
    }

    private void authorizeAgent(ResourceType type, String agentId, String permission, ResourceAction.ActionKind kind,
                                boolean sideEffecting, VisibilityLevel visibility, String purpose) {
        if (scopedAccess == null) return;
        scopedAccess.authorize(type, agentId, permission, kind, sideEffecting, visibility, purpose);
    }

    private void requireTenantWide(String permission, ResourceType type, VisibilityLevel visibility, String purpose) {
        if (scopedAccess == null) return;
        scopedAccess.requireTenantWide(permission, type, visibility, purpose);
    }

    public record AgentAdminActionRequest(String operatorId, String reason, String gatewayNodeId) {}
    public record AgentClusterDisconnectRequest(String operatorId, String reason, List<String> gatewayNodeIds) {}
    public record RuntimeDisconnectReconcileRequest(String operatorId, String gatewayNodeId, Integer limit, boolean includeAgentsWithoutOwner) {}
    public record RuntimeDisconnectReconcileItem(String agentId, String gatewayNodeId, String status, RuntimeDisconnectResult runtimeDisconnect) {}
    public record RuntimeDisconnectReconcileReport(int evaluated, int attempted, int closed, int failed, List<RuntimeDisconnectReconcileItem> items, OffsetDateTime occurredAt) {}
    public record AgentSecurityEnforcementResponse(
            String agentId,
            String status,
            AgentProfile profile,
            RuntimeDisconnectResult runtimeDisconnect,
            boolean credentialRotationRequired,
            boolean credentialsRevoked,
            String[] nextActions,
            OffsetDateTime occurredAt
    ) {}
    public record AgentGovernanceMetadata(
            String storeMode,
            AgentApprovalStatus[] approvalStatuses,
            AgentEnrollmentStatus[] enrollmentStatuses,
            AgentRiskStatus[] riskStatuses,
            AgentCredentialStatus[] credentialStatuses,
            String[] readinessStatuses,
            String[] runtimeAuthorizationStatuses,
            OffsetDateTime now
    ) {}
    private String safeForLog(String value) {
        if (value == null) return "";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256) + "...";
    }

}
