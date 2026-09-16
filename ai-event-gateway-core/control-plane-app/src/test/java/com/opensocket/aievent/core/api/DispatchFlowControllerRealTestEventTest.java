package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.decision.EventIntakeApplicationService;
import com.opensocket.aievent.core.decision.EventIntakeDecisionResponse;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowManagementService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowActivationService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowReadinessService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRuleView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowView;
import com.opensocket.aievent.core.event.EventIntakeRequest;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;

class DispatchFlowControllerRealTestEventTest {

    private final DispatchFlowManagementService management = mock(DispatchFlowManagementService.class);
    private final EventIntakeApplicationService intake = mock(EventIntakeApplicationService.class);
    private final DispatchFlowController controller = new DispatchFlowController(
            management,
            mock(DispatchFlowActivationService.class),
            mock(DispatchFlowReadinessService.class),
            intake,
            noScopedAccess());

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
        assertThat(request.getValue().getCorrelationId()).startsWith("source-flow-test-");
        assertThat(request.getValue().getAttributes())
                .containsEntry("openDispatchRealTestEvent", true)
                .containsEntry("flowId", "flow-1")
                .containsEntry("flowMatchExpectation", "MATCHED")
                .containsEntry("testRuleHint", "rule-1")
                .containsEntry("routingEntry", "FLOW_MATCH_AUTHORITY");
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

    @Test
    void createsRealEventThatExercisesNoMatchWhenNoExternalRuleExists() {
        DispatchFlowView flow = activeFlow();
        flow.setRules(List.of());
        flow.setDefaultPoolId(null);
        EventIntakeDecisionResponse expected = mock(EventIntakeDecisionResponse.class);
        when(management.findFlow("tenant-a", "flow-1")).thenReturn(Optional.of(flow));
        when(intake.intake(any(EventIntakeRequest.class))).thenReturn(expected);

        EventIntakeDecisionResponse actual = controller.createRealTestEvent(
                "flow-1",
                Map.of("message", "no-match real test", "eventType", "PAYMENT_BLOCKED_BY_RISK_RULE", "objectType", "PAYMENT"),
                "tenant-a");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<EventIntakeRequest> request = ArgumentCaptor.forClass(EventIntakeRequest.class);
        verify(intake).intake(request.capture());
        assertThat(request.getValue().getSourceSystem()).isEqualTo("SRC_E2E");
        assertThat(request.getValue().getObjectType()).isEqualTo("PAYMENT");
        assertThat(request.getValue().getEventType()).isEqualTo("PAYMENT_BLOCKED_BY_RISK_RULE");
        assertThat(request.getValue().getAttributes())
                .containsEntry("flowMatchExpectation", "NO_MATCH")
                .containsEntry("routingEntry", "FLOW_MATCH_AUTHORITY")
                .doesNotContainKey("ruleId")
                .doesNotContainKey("testRuleHint");
    }


    @Test
    void rejectsSuccessfulIssueExpectationWhenEffectivePolicyIsNotRequired() {
        DispatchFlowView flow = activeFlow();
        flow.setDefaultIssueSyncPolicy("OPTIONAL");
        when(management.findFlow("tenant-a", "flow-1")).thenReturn(Optional.of(flow));

        assertThatThrownBy(() -> controller.createRealTestEvent(
                "flow-1",
                Map.of("expectExternalIssueOnSuccess", true, "expectedIssueSyncPolicy", "REQUIRED"),
                "tenant-a"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).contains("Issue policy preflight failed").contains("effective policy is OPTIONAL");
                });

        verifyNoInteractions(intake);
    }

    @Test
    void acceptsRequiredRuleOverrideAndCarriesPolicyEvidenceIntoRealEvent() {
        DispatchFlowView flow = activeFlow();
        flow.setDefaultIssueSyncPolicy("OPTIONAL");
        flow.getRules().get(0).setIssueSyncPolicy("REQUIRED");
        EventIntakeDecisionResponse expected = mock(EventIntakeDecisionResponse.class);
        when(management.findFlow("tenant-a", "flow-1")).thenReturn(Optional.of(flow));
        when(intake.intake(any(EventIntakeRequest.class))).thenReturn(expected);

        EventIntakeDecisionResponse actual = controller.createRealTestEvent(
                "flow-1",
                Map.of("expectExternalIssueOnSuccess", true, "expectedIssueSyncPolicy", "REQUIRED"),
                "tenant-a");

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<EventIntakeRequest> request = ArgumentCaptor.forClass(EventIntakeRequest.class);
        verify(intake).intake(request.capture());
        assertThat(request.getValue().getAttributes())
                .containsEntry("effectiveIssueSyncPolicy", "REQUIRED")
                .containsEntry("issueSyncPolicySource", "RULE_OVERRIDE")
                .containsEntry("expectedIssueSyncPolicy", "REQUIRED")
                .containsEntry("expectExternalIssueOnSuccess", true);
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
        flow.setDefaultPoolId("pool-e2e");
        flow.setRules(List.of(rule));
        flow.setAgents(List.of(agent));
        return flow;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ScopedBusinessResourceAccessCoordinator> noScopedAccess() {
        ObjectProvider<ScopedBusinessResourceAccessCoordinator> provider =
                (ObjectProvider<ScopedBusinessResourceAccessCoordinator>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
