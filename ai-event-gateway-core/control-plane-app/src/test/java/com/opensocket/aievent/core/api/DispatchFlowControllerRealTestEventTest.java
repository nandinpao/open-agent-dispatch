package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.decision.EventIntakeApplicationService;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowManagementService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowReadinessService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRuleView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowView;
import com.opensocket.aievent.core.event.EventIntakeRequest;

class DispatchFlowControllerRealTestEventTest {

    private final DispatchFlowManagementService management = mock(DispatchFlowManagementService.class);
    private final EventIntakeApplicationService intake = mock(EventIntakeApplicationService.class);
    private final DispatchFlowController controller = new DispatchFlowController(
            management,
            mock(DispatchFlowReadinessService.class),
            intake);

    @Test
    void createsRealEventFromPersistedActiveFlow() {
        DispatchFlowView flow = activeFlow();
        EventIntakeDecisionResponse expected = mock(EventIntakeDecisionResponse.class);
        when(management.findFlow("tenant-a", "flow-1")).thenReturn(Optional.of(flow));
        when(intake.intake(any(EventIntakeRequest.class))).thenReturn(expected);

        EventIntakeDecisionResponse actual = controller.createRealTestEvent(
                "flow-1",
                Map.of("message", "real test", "severity", "HIGH"),
                "tenant-a");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<EventIntakeRequest> request = ArgumentCaptor.forClass(EventIntakeRequest.class);
        verify(intake).intake(request.capture());
        assertThat(request.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(request.getValue().getSourceSystem()).isEqualTo("SRC_E2E");
        assertThat(request.getValue().getEventStage()).isEqualTo("EXTERNAL");
        assertThat(request.getValue().getObjectType()).isEqualTo("ORDER");
        assertThat(request.getValue().getEventType()).isEqualTo("ORDER_FAILED");
        assertThat(request.getValue().getSeverity()).isEqualTo("HIGH");
        assertThat(request.getValue().getMessage()).isEqualTo("real test");
        assertThat(request.getValue().getCorrelationId()).startsWith("stage5-test-");
        assertThat(request.getValue().getAttributes())
                .containsEntry("openDispatchRealTestEvent", true)
                .containsEntry("flowId", "flow-1")
                .containsEntry("ruleId", "rule-1");
    }

    @Test
    void rejectsDraftFlowWithoutCallingIntake() {
        DispatchFlowView flow = activeFlow();
        flow.setStatus("DRAFT");
        when(management.findFlow("tenant-a", "flow-1")).thenReturn(Optional.of(flow));

        assertThatThrownBy(() -> controller.createRealTestEvent("flow-1", Map.of(), "tenant-a"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private DispatchFlowView activeFlow() {
        DispatchFlowRuleView rule = new DispatchFlowRuleView();
        rule.setRuleId("rule-1");
        rule.setEventStage("EXTERNAL");
        rule.setSourceSystem("SRC_E2E");
        rule.setObjectType("ORDER");
        rule.setEventType("ORDER_FAILED");
        rule.setErrorCode("*");
        rule.setEnabled(true);

        DispatchFlowAgentView agent = new DispatchFlowAgentView();
        agent.setAgentId("agent-e2e");
        agent.setApprovalStatus("APPROVED");

        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId("tenant-a");
        flow.setFlowId("flow-1");
        flow.setFlowCode("FLOW_E2E");
        flow.setFlowName("E2E Flow");
        flow.setSourceSystem("SRC_E2E");
        flow.setStatus("ACTIVE");
        flow.setRules(List.of(rule));
        flow.setAgents(List.of(agent));
        return flow;
    }
}
