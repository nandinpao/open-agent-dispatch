package com.opensocket.aievent.core.dispatch.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.task.TaskRecord;

class FlowRuleRoutingServiceCharacterizationTest {

    @Test
    void shouldForwardOpaqueSourceAndCapabilityToPersistedRuleRepository() {
        AtomicReference<FlowRuleRuntimeQuery> captured = new AtomicReference<>();
        FlowRuleRoutingRepository repository = query -> {
            captured.set(query);
            FlowRuleRuntimeMatch match = new FlowRuleRuntimeMatch();
            match.setFlowId("flow-random-7f31");
            match.setRuleId("rule-random-4b92");
            match.setEventStage("EXTERNAL");
            match.setRuleScope("EXTERNAL_INTAKE");
            match.setRequestedSkill("CAP_RANDOM_719CD");
            match.setRequiredSkills(List.of("CAP_RANDOM_719CD"));
            match.setMatchReason("synthetic persisted rule matched");
            return Optional.of(match);
        };

        TaskRecord task = syntheticTask();
        FlowRuleRoutingPlan plan = new FlowRuleRoutingService(repository).resolve(task);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getTenantId()).isEqualTo("tenant-random-82a9");
        assertThat(captured.get().getSourceSystem()).isEqualTo("SRC_RANDOM_8F2A");
        assertThat(captured.get().getOriginSourceSystem()).isEqualTo("EDGE_RANDOM_1A2B");
        assertThat(captured.get().getTargetSystem()).isEqualTo("TARGET_RANDOM_51C0");
        assertThat(captured.get().getEventStage()).isEqualTo("EXTERNAL");
        assertThat(captured.get().getEventType()).isEqualTo("EVT_RANDOM_B31D");
        assertThat(captured.get().getObjectType()).isEqualTo("OBJ_RANDOM_57D3");
        assertThat(captured.get().getErrorCode()).isEqualTo("ERR_RANDOM_3012");
        assertThat(captured.get().getRequestedSkill()).isEqualTo("CAP_RANDOM_719CD");

