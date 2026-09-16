package com.opensocket.aievent.core.routing.governance.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignmentStatus;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;

class CapabilityEligibilityEvaluatorAuthorityTest {

    private final CapabilityEligibilityEvaluator evaluator = new CapabilityEligibilityEvaluator();

    @Test
    void runtimeReportedCapabilityCannotGrantQualificationWithoutCoreApproval() {
        DispatchEligibilityShadowContext context = context();
        AgentSnapshot runtime = new AgentSnapshot();
        runtime.setAgentId("agent-a");
        runtime.setCapabilities(List.of("ERP_DIAGNOSE"));
        context.setRuntime(runtime);
        context.setCapabilityAssignments(List.of());

        AgentEligibilityShadowCheck result = evaluator.evaluate(context);

        assertThat(result.isBlocking()).isTrue();
        assertThat(result.getReasonCode()).isEqualTo("REQUIRED_CAPABILITY_NOT_APPROVED");
    }

    @Test
    void coreApprovedCapabilityQualifiesEvenWhenRuntimeDoesNotReportIt() {
        DispatchEligibilityShadowContext context = context();
        AgentSnapshot runtime = new AgentSnapshot();
        runtime.setAgentId("agent-a");
        runtime.setCapabilities(List.of());
        context.setRuntime(runtime);

        AgentCapabilityAssignment approved = new AgentCapabilityAssignment();
        approved.setTenantId("tenant-a");
        approved.setAgentId("agent-a");
        approved.setCapabilityCode("ERP_DIAGNOSE");
        approved.setStatus(AgentCapabilityAssignmentStatus.APPROVED);
        context.setCapabilityAssignments(List.of(approved));

        AgentEligibilityShadowCheck result = evaluator.evaluate(context);

        assertThat(result.isBlocking()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("REQUIRED_CAPABILITY_APPROVED");
    }


    @Test
    void coreApprovedAndRuntimeReportedCapabilityQualifies() {
        DispatchEligibilityShadowContext context = context();
        AgentSnapshot runtime = new AgentSnapshot();
        runtime.setAgentId("agent-a");
        runtime.setCapabilities(List.of("ERP_DIAGNOSE"));
        context.setRuntime(runtime);

        AgentCapabilityAssignment approved = new AgentCapabilityAssignment();
        approved.setTenantId("tenant-a");
        approved.setAgentId("agent-a");
        approved.setCapabilityCode("ERP_DIAGNOSE");
        approved.setStatus(AgentCapabilityAssignmentStatus.APPROVED);
        context.setCapabilityAssignments(List.of(approved));

        AgentEligibilityShadowCheck result = evaluator.evaluate(context);

        assertThat(result.isBlocking()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("REQUIRED_CAPABILITY_APPROVED");
    }

    @Test
    void absentCoreApprovalAndAbsentRuntimeReportRemainBlocked() {
        DispatchEligibilityShadowContext context = context();
        AgentSnapshot runtime = new AgentSnapshot();
        runtime.setAgentId("agent-a");
        runtime.setCapabilities(List.of());
        context.setRuntime(runtime);
        context.setCapabilityAssignments(List.of());

        AgentEligibilityShadowCheck result = evaluator.evaluate(context);

        assertThat(result.isBlocking()).isTrue();
        assertThat(result.getReasonCode()).isEqualTo("REQUIRED_CAPABILITY_NOT_APPROVED");
    }

    private DispatchEligibilityShadowContext context() {
        TaskRequirementEvidence requirement = new TaskRequirementEvidence();
        requirement.setTenantId("tenant-a");
        requirement.setEvidenceId("req-a");
        requirement.setTaskId("task-a");
        requirement.setSourceSystem("ERP");
        requirement.setRequiredCapabilities(List.of("ERP_DIAGNOSE"));

        DispatchEligibilityShadowContext context = new DispatchEligibilityShadowContext();
        context.setRequirement(requirement);
        context.setEvaluatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return context;
    }
}
