package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowManagementService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowReadinessService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRequiredSkillView;
import com.opensocket.aievent.core.decision.EventIntakeApplicationService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRuleView;

class DispatchFlowControllerAggregateMutationTest {

    private final DispatchFlowController controller = new DispatchFlowController(
            mock(DispatchFlowManagementService.class),
            mock(DispatchFlowReadinessService.class),
            mock(EventIntakeApplicationService.class));

    @Test
    void partialRuleMutationIsRejected() {
        assertAggregateOnly(() -> controller.upsertRule("flow-1", new DispatchFlowRuleView(), "tenant-1"));
    }

    @Test
    void partialCapabilityMutationIsRejected() {
        assertAggregateOnly(() -> controller.upsertSkill("flow-1", new DispatchFlowRequiredSkillView(), "tenant-1"));
    }

    @Test
    void partialAgentMutationIsRejected() {
        assertAggregateOnly(() -> controller.upsertAgent("flow-1", new DispatchFlowAgentView(), "tenant-1"));
    }

    @Test
    void createShouldExposeMissingAgentProfileAsDomainError() {
        DispatchFlowManagementService service = mock(DispatchFlowManagementService.class);
        DispatchFlowController controller = new DispatchFlowController(
                service,
                mock(DispatchFlowReadinessService.class),
                mock(EventIntakeApplicationService.class));
        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId("tenant-a");
        flow.setFlowId("flow-1");
        when(service.createOrUpdateFlow(flow))
                .thenThrow(new IllegalArgumentException("Agent does not exist in the selected tenant: agent-local-001"));

        assertThatThrownBy(() -> controller.create(flow, "tenant-a"))
                .isInstanceOfSatisfying(StandardApiException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo(StandardApiErrorCode.FLOW_AGENT_PROFILE_NOT_FOUND.code());
                    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("agent-local-001");
                });
    }

    private void assertAggregateOnly(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    org.assertj.core.api.Assertions.assertThat(ex.getReason()).contains("complete Flow");
                });
    }
}
