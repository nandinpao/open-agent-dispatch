package com.opensocket.aievent.core.api;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.opensocket.aievent.core.agent.eligibility.AgentDispatchEligibility;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityService;
import com.opensocket.aievent.core.agent.eligibility.TaskDispatchRequirements;
import com.opensocket.aievent.core.agent.eligibility.TaskEligibleAgentsResponse;
import com.opensocket.aievent.core.task.TaskOperationalQuery;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceAction;
import com.opensocket.aievent.core.resourceaccess.contract.ResourceType;
import com.opensocket.aievent.core.resourceaccess.contract.VisibilityLevel;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;

@RestController
@RequestMapping("/admin")
public class DispatchEligibilityController {
    private final DispatchEligibilityService dispatchEligibilityService;
    private final TaskOperationalQuery taskQuery;
    private ScopedBusinessResourceAccessCoordinator scopedAccess;

    public DispatchEligibilityController(DispatchEligibilityService dispatchEligibilityService,
                                         TaskOperationalQuery taskQuery) {
        this.dispatchEligibilityService = dispatchEligibilityService;
        this.taskQuery = taskQuery;
    }

    @Autowired(required = false)
    void setScopedAccess(ObjectProvider<ScopedBusinessResourceAccessCoordinator> provider) {
        this.scopedAccess = provider == null ? null : provider.getIfAvailable();
    }

    @GetMapping("/agents/{agentId}/dispatch-eligibility")
    public AgentDispatchEligibility agentDispatchEligibility(@PathVariable String agentId,
                                                             @RequestParam(required = false) String taskId) {
        if (scopedAccess != null) scopedAccess.authorize(ResourceType.AGENT, agentId, "admin.dispatch.eligibility.agent.dispatch.eligibility", ResourceAction.ActionKind.READ, false, VisibilityLevel.SENSITIVE, "RS3_AGENT_DISPATCH_ELIGIBILITY");
        if (taskId == null || taskId.isBlank()) {
            return dispatchEligibilityService.evaluateAgent(agentId);
        }
        return dispatchEligibilityService.evaluateAgent(agentId, requireTask(taskId));
    }

    @GetMapping("/tasks/{taskId}/dispatch-requirements")
    public TaskDispatchRequirements taskDispatchRequirements(@PathVariable String taskId) {
        return dispatchEligibilityService.resolveTaskRequirements(requireTask(taskId));
    }

    @GetMapping("/tasks/{taskId}/eligible-agents")
    public TaskEligibleAgentsResponse eligibleAgents(@PathVariable String taskId,
                                                     @RequestParam(defaultValue = "500") int limit) {
        return dispatchEligibilityService.eligibleAgents(requireTask(taskId), limit);
    }

    @GetMapping("/tasks/{taskId}/eligible-agents-v2")
    public TaskEligibleAgentsResponse eligibleAgentsV2(@PathVariable String taskId,
                                                       @RequestParam(defaultValue = "500") int limit) {
        return eligibleAgents(taskId, limit);
    }


    private TaskRecord requireTask(String taskId) {
        return taskQuery.findTask(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
    }
}
