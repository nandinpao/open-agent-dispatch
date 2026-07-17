package com.opensocket.aievent.core.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.assignment.AssignmentStatus;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.task.TaskRecord;

class DispatchEligibilityServiceStage7Test {

    @Test
    void dispatchRequestCreationUsesPersistedFlowAssignmentWithoutHistoricalProfiles() {
        AgentDirectoryFacade directory = mock(AgentDirectoryFacade.class);
        DispatchProperties properties = new DispatchProperties();
        properties.setRequestCreationEnabled(true);
        properties.setReviewMode(DispatchReviewMode.AUTO_APPROVE);
        properties.setRequireAssignableAgent(true);
        when(directory.findById("agent-stage7")).thenReturn(Optional.of(runtime()));

        DispatchEligibilityService service = new DispatchEligibilityService(directory, properties);
        DispatchEligibilityService.EligibilityResult result = service.check(assignment(), task());

        assertThat(result.eligible()).isTrue();
        assertThat(result.reason())
                .contains("FLOW_RULE_AGENT_CAPABILITY_RUNTIME")
                .contains("legacyEligibility=DECOMMISSIONED");
    }

    private TaskAssignment assignment() {
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assignment-stage7");
        assignment.setTaskId("task-stage7");
        assignment.setAgentId("agent-stage7");
        assignment.setOwnerGatewayNodeId("gateway-stage7");
        assignment.setAgentSessionId("session-stage7");
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        return assignment;
    }

    private TaskRecord task() {
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-stage7");
        task.setTenantId("tenant-stage7");
        task.setRoutingPath("FLOW_RULE");
        task.setMatchedFlowId("flow-stage7");
        task.setMatchedRuleId("rule-stage7");
        return task;
    }

    private AgentSnapshot runtime() {
        AgentSnapshot agent = new AgentSnapshot();
        agent.setAgentId("agent-stage7");
        agent.setOwnerGatewayNodeId("gateway-stage7");
        agent.setAgentSessionId("session-stage7");
        agent.setStatus(AgentStatus.IDLE);
        agent.setMaxConcurrentTasks(4);
        agent.setAvailableSlots(4);
        return agent;
    }
}
