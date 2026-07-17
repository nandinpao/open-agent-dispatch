package com.opensocket.aievent.core.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.task.TaskDecisionResult;
import com.opensocket.aievent.core.task.TaskType;

class DecisionEngineSummaryTest {

    @Test
    void shouldExposeNoActiveFlowAsWaitingConfiguration() {
        TaskDecisionResult decision = decision(false, false,
                "No ACTIVE Flow-owned Dispatch Rule matched this event",
                "Routing did not select an agent",
                "removed two-step dispatch did not create a dispatch request: FLOW_RULE_REQUIRED_BLOCKED");

        assertThat(DecisionEngine.primaryReasonCode(decision)).isEqualTo("NO_ACTIVE_FLOW_RULE");
        assertThat(DecisionEngine.primaryStatus(decision)).isEqualTo("WAITING_CONFIGURATION");
        assertThat(DecisionEngine.nextAction(decision)).isEqualTo("CREATE_OR_ACTIVATE_DISPATCH_FLOW");
    }

    @Test
    void shouldExposeMissingCapabilityAsWaitingConfiguration() {
        TaskDecisionResult decision = decision(false, false,
                "REQUIRED_CAPABILITY_MISSING: CAP_DOCUMENT_ANALYSIS", null, null);

        assertThat(DecisionEngine.primaryReasonCode(decision)).isEqualTo("REQUIRED_CAPABILITY_MISSING");
        assertThat(DecisionEngine.primaryStatus(decision)).isEqualTo("WAITING_CONFIGURATION");
        assertThat(DecisionEngine.nextAction(decision)).isEqualTo("APPROVE_REQUIRED_CAPABILITY");
    }

    @Test
    void shouldExposeRuntimeAndCapacityBlockersWithoutLegacyVocabulary() {
        TaskDecisionResult offline = decision(false, false,
                "NO_ACTIVE_RUNTIME_SESSION", null, null);
        TaskDecisionResult capacity = decision(false, false,
                "Agent runtime has NO_CAPACITY", null, null);

        assertThat(DecisionEngine.primaryReasonCode(offline)).isEqualTo("AGENT_OFFLINE");
        assertThat(DecisionEngine.nextAction(offline)).isEqualTo("RESTORE_AGENT_RUNTIME");
        assertThat(DecisionEngine.primaryReasonCode(capacity)).isEqualTo("AGENT_NO_CAPACITY");
        assertThat(DecisionEngine.nextAction(capacity)).isEqualTo("WAIT_OR_ADD_AGENT_CAPACITY");
    }


    @Test
    void shouldNotReportNoActiveFlowWhenFlowMatchedButNoAgentEligible() {
        TaskDecisionResult decision = decision(false, false,
                "Immediate severity matched | resolution=[failClosed=NO_ACTIVE_FLOW_RULE] | Flow Rule matched: flowId=flow-e2e; ruleId=rule-e2e",
                "P11 generic authority fail-closed: NO_GENERIC_ELIGIBLE_AGENT: blockers=[AGENT_RUNTIME_NOT_FOUND]",
                "No assignment was created; task dispatch recovery scheduled");

        assertThat(DecisionEngine.primaryReasonCode(decision)).isEqualTo("AGENT_OFFLINE");
        assertThat(DecisionEngine.primaryStatus(decision)).isEqualTo("NEEDS_ATTENTION");
        assertThat(DecisionEngine.nextAction(decision)).isEqualTo("RESTORE_AGENT_RUNTIME");
    }

    @Test
    void shouldExposeQueuedDispatchWhenAssignmentAndRequestExist() {
        TaskDecisionResult decision = decision(true, true, "Flow matched", "eligible", "queued");

        assertThat(DecisionEngine.primaryReasonCode(decision)).isEqualTo("AGENT_SELECTED");
        assertThat(DecisionEngine.primaryStatus(decision)).isEqualTo("DISPATCH_QUEUED");
        assertThat(DecisionEngine.nextAction(decision)).isEqualTo("MONITOR_TASK_DELIVERY");
    }

    private TaskDecisionResult decision(boolean assignmentCreated,
                                        boolean dispatchRequestCreated,
                                        String reason,
                                        String assignmentReason,
                                        String dispatchReason) {
        return new TaskDecisionResult(
                true, false, "task-stage7-fix2", TaskType.INCIDENT_RESPONSE, reason, List.of(),
                assignmentCreated, assignmentCreated ? "assignment-stage7-fix2" : null,
                assignmentCreated ? "agent-stage7-fix2" : null,
                assignmentCreated ? "gateway-stage7-fix2" : null, null,
                assignmentCreated ? "route-stage7-fix2" : null,
                assignmentCreated ? "ASSIGNED" : "ROUTING_NOT_SELECTED", assignmentReason,
                dispatchRequestCreated, dispatchRequestCreated ? "dispatch-stage7-fix2" : null,
                dispatchRequestCreated ? "QUEUED" : null, null, null, null, dispatchReason);
    }
}
