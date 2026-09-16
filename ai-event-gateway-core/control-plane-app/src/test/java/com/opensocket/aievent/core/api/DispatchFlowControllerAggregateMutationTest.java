package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.opensocket.aievent.core.dispatch.flow.DispatchFlowAgentView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowView;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowManagementService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowActivationService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowReadinessService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRequiredSkillView;
import com.opensocket.aievent.core.decision.EventIntakeApplicationService;
import com.opensocket.aievent.core.dispatch.flow.DispatchFlowRuleView;
import com.opensocket.aievent.core.resourceaccess.runtime.ScopedBusinessResourceAccessCoordinator;

class DispatchFlowControllerAggregateMutationTest {

    private final DispatchFlowController controller = new DispatchFlowController(
            mock(DispatchFlowManagementService.class),
            mock(DispatchFlowActivationService.class),
            mock(DispatchFlowReadinessService.class),
            mock(EventIntakeApplicationService.class),
            noScopedAccess());

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
    void createRequiresExplicitIssuePolicyInsteadOfSilentlyDefaultingToOptional() {
        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId("tenant-a");
        flow.setFlowId("flow-explicit-policy");

        assertThatThrownBy(() -> controller.create(flow, "tenant-a"))
                .isInstanceOfSatisfying(StandardApiException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo(StandardApiErrorCode.BAD_REQUEST.code());
                    org.assertj.core.api.Assertions.assertThat(ex.getMessage())
                            .contains("defaultIssueSyncPolicy is required for Source Flow writes")
                            .contains("will not silently choose Issue behavior");
                });
    }

    @Test
    void updateRequiresExplicitIssuePolicyInsteadOfSilentlyDefaultingToOptional() {
        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId("tenant-a");

        assertThatThrownBy(() -> controller.update("flow-1", flow, "tenant-a", null))
                .isInstanceOfSatisfying(StandardApiException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo(StandardApiErrorCode.BAD_REQUEST.code());
                    org.assertj.core.api.Assertions.assertThat(ex.getMessage())
                            .contains("defaultIssueSyncPolicy is required for Source Flow writes");
                });
    }

    @Test
    void createShouldExposeMissingAgentProfileAsDomainError() {
        DispatchFlowManagementService service = mock(DispatchFlowManagementService.class);
        DispatchFlowController controller = new DispatchFlowController(
                service,
                mock(DispatchFlowActivationService.class),
                mock(DispatchFlowReadinessService.class),
                mock(EventIntakeApplicationService.class),
                noScopedAccess());
        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId("tenant-a");
        flow.setFlowId("flow-1");
        flow.setDefaultIssueSyncPolicy("NONE");
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

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ScopedBusinessResourceAccessCoordinator> noScopedAccess() {
        ObjectProvider<ScopedBusinessResourceAccessCoordinator> provider =
                (ObjectProvider<ScopedBusinessResourceAccessCoordinator>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
