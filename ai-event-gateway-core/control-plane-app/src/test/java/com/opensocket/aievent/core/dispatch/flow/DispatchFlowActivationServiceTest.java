package com.opensocket.aievent.core.dispatch.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.api.StandardApiException;
import com.opensocket.aievent.core.routing.RoutingSimulationService;

class DispatchFlowActivationServiceTest {

    @Test
    void draftSaveDoesNotInvokeProductionRoutingValidation() {
        DispatchFlowManagementService flows = mock(DispatchFlowManagementService.class);
        RoutingSimulationService simulation = mock(RoutingSimulationService.class);
        DispatchFlowActivationService service = new DispatchFlowActivationService(flows, simulation);
        DispatchFlowView draft = flow("DRAFT");
        when(flows.createOrUpdateFlow(draft, 3)).thenReturn(draft);

        assertThat(service.saveAndValidateActivation(draft, 3)).isSameAs(draft);
        verify(simulation, never()).simulate(any());
    }

    @Test
    void activeSaveRunsProductionRoutingBoundaryAndReturnsWhenRuntimeIsReady() {
        DispatchFlowManagementService flows = mock(DispatchFlowManagementService.class);
        RoutingSimulationService simulation = mock(RoutingSimulationService.class);
        DispatchFlowActivationService service = new DispatchFlowActivationService(flows, simulation);
        DispatchFlowView active = flow("ACTIVE");
        when(flows.createOrUpdateFlow(active, 3)).thenReturn(active);
        DispatchSimulationResponse ready = new DispatchSimulationResponse();
        ready.setDispatchable(true);
        ready.setTargetPoolId("pool-1");
        ready.setPoolMemberCount(1);
        when(simulation.simulate(any())).thenAnswer(invocation -> {
            DispatchSimulationRequest request = invocation.getArgument(0);
            assertThat(request.getEvaluationMode()).isEqualTo("RUNTIME_READINESS");
            assertThat(request.getFlowId()).isEqualTo("flow-1");
            return ready;
        });

        assertThat(service.saveAndValidateActivation(active, 3)).isSameAs(active);
        verify(flows).createOrUpdateFlow(active, 3);
    }

    @Test
    void activeSaveAllowsConfiguredPoolWhenAgentRuntimeDoesNotExistYet() {
        DispatchFlowManagementService flows = mock(DispatchFlowManagementService.class);
        RoutingSimulationService simulation = mock(RoutingSimulationService.class);
        DispatchFlowActivationService service = new DispatchFlowActivationService(flows, simulation);
        DispatchFlowView active = flow("ACTIVE");
        when(flows.createOrUpdateFlow(active, 3)).thenReturn(active);
        DispatchSimulationResponse blocked = new DispatchSimulationResponse();
        blocked.setDispatchable(false);
        blocked.setManualOnly(false);
        blocked.setTargetPoolId("pool-1");
        blocked.setPoolMemberCount(1);
        blocked.setBlockerCode("POOL_AGENT_RUNTIME_NOT_FOUND");
        blocked.setBlockerReason("No eligible Agent was available in the resolved Agent Pool.");
        when(simulation.simulate(any())).thenReturn(blocked);

        assertThat(service.saveAndValidateActivation(active, 3)).isSameAs(active);
        verify(flows).createOrUpdateFlow(active, 3);
    }

    @Test
    void activeSaveAllowsTransientOfflineOrCapacityRuntimeBlockers() {
        for (String blocker : List.of("POOL_AGENT_OFFLINE", "POOL_AGENT_CAPACITY_FULL", "POOL_AGENT_BACKOFF", "NO_ELIGIBLE_AGENT_IN_POOL")) {
            DispatchFlowManagementService flows = mock(DispatchFlowManagementService.class);
            RoutingSimulationService simulation = mock(RoutingSimulationService.class);
            DispatchFlowActivationService service = new DispatchFlowActivationService(flows, simulation);
            DispatchFlowView active = flow("ACTIVE");
            when(flows.createOrUpdateFlow(active, 3)).thenReturn(active);
            DispatchSimulationResponse blocked = new DispatchSimulationResponse();
            blocked.setDispatchable(false);
            blocked.setTargetPoolId("pool-1");
            blocked.setPoolMemberCount(1);
            blocked.setBlockerCode(blocker);
            when(simulation.simulate(any())).thenReturn(blocked);

            assertThat(service.saveAndValidateActivation(active, 3)).as(blocker).isSameAs(active);
        }
    }

    @Test
    void activeSaveFailsClosedWhenTargetPoolHasNoActiveMember() {
        DispatchFlowManagementService flows = mock(DispatchFlowManagementService.class);
        RoutingSimulationService simulation = mock(RoutingSimulationService.class);
        DispatchFlowActivationService service = new DispatchFlowActivationService(flows, simulation);
        DispatchFlowView active = flow("ACTIVE");
        when(flows.createOrUpdateFlow(active, 3)).thenReturn(active);
        DispatchSimulationResponse blocked = new DispatchSimulationResponse();
        blocked.setDispatchable(false);
        blocked.setTargetPoolId("pool-1");
        blocked.setPoolMemberCount(0);
        blocked.setBlockerCode("POOL_HAS_NO_ACTIVE_MEMBER");
        blocked.setBlockerReason("Pool has no active member");
        when(simulation.simulate(any())).thenReturn(blocked);

        assertThatThrownBy(() -> service.saveAndValidateActivation(active, 3))
                .isInstanceOf(StandardApiException.class)
                .hasMessageContaining("durable routing configuration is blocked")
                .hasMessageContaining("POOL_HAS_NO_ACTIVE_MEMBER");
        verify(flows).createOrUpdateFlow(active, 3);
    }

    @Test
    void activeSaveFailsClosedWhenRuleDoesNotResolveToSourceFlow() {
        DispatchFlowManagementService flows = mock(DispatchFlowManagementService.class);
        RoutingSimulationService simulation = mock(RoutingSimulationService.class);
        DispatchFlowActivationService service = new DispatchFlowActivationService(flows, simulation);
        DispatchFlowView active = flow("ACTIVE");
        when(flows.createOrUpdateFlow(active, 3)).thenReturn(active);
        DispatchSimulationResponse blocked = new DispatchSimulationResponse();
        blocked.setDispatchable(false);
        blocked.setBlockerCode("SOURCE_FLOW_NOT_MATCHED");
        blocked.setBlockerReason("No deterministic Flow Rule matched");
        when(simulation.simulate(any())).thenReturn(blocked);

        assertThatThrownBy(() -> service.saveAndValidateActivation(active, 3))
                .isInstanceOf(StandardApiException.class)
                .hasMessageContaining("SOURCE_FLOW_NOT_MATCHED");
    }

    private DispatchFlowView flow(String status) {
        DispatchFlowView flow = new DispatchFlowView();
        flow.setTenantId("tenant-a");
        flow.setFlowId("flow-1");
        flow.setFlowCode("ERP_FLOW");
        flow.setSourceSystem("ERP");
        flow.setStatus(status);
        DispatchFlowRuleView rule = new DispatchFlowRuleView();
        rule.setRuleId("rule-1");
        rule.setRuleCode("ERP_ORDER_FAILED");
        rule.setEventStage("EXTERNAL");
        rule.setSourceSystem("ERP");
        rule.setEventType("ORDER_FAILED");
        rule.setEnabled(true);
        flow.setRules(List.of(rule));
        return flow;
    }
}
