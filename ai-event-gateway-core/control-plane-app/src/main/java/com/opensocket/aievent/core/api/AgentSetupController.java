package com.opensocket.aievent.core.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.operational.AgentOperationalView;
import com.opensocket.aievent.core.agent.operational.AgentOperationalViewService;
import com.opensocket.aievent.core.agent.setup.AgentSetupReadinessResponse;
import com.opensocket.aievent.core.agent.setup.AgentSetupRequest;
import com.opensocket.aievent.core.agent.setup.AgentSetupResponse;
import com.opensocket.aievent.core.agent.setup.AgentSetupService;
import com.opensocket.aievent.core.api.security.ServerActorAuthority;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceListScopeQueryPlan;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedAgentQueryService;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;

@RestController
public class AgentSetupController {
    @Autowired
    private AgentSetupService agentSetupService;

    @Autowired(required = false)
    private AgentOperationalViewService operationalViewService;

    @Autowired(required = false)
    private ScopedBusinessResourceAccessCoordinator scopedAccess;

    @Autowired(required = false)
    private ScopedAgentQueryService scopedAgents;

    public AgentSetupController() {
        // Default constructor required for Spring contexts that instantiate MVC controllers
        // through SimpleInstantiationStrategy before applying field injection.
    }

    public AgentSetupController(AgentSetupService agentSetupService) {
        this.agentSetupService = agentSetupService;
    }

    public AgentSetupController(AgentSetupService agentSetupService,
                                AgentOperationalViewService operationalViewService) {
        this.agentSetupService = agentSetupService;
        this.operationalViewService = operationalViewService;
    }

    @GetMapping("/admin/agents/{agentId}/setup-readiness")
    public AgentSetupReadinessResponse getSetupReadiness(@PathVariable String agentId) {
        authorizeAgent(agentId, "admin.agent.setup.get.setup.readiness", ResourceAction.ActionKind.READ, false, "RS3_AGENT_SETUP_READINESS");
        try {
            return agentSetupService.getSetupReadiness(agentId);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }


    @GetMapping("/admin/agents/{agentId}/operational-view")
    public AgentOperationalView getOperationalView(@PathVariable String agentId) {
        authorizeAgent(agentId, "admin.agent.setup.get.operational.view", ResourceAction.ActionKind.READ, false, "RS3_AGENT_OPERATIONAL_VIEW");
        try {
            if (operationalViewService == null) {
                throw new IllegalArgumentException("Agent operational view service is not available in this test/application context");
            }
            return operationalViewService.getOperationalView(agentId);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/admin/agents/setup")
    public AgentSetupResponse setupAgent(@RequestBody(required = false) AgentSetupRequest request) {
        try {
            AgentSetupRequest body = request == null ? new AgentSetupRequest() : request;
            String actorId = ServerActorAuthority.requireActorId();
            ServerActorAuthority.rejectSpoofedActor(body.getOperatorId(), actorId);
            body.setOperatorId(actorId);
            if (scopedAccess != null) {
                String tenantId = scopedAccess.activeTenantId();
                body.setTenantId(tenantId);
                ResourceListScopeQueryPlan plan = scopedAccess.plan("admin.agent.setup.setup.agent", ResourceType.AGENT, VisibilityLevel.SENSITIVE, "RS3_AGENT_SETUP_CREATE");
                if (scopedAgents == null) {
                    if (!plan.tenantWide()) throw new StandardApiException(StandardApiErrorCode.FORBIDDEN, "Scoped Agent setup requires the RS3 Agent scope adapter.");
                } else {
                    scopedAgents.requireAssignableOwner(plan, tenantId, body.getAgentId(), body.getOwnerDepartmentId(), body.getOwnerGroupId());
                }
            }
            return agentSetupService.setupAgent(body);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }
    private void authorizeAgent(String agentId, String permission, ResourceAction.ActionKind kind, boolean sideEffecting, String purpose) {
        if (scopedAccess == null) return;
        scopedAccess.authorize(ResourceType.AGENT, agentId, permission, kind, sideEffecting, VisibilityLevel.SENSITIVE, purpose);
    }

}
