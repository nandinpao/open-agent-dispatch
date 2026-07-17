package com.opensocket.aievent.core.dispatch;

import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Structural and runtime eligibility gate before DispatchRequest creation.
 *
 * <p>Stage 7 decommissions all Assignment Profile, Service Scope, Task Scope,
 * and alternate lookup gates from the dispatch runtime. The authoritative
 * Flow/Rule/Agent/Capability decision has already been persisted on the
 * Assignment before this service is invoked.</p>
 */
@Service
public class DispatchEligibilityService {
    private final AgentDirectoryFacade agentDirectory;
    private final DispatchProperties properties;

    public DispatchEligibilityService(AgentDirectoryFacade agentDirectory, DispatchProperties properties) {
        this.agentDirectory = agentDirectory;
        this.properties = properties;
    }

    public EligibilityResult check(TaskAssignment assignment, TaskRecord task) {
        if (!properties.isRequestCreationEnabled()) {
            return new EligibilityResult(false, "Dispatch request creation disabled by DISPATCH_REQUEST_CREATION_ENABLED=false");
        }
        if (properties.getReviewMode() == DispatchReviewMode.DISABLED) {
            return new EligibilityResult(false, "Dispatch review mode DISABLED suppresses dispatch request creation");
        }
        if (assignment == null) {
            return new EligibilityResult(false, "Assignment is missing");
        }
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            return new EligibilityResult(false, "Assignment status is not ASSIGNED: " + assignment.getStatus());
        }
        if (isBlank(assignment.getAgentId())) {
            return new EligibilityResult(false, "Assignment has no target agent");
        }
        if (isBlank(assignment.getOwnerGatewayNodeId())) {
            return new EligibilityResult(false, "Assignment has no owner gateway node");
        }
        if (task == null) {
            return new EligibilityResult(false, "Task is missing");
        }
        if (properties.isRequireAssignableAgent()) {
            EligibilityResult assignable = agentDirectory.findById(assignment.getAgentId())
                    .map(agent -> {
                        boolean online = agent.getStatus() != AgentStatus.OFFLINE
                                && agent.getStatus() != AgentStatus.EXPIRED
                                && agent.getStatus() != AgentStatus.ERROR
                                && agent.getStatus() != AgentStatus.DRAINING;
                        if (assignment.isCapacityReserved() && online) {
                            return new EligibilityResult(true, "Assignment owns an atomic capacity reservation");
                        }
                        return agent.isAssignable()
                                ? new EligibilityResult(true, "Agent is assignable and owner gateway is known")
                                : new EligibilityResult(false, "Agent is not currently assignable: status=" + agent.getStatus());
                    })
                    .orElseGet(() -> new EligibilityResult(false, "Agent not found in Agent Directory: " + assignment.getAgentId()));
            if (!assignable.eligible()) {
                return assignable;
            }
        }
        return new EligibilityResult(true,
                "Assignment is structurally eligible; authority=FLOW_RULE_AGENT_CAPABILITY_RUNTIME; legacyEligibility=DECOMMISSIONED");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record EligibilityResult(boolean eligible, String reason) {}
}
