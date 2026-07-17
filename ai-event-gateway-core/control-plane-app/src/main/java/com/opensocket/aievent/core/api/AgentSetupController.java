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

@RestController
public class AgentSetupController {
    @Autowired
    private AgentSetupService agentSetupService;

    @Autowired(required = false)
    private AgentOperationalViewService operationalViewService;

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
        try {
            return agentSetupService.getSetupReadiness(agentId);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }


    @GetMapping("/admin/agents/{agentId}/operational-view")
    public AgentOperationalView getOperationalView(@PathVariable String agentId) {
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
            return agentSetupService.setupAgent(request == null ? new AgentSetupRequest() : request);
        } catch (IllegalArgumentException ex) {
            throw new StandardApiException(StandardApiErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }
}
