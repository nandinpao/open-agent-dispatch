package com.opensocket.aievent.core.routing.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.dispatch.flow.FlowRuleRoutingPlan;
import com.opensocket.aievent.core.task.TaskRecord;

class GenericDispatchRequirementResolverAuthorityTest {

    private final GenericDispatchRequirementResolver resolver = new GenericDispatchRequirementResolver();

    @Test
    void sourceFlowResolvesAgentPoolAsCandidateBoundaryAndCoreApprovalAsCapabilityAuthority() {
        FlowRuleRoutingPlan plan = plan();
        plan.setRequiredSkills(List.of("erp.diagnose"));

        DispatchRequirementResolution result = resolver.resolve(task(), plan);

        assertThat(result.getOutcome()).isEqualTo(RequirementDecisionStatus.RESOLVED);
        assertThat(result.getCandidatePoolMode()).isEqualTo(CandidatePoolMode.SOURCE_SYSTEM_POOL);
        assertThat(result.getRequiredCapabilities()).containsExactly("ERP_DIAGNOSE");
        assertThat(result.getRoutingStrategy()).isEqualTo(GenericRoutingStrategy.LOWEST_LOAD);
        assertThat(result.getDetails())
                .containsEntry("candidateAuthority", "AGENT_POOL_MEMBERSHIP")
                .containsEntry("targetPoolId", "pool-stage1")
                .containsEntry("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY")
                .containsEntry("runtimeReportedCapabilitiesAuthority", false);
    }

    @Test
    void missingTargetPoolFailsClosedBeforeCandidateSelection() {
        FlowRuleRoutingPlan plan = plan();
        plan.setTargetPoolId(null);
        plan.setDefaultPoolId(null);
        TaskRecord task = task();
        task.setTargetPoolId(null);
        task.setAssignedPoolId(null);

        DispatchRequirementResolution result = resolver.resolve(task, plan);

        assertThat(result.getOutcome()).isEqualTo(RequirementDecisionStatus.BLOCKED);
        assertThat(result.getReasonCode()).isEqualTo("SOURCE_FLOW_HAS_NO_TARGET_POOL");
    }

    private TaskRecord task() {
        TaskRecord task = new TaskRecord();
        task.setTenantId("tenant-stage1");
        task.setTaskId("task-stage1");
        task.setSourceSystem("src-stage1-random");
        task.setTargetPoolId("pool-stage1");
        return task;
    }

    private FlowRuleRoutingPlan plan() {
        FlowRuleRoutingPlan plan = new FlowRuleRoutingPlan();
        plan.setMatched(true);
        plan.setFlowId("flow-stage1");
        plan.setRuleId("rule-stage1");
        plan.setTargetPoolId("pool-stage1");
        plan.setTargetPoolCode("POOL_STAGE1");
        plan.setSelectionStrategy("LOWEST_LOAD");
        plan.setCandidatePoolMode("SOURCE_SYSTEM_POOL");
        return plan;
    }
}