        assertThat(plan.isMatched()).isTrue();
        assertThat(plan.getFlowId()).isEqualTo("flow-random-7f31");
        assertThat(plan.getRuleId()).isEqualTo("rule-random-4b92");
        assertThat(plan.getRequestedSkill()).isEqualTo("CAP_RANDOM_719CD");
        assertThat(plan.getRequiredSkills()).containsExactly("CAP_RANDOM_719CD");
        assertThat(plan.getRoutingPath()).isEqualTo("FLOW_RULE");
    }

    @Test
    void shouldEnrichRetryTaskRequirementContractWithoutChangingAuthoritativeRoutingEvidence() {
        AtomicReference<FlowRuleRuntimeQuery> captured = new AtomicReference<>();
        FlowRuleRoutingRepository repository = query -> {
            captured.set(query);
            FlowRuleRuntimeMatch match = new FlowRuleRuntimeMatch();
            match.setFlowId("FLOW-RANDOM-7F31");
            match.setRuleId("RULE-RANDOM-4B92");
            match.setRequestedSkill("CAP_DIFFERENT_FROM_AUTHORITATIVE_EVIDENCE");
            match.setRequiredSkills(List.of("CAP_DIFFERENT_FROM_AUTHORITATIVE_EVIDENCE"));
            match.setCapabilityRequirementMode("SOURCE_DEFAULT");
            match.setRequiredOperation("ANALYZE");
            match.setSideEffectLevel("NONE");
            match.setCandidatePoolMode("SOURCE_SYSTEM_POOL");
            match.setExplicitActionAuthorizationRequired(Boolean.FALSE);
            match.setRequirementModelVersion(2);
            return Optional.of(match);
        };

        TaskRecord task = syntheticTask();
        task.setMatchedFlowId("flow-random-7f31");
        task.setMatchedRuleId("rule-random-4b92");
        task.setRequestedSkill("CAP_AUTHORITATIVE_RETRY_EVIDENCE");

        FlowRuleRoutingPlan plan = new FlowRuleRoutingService(repository).resolve(task);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequestedSkill()).isEqualTo("CAP_AUTHORITATIVE_RETRY_EVIDENCE");
        assertThat(plan.isMatched()).isTrue();
        assertThat(plan.getFlowId()).isEqualTo("flow-random-7f31");
        assertThat(plan.getRuleId()).isEqualTo("rule-random-4b92");
        assertThat(plan.getRequestedSkill()).isEqualTo("CAP_AUTHORITATIVE_RETRY_EVIDENCE");
        assertThat(plan.getRequiredSkills()).containsExactly("CAP_AUTHORITATIVE_RETRY_EVIDENCE");
        assertThat(plan.getCapabilityRequirementMode()).isEqualTo("SOURCE_DEFAULT");
        assertThat(plan.getRequiredOperation()).isEqualTo("ANALYZE");
        assertThat(plan.getSideEffectLevel()).isEqualTo("NONE");
        assertThat(plan.getCandidatePoolMode()).isEqualTo("SOURCE_SYSTEM_POOL");
        assertThat(plan.getExplicitActionAuthorizationRequired()).isFalse();
        assertThat(plan.getRequirementModelVersion()).isEqualTo(2);
    }

    @Test
    void shouldRemainFailClosedWhenNoPersistedRuleMatchesSyntheticSource() {
        AtomicReference<FlowRuleRuntimeQuery> captured = new AtomicReference<>();
        FlowRuleRoutingRepository repository = query -> {
            captured.set(query);
            return Optional.empty();
        };

        TaskRecord task = syntheticTask();
        task.setRequestedSkill(null);

        FlowRuleRoutingPlan plan = new FlowRuleRoutingService(repository).resolve(task);

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getSourceSystem()).isEqualTo("SRC_RANDOM_8F2A");
        assertThat(captured.get().getRequestedSkill()).isNull();
        assertThat(plan.isMatched()).isFalse();
        assertThat(plan.getRoutingPath()).isEqualTo("FLOW_RULE_REQUIRED_BLOCKED");
        assertThat(plan.getReason())
                .contains("No ACTIVE Flow-owned Dispatch Rule matched")
                .contains("Assign at least one approved Agent to the Flow")
                .doesNotContain("SOURCE_DEFAULT")
                .doesNotContain("Agent Source Coverage")
                .doesNotContain("Operation Profile")
                .doesNotContain("Service Scope");
    }

    @Test
    void shouldApplyPersistedFlowEvidenceWithoutInterpretingSourceName() {
        TaskRecord task = syntheticTask();
        task.setRequiredCapabilities(List.of("CAP_EXISTING_001"));

        FlowRuleRoutingPlan plan = new FlowRuleRoutingPlan();
        plan.setMatched(true);
        plan.setFlowId("flow-random-7f31");
        plan.setRuleId("rule-random-4b92");
        plan.setEventStage("EXTERNAL");
        plan.setRequestedSkill("CAP_RANDOM_719CD");
        plan.setRequiredSkills(List.of("CAP_RANDOM_719CD"));
        plan.setRoutingPath("FLOW_RULE");

        new FlowRuleRoutingService(query -> Optional.empty()).applyToTask(task, plan);

        assertThat(task.getMatchedFlowId()).isEqualTo("flow-random-7f31");
        assertThat(task.getMatchedRuleId()).isEqualTo("rule-random-4b92");
        assertThat(task.getRequestedSkill()).isEqualTo("CAP_RANDOM_719CD");
        assertThat(task.getRoutingPath()).isEqualTo("FLOW_RULE");
        assertThat(task.getRoutingPolicy()).isEqualTo("FLOW_RULE");
        assertThat(task.getRequiredCapabilities()).containsExactly("CAP_EXISTING_001", "CAP_RANDOM_719CD");
    }

    private TaskRecord syntheticTask() {
        TaskRecord task = new TaskRecord();
        task.setTaskId("task-random-c42f");
        task.setTenantId("tenant-random-82a9");
        task.setSourceSystem("SRC_RANDOM_8F2A");
        task.setOriginSourceSystem("EDGE_RANDOM_1A2B");
        task.setTargetSystem("TARGET_RANDOM_51C0");
        task.setEventStage("EXTERNAL");
        task.setEventType("EVT_RANDOM_B31D");
        task.setObjectType("OBJ_RANDOM_57D3");
        task.setErrorCode("ERR_RANDOM_3012");
        task.setRequestedSkill("CAP_RANDOM_719CD");
        return task;
    }
}
