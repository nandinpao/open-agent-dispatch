package com.opensocket.aievent.core.routing.governance.eligibility;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.routing.governance.RequirementDecisionStatus;
import com.opensocket.aievent.core.routing.governance.TaskRequirementEvidence;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Stage 8 direct-dispatch Agent eligibility evaluator.
 *
 * The standard path is intentionally small: required Capability only when the
 * Flow Rule asks for it, Agent profile/credential approval, runtime readiness,
 * and capacity. It never loads parallel dispatch governance records.
 */
@Service
public class GenericDispatchEligibilityService {
    private static final Logger log = LoggerFactory.getLogger(GenericDispatchEligibilityService.class);
    private static final String ACTOR = "STAGE8_DIRECT_ELIGIBILITY";

    private final AgentAssignmentService assignmentService;
    private final AgentGovernanceService agentGovernanceService;
    private final List<DispatchEligibilityShadowEvaluator> evaluators;

    public GenericDispatchEligibilityService(
            AgentAssignmentService assignmentService,
            AgentGovernanceService agentGovernanceService,
            CapabilityEligibilityEvaluator capability,
            AgentProfileEligibilityEvaluator agentProfile,
            RuntimeEligibilityEvaluator runtime,
            CapacityEligibilityEvaluator capacity) {
        this.assignmentService = assignmentService;
        this.agentGovernanceService = agentGovernanceService;
        this.evaluators = List.of(capability, agentProfile, runtime, capacity);
    }

    public TaskAgentEligibilityShadowComparison evaluateCandidateAuthoritatively(
            TaskRecord task, TaskRequirementEvidence requirement, AgentSnapshot runtime, String agentId,
            OffsetDateTime evaluatedAt) {
        DispatchEligibilityShadowContext context = hydrate(task, requirement, runtime, agentId, false,
                false, null, evaluatedAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : evaluatedAt);
        return evaluate(context);
    }

    private DispatchEligibilityShadowContext hydrate(TaskRecord task,
                                                       TaskRequirementEvidence requirement,
                                                       AgentSnapshot runtime,
                                                       String agentId,
                                                       boolean legacyCandidate,
                                                       boolean legacyEligible,
                                                       Integer legacyScore,
                                                       OffsetDateTime now) {
        DispatchEligibilityShadowContext context = new DispatchEligibilityShadowContext();
        context.setTask(task);
        context.setRequirement(requirement);
        context.setRuntime(runtime);
        context.setLegacyCandidate(legacyCandidate);
        context.setLegacyEligible(legacyEligible);
        context.setLegacyScore(legacyScore);
        context.setEvaluatedAt(now);
        context.setCapabilityAssignments(assignmentService.findAgentCapabilities(agentId));
        context.setAgentProfile(safeProfile(agentId));
        return context;
    }

    private AgentProfile safeProfile(String agentId) {
        try { return agentGovernanceService.getProfile(agentId); }
        catch (RuntimeException ex) { return null; }
    }

    private TaskAgentEligibilityShadowComparison evaluate(DispatchEligibilityShadowContext context) {
        List<AgentEligibilityShadowCheck> checks = new ArrayList<>();
        boolean requirementBlocked = context.getRequirement().getDecisionStatus() == RequirementDecisionStatus.BLOCKED;
        if (requirementBlocked) {
            checks.add(AgentEligibilityShadowCheck.of("REQUIREMENT_RESOLUTION", EligibilityShadowCheckOutcome.BLOCK,
                    firstNonBlank(context.getRequirement().getReasonCode(), "DIRECT_REQUIREMENT_BLOCKED"),
                    "Flow-owned requirement resolution blocked this task before Agent eligibility."));
        }
        boolean evaluatorFailed = false;
        for (DispatchEligibilityShadowEvaluator evaluator : evaluators) {
            try {
                checks.add(evaluator.evaluate(context));
            } catch (RuntimeException ex) {
                evaluatorFailed = true;
                checks.add(AgentEligibilityShadowCheck.of(evaluator.code(), EligibilityShadowCheckOutcome.BLOCK,
                        "DIRECT_ELIGIBILITY_EVALUATOR_FAILED", "Direct dispatch eligibility evaluator failed closed.")
                        .withDetail("exception", ex.getClass().getSimpleName())
                        .withDetail("message", safeMessage(ex)));
            }
        }
        boolean eligible = !requirementBlocked && !evaluatorFailed
                && checks.stream().noneMatch(AgentEligibilityShadowCheck::isBlocking);
        List<String> blockers = checks.stream().filter(AgentEligibilityShadowCheck::isBlocking)
                .map(AgentEligibilityShadowCheck::getReasonCode).filter(value -> !blank(value)).distinct().toList();

        TaskAgentEligibilityShadowComparison comparison = new TaskAgentEligibilityShadowComparison();
        comparison.setTenantId(context.getRequirement().getTenantId());
        comparison.setComparisonId("direct-eligibility-" + UUID.randomUUID());
        comparison.setRequirementEvidenceId(context.getRequirement().getEvidenceId());
        comparison.setTaskId(context.getRequirement().getTaskId());
        comparison.setMatchedFlowId(context.getRequirement().getMatchedFlowId());
        comparison.setMatchedRuleId(context.getRequirement().getMatchedRuleId());
        comparison.setSourceSystem(context.getRequirement().getSourceSystem());
        comparison.setAgentId(context.agentId());
        comparison.setShadowResolutionMode(context.getRequirement().getResolutionMode());
        comparison.setLegacyCandidate(context.isLegacyCandidate());
        comparison.setLegacyEligible(context.isLegacyEligible());
        comparison.setLegacyScore(context.getLegacyScore());
        comparison.setShadowEligible(eligible);
        comparison.setDifferenceType(difference(context.isLegacyEligible(), eligible, requirementBlocked, evaluatorFailed));
        comparison.setBlockingReasonCodes(blockers);
        comparison.setChecks(checks);
        comparison.setEvaluatorVersion(12);
        comparison.setCreatedAt(context.getEvaluatedAt());
        comparison.setCreatedBy(ACTOR);
        comparison.validate();

        List<String> checkSummary = checks.stream()
                .map(check -> check.getEvaluatorCode() + ":" + check.getOutcome() + ":" + firstNonBlank(check.getReasonCode(), "-") )
                .toList();
        if (eligible) {
            log.info("direct_dispatch_eligibility_pass tenantId={} taskId={} flowId={} ruleId={} sourceSystem={} agentId={} resolutionMode={} candidatePoolMode={} requiredCapabilities={} runtimeStatus={} checks={}",
                    context.getRequirement().getTenantId(), context.getRequirement().getTaskId(),
                    context.getRequirement().getMatchedFlowId(), context.getRequirement().getMatchedRuleId(),
                    context.getRequirement().getSourceSystem(), context.agentId(),
                    context.getRequirement().getResolutionMode(), context.getRequirement().getCandidatePoolMode(),
                    context.getRequirement().getRequiredCapabilities(),
                    context.getRuntime() == null || context.getRuntime().getStatus() == null ? null : context.getRuntime().getStatus().name(),
                    checkSummary);
        } else {
            log.warn("direct_dispatch_eligibility_blocked tenantId={} taskId={} flowId={} ruleId={} sourceSystem={} agentId={} resolutionMode={} candidatePoolMode={} requiredCapabilities={} runtimeStatus={} blockers={} checks={}",
                    context.getRequirement().getTenantId(), context.getRequirement().getTaskId(),
                    context.getRequirement().getMatchedFlowId(), context.getRequirement().getMatchedRuleId(),
                    context.getRequirement().getSourceSystem(), context.agentId(),
                    context.getRequirement().getResolutionMode(), context.getRequirement().getCandidatePoolMode(),
                    context.getRequirement().getRequiredCapabilities(),
                    context.getRuntime() == null || context.getRuntime().getStatus() == null ? null : context.getRuntime().getStatus().name(),
                    blockers, checkSummary);
        }
        return comparison;
    }

    private EligibilityShadowDifferenceType difference(boolean previousEligible,
                                                       boolean directEligible,
                                                       boolean requirementBlocked,
                                                       boolean evaluatorFailed) {
        if (evaluatorFailed) return EligibilityShadowDifferenceType.SHADOW_EVALUATION_FAILED;
        if (requirementBlocked) return EligibilityShadowDifferenceType.REQUIREMENT_BLOCKED;
        if (previousEligible && directEligible) return EligibilityShadowDifferenceType.EQUIVALENT_ELIGIBLE;
        if (!previousEligible && !directEligible) return EligibilityShadowDifferenceType.EQUIVALENT_BLOCKED;
        if (previousEligible) return EligibilityShadowDifferenceType.LEGACY_ONLY_ELIGIBLE;
        return EligibilityShadowDifferenceType.SHADOW_ONLY_ELIGIBLE;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String firstNonBlank(String first, String fallback) { return blank(first) ? fallback : first; }
    private static String safeMessage(Exception ex) {
        return ex == null || ex.getMessage() == null ? "-" : ex.getMessage().replace('\n', ' ').replace('\r', ' ');
    }
}
