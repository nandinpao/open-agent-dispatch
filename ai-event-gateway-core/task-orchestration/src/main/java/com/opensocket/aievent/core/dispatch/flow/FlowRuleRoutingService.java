package com.opensocket.aievent.core.dispatch.flow;


import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.domain.TaskIssueSyncPolicy;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.opensocket.aievent.core.dispatch.flow.observation.FlowRuleRoutingObservationDocumentation;
import com.opensocket.aievent.core.dispatch.flow.observation.FlowRuleRoutingObservationDocumentation.HighCardinalityKeyNames;
import com.opensocket.aievent.core.dispatch.flow.observation.FlowRuleRoutingObservationDocumentation.LowCardinalityKeyNames;

/**
 * R6 Flow-owned Rule resolver.
 * R9 requires persisted Flow-owned Rule evidence; R12.9 resolves that evidence
 * from dispatch_flows / dispatch_policies / flow_required_capabilities at runtime.
 * Formal evidence fields are matchedFlowId and matchedRuleId. requestedSkill is required
 * only when the matched rule uses EXPLICIT capability requirements.
 * Synthetic source/capability fallback is disabled; DB-backed Flow Rule lookup is allowed.
 *
 * The first production cut is deliberately legacy-safe: it resolves a deterministic
 * Flow/Rule/Skill plan from the R2-R5 Flow-owned fields already present on the task
 * and stores trace evidence. DB-backed matching of persisted flow_agent_assignments
 * can replace this resolver without changing the downstream routing contract.
 */
@Service
public class FlowRuleRoutingService {
    private static final Logger log = LoggerFactory.getLogger(FlowRuleRoutingService.class);
    private final ObservationRegistry observationRegistry;
    private final FlowRuleRoutingRepository repository;
    private boolean legacyCreateOnCompletedCompatibilityEnabled;

    public FlowRuleRoutingService() {
        this(null, ObservationRegistry.create());
    }

    public FlowRuleRoutingService(FlowRuleRoutingRepository repository) {
        this(repository, ObservationRegistry.create());
    }

    public FlowRuleRoutingService(FlowRuleRoutingRepository repository, ObservationRegistry observationRegistry) {
        this.repository = repository;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.create() : observationRegistry;
    }

    @Autowired
    public FlowRuleRoutingService(
            ObjectProvider<FlowRuleRoutingRepository> repositoryProvider,
            ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        this(repositoryProvider == null ? null : repositoryProvider.getIfAvailable(),
                observationRegistryProvider == null
                        ? ObservationRegistry.create()
                        : observationRegistryProvider.getIfAvailable(ObservationRegistry::create));
    }


    /**
     * Route-B compatibility bridge for Flows that only received V232/V234 migration defaults.
     * An explicitly managed Flow/Rule always wins; this switch only preserves the old
     * create-on-completed behavior until an administrator saves a canonical Flow policy.
     */
    @Value("${adapter-actions.issue.create-on-completed-task:false}")
    void setLegacyCreateOnCompletedCompatibilityEnabled(boolean enabled) {
        this.legacyCreateOnCompletedCompatibilityEnabled = enabled;
    }

    public FlowRuleRoutingPlan resolve(TaskRecord task) {
        return resolve(task, Map.of());
    }

    public FlowRuleRoutingPlan resolve(TaskRecord task, Map<String,Object> matchAttributes) {
        return resolve(task, matchAttributes, false);
    }

    /** C7 Draft-safe simulation path. Production resolution remains ACTIVE/ENABLED-only. */
    public FlowRuleRoutingPlan resolveForSimulation(TaskRecord task, Map<String,Object> matchAttributes) {
        return resolve(task, matchAttributes, true);
    }

    private FlowRuleRoutingPlan resolve(TaskRecord task, Map<String,Object> matchAttributes, boolean simulationMode) {
        Observation observation = FlowRuleRoutingObservationDocumentation.FLOW_RULE_RESOLUTION
                .observation(observationRegistry)
                .lowCardinalityKeyValue(LowCardinalityKeyNames.RESULT.withValue("processing"))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.MATCHED.withValue("false"))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.RESOLUTION_SOURCE.withValue("none"))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.BLOCKING_REASON_CODE.withValue("none"))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TENANT_ID.withValue(valueOrNone(task == null ? null : task.getTenantId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TASK_ID.withValue(valueOrNone(task == null ? null : task.getTaskId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.FLOW_ID.withValue(valueOrNone(task == null ? null : task.getMatchedFlowId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.RULE_ID.withValue(valueOrNone(task == null ? null : task.getMatchedRuleId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.REQUESTED_SKILL.withValue(valueOrNone(task == null ? null : task.getRequestedSkill())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.SOURCE_SYSTEM.withValue(valueOrNone(task == null ? null : task.getSourceSystem())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TARGET_SYSTEM.withValue(valueOrNone(task == null ? null : task.getTargetSystem())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.EVENT_TYPE.withValue(valueOrNone(task == null ? null : task.getEventType())));
        return observation.observe(() -> {
            try {
                return resolveInternal(task, observation, matchAttributes == null ? Map.of() : matchAttributes, simulationMode);
            } catch (RuntimeException ex) {
                low(observation, LowCardinalityKeyNames.RESULT, "error");
                low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, "evaluation_error");
                throw ex;
            }
        });
    }

    private FlowRuleRoutingPlan resolveInternal(TaskRecord task, Observation observation, Map<String,Object> matchAttributes, boolean simulationMode) {
        if (task == null) {
            markNotMatched(observation, "task_missing");
            log.warn("flow_rule_plan_not_matched reason={} taskId=-", "Task is required for R6 Flow Rule routing");
            return FlowRuleRoutingPlan.notMatched("Task is required for R6 Flow Rule routing");
        }
        String eventStage = firstNonBlank(normalize(task.getEventStage()), "EXTERNAL");
        String source = firstNonBlank(normalize(task.getSourceSystem()), normalize(task.getOriginSourceSystem()));
        if (blank(task.getTenantId())) {
            markNotMatched(observation, "tenant_missing");
            return FlowRuleRoutingPlan.notMatched("tenantId is required for Dispatch Flow routing");
        }
        if (blank(source)) {
            markNotMatched(observation, "source_system_missing");
            return FlowRuleRoutingPlan.notMatched("sourceSystem is required for Dispatch Flow routing");
        }
        String target = normalize(task.getTargetSystem());
        String eventType = firstNonBlank(normalize(task.getEventType()), "*");
        String requestedSkill = normalize(task.getRequestedSkill());
        String flowId = cleanIdentifier(task.getMatchedFlowId());
        String ruleId = cleanIdentifier(task.getMatchedRuleId());
        FlowRuleRoutingPlan plan;
        ResolutionOrigin resolutionOrigin;
        if (!blank(flowId) && !blank(ruleId)) {
            resolutionOrigin = ResolutionOrigin.EXISTING;
            plan = new FlowRuleRoutingPlan();
            plan.setMatched(true);
            plan.setFlowId(flowId);
            plan.setRuleId(ruleId);
            plan.setEventStage(eventStage);
            plan.setRuleScope(scopeFor(eventStage));
            plan.setRequestedSkill(requestedSkill);
            plan.setTargetSystem(blank(target) ? null : target);
            plan.setHandoffMode(cleanRoutingToken(task.getHandoffMode()));
            plan.setRoutingPath(firstNonBlank(task.getRoutingPath(), "FLOW_RULE"));
            plan.setTargetPoolId(task.getTargetPoolId());
            plan.setDefaultPoolId(task.getTargetPoolId());
            plan.setSelectionStrategy("LOWEST_LOAD");
            plan.setRequiredSkills(List.of());
            plan.setMatchAttributes(matchAttributes);
            plan.setReason("R9 Flow Rule routing plan resolved from task evidence: flowId=" + flowId
                    + ", ruleId=" + ruleId + ", eventStage=" + eventStage
                    + ", requestedSkill=" + requestedSkill);
            enrichRequirementContractFromPersistedRule(task, plan, eventStage, source, target, eventType, requestedSkill, matchAttributes, simulationMode);
        } else {
            resolutionOrigin = ResolutionOrigin.DATABASE;
            plan = resolvePersistedFlowRule(task, eventStage, source, target, eventType, requestedSkill, matchAttributes, simulationMode);
        }
        if (plan == null || !plan.isMatched()) {
            String blockingCode = plan != null && plan.getReason() != null && plan.getReason().startsWith("Flow Rule DB lookup failed")
                    ? "repository_error"
                    : (plan != null && plan.isAmbiguous() ? "flow_rule_ambiguous" : "flow_rule_no_match");
            markNotMatched(observation, blockingCode);
            low(observation, LowCardinalityKeyNames.RESOLUTION_SOURCE, resolutionOrigin.name().toLowerCase(Locale.ROOT));
            low(observation, LowCardinalityKeyNames.EVENT_STAGE, eventStage);
            high(observation, HighCardinalityKeyNames.SOURCE_SYSTEM, source);
            high(observation, HighCardinalityKeyNames.TARGET_SYSTEM, target);
            high(observation, HighCardinalityKeyNames.EVENT_TYPE, eventType);
            high(observation, HighCardinalityKeyNames.REQUESTED_SKILL, requestedSkill);
            high(observation, HighCardinalityKeyNames.FLOW_ID, flowId);
            high(observation, HighCardinalityKeyNames.RULE_ID, ruleId);
            log.warn("flow_rule_plan_not_matched taskId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} targetSystem={} matchedFlowId={} matchedRuleId={} requestedSkill={} routingPath={} matchResult={} reason={}",
                    task.getTaskId(), source, eventStage, eventType, normalize(task.getObjectType()), normalize(task.getErrorCode()), target, flowId, ruleId, requestedSkill,
                    plan == null ? null : plan.getRoutingPath(), plan == null ? null : plan.getMatchResult(),
                    plan == null ? "No persisted deterministic Flow Rule matched" : plan.getReason());
            if (plan != null) return plan;
            FlowRuleRoutingPlan noMatch = FlowRuleRoutingPlan.notMatched("No deterministic Flow Rule matched; semantic triage required.");
            noMatch.setRoutingPath("FLOW_RULE_NO_MATCH_TRIAGE");
            return noMatch;
        }
        low(observation, LowCardinalityKeyNames.RESULT, "matched");
        low(observation, LowCardinalityKeyNames.MATCHED, "true");
        low(observation, LowCardinalityKeyNames.RESOLUTION_SOURCE, resolutionOrigin.name().toLowerCase(Locale.ROOT));
        low(observation, LowCardinalityKeyNames.EVENT_STAGE, eventStage);
        low(observation, LowCardinalityKeyNames.RULE_SCOPE, plan.getRuleScope());
        low(observation, LowCardinalityKeyNames.ROUTING_PATH, plan.getRoutingPath());
        low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, "none");
        high(observation, HighCardinalityKeyNames.FLOW_ID, plan.getFlowId());
        high(observation, HighCardinalityKeyNames.RULE_ID, plan.getRuleId());
        high(observation, HighCardinalityKeyNames.SOURCE_SYSTEM, source);
        high(observation, HighCardinalityKeyNames.TARGET_SYSTEM, target);
        high(observation, HighCardinalityKeyNames.EVENT_TYPE, eventType);
        high(observation, HighCardinalityKeyNames.REQUESTED_SKILL, plan.getRequestedSkill());
        log.info("flow_rule_plan_resolved taskId={} matchedFlowId={} matchedRuleId={} eventStage={} ruleScope={} sourceSystem={} targetSystem={} eventType={} requestedSkill={} handoffMode={} routingPath={}",
                task.getTaskId(), plan.getFlowId(), plan.getRuleId(), plan.getEventStage(), plan.getRuleScope(), source,
                blank(plan.getTargetSystem()) ? "-" : plan.getTargetSystem(), eventType, plan.getRequestedSkill(), plan.getHandoffMode(), plan.getRoutingPath());
        return plan;
    }

    /**
     * Retry/recovery tasks already carry authoritative Flow/Rule/Skill evidence and must not be
     * re-routed. P2 still needs the persisted requirement contract so shadow evaluation does not
     * silently fall back to LEGACY. This method therefore enriches only the P1/P2 contract fields
     * when the persisted match resolves to the same normalized Flow and Rule identifiers.
     */
    private void enrichRequirementContractFromPersistedRule(
            TaskRecord task,
            FlowRuleRoutingPlan authoritativePlan,
            String eventStage,
            String source,
            String target,
            String eventType,
            String requestedSkill,
            Map<String,Object> matchAttributes,
            boolean simulationMode) {
        FlowRuleRoutingPlan persisted = resolvePersistedFlowRule(
                task, eventStage, source, target, eventType, requestedSkill, matchAttributes, simulationMode);
        if (persisted == null || !persisted.isMatched()) {
            log.debug("flow_rule_requirement_contract_enrichment_skipped taskId={} matchedFlowId={} matchedRuleId={} reason=PERSISTED_RULE_NOT_FOUND",
                    task.getTaskId(), authoritativePlan.getFlowId(), authoritativePlan.getRuleId());
            return;
        }
        if (!sameNormalizedIdentifier(authoritativePlan.getFlowId(), persisted.getFlowId())
                || !sameNormalizedIdentifier(authoritativePlan.getRuleId(), persisted.getRuleId())) {
            log.warn("flow_rule_requirement_contract_enrichment_skipped taskId={} authoritativeFlowId={} authoritativeRuleId={} persistedFlowId={} persistedRuleId={} reason=EVIDENCE_MISMATCH authoritativeRoutingUnchanged=true",
                    task.getTaskId(), authoritativePlan.getFlowId(), authoritativePlan.getRuleId(),
                    persisted.getFlowId(), persisted.getRuleId());
            return;
        }
        authoritativePlan.setCapabilityRequirementMode(
                standardCapabilityRequirementMode(persisted.getCapabilityRequirementMode(), persisted.getRequiredSkills(), persisted.getRequestedSkill()));
        authoritativePlan.setRequiredOperation(persisted.getRequiredOperation());
        authoritativePlan.setSideEffectLevel(firstNonBlank(persisted.getSideEffectLevel(), "NONE"));
        authoritativePlan.setCandidatePoolMode(standardCandidatePoolMode(persisted.getCandidatePoolMode()));
        authoritativePlan.setRoutingStrategy(firstNonBlank(persisted.getRoutingStrategy(), "WEIGHTED_SCORE"));
        authoritativePlan.setTargetPoolId(firstNonBlank(persisted.getTargetPoolId(), authoritativePlan.getTargetPoolId()));
        authoritativePlan.setTargetPoolCode(firstNonBlank(persisted.getTargetPoolCode(), authoritativePlan.getTargetPoolCode()));
        authoritativePlan.setDefaultPoolId(firstNonBlank(persisted.getDefaultPoolId(), authoritativePlan.getDefaultPoolId()));
        authoritativePlan.setSelectionStrategy(firstNonBlank(persisted.getSelectionStrategy(), authoritativePlan.getSelectionStrategy(), "LOWEST_LOAD"));
        authoritativePlan.setSourceDefaultPool(persisted.isSourceDefaultPool());
        authoritativePlan.setExplicitActionAuthorizationRequired(
                persisted.getExplicitActionAuthorizationRequired());
        authoritativePlan.setRequirementModelVersion(persisted.getRequirementModelVersion());
        authoritativePlan.setIssueSyncPolicy(firstNonBlank(persisted.getIssueSyncPolicy(), authoritativePlan.getIssueSyncPolicy(), "OPTIONAL"));
        authoritativePlan.setIssueSyncPolicySource(firstNonBlank(persisted.getIssueSyncPolicySource(), authoritativePlan.getIssueSyncPolicySource(), "SYSTEM_FALLBACK"));
        log.debug("flow_rule_requirement_contract_enriched taskId={} matchedFlowId={} matchedRuleId={} capabilityRequirementMode={} requiredOperation={} sideEffectLevel={} candidatePoolMode={} issueSyncPolicy={} issueSyncPolicySource={} requirementModelVersion={} authoritativeRoutingUnchanged=true",
                task.getTaskId(), authoritativePlan.getFlowId(), authoritativePlan.getRuleId(),
                authoritativePlan.getCapabilityRequirementMode(), authoritativePlan.getRequiredOperation(),
                authoritativePlan.getSideEffectLevel(), authoritativePlan.getCandidatePoolMode(), authoritativePlan.getIssueSyncPolicy(),
                authoritativePlan.getIssueSyncPolicySource(), authoritativePlan.getRequirementModelVersion());
    }

    private String standardCapabilityRequirementMode(String persistedMode, List<String> requiredSkills, String requestedSkill) {
        // In the unified dispatch authority, requestedSkill is matching/trace evidence only.
        // Only Flow-owned required skills/capabilities participate in capability eligibility.
        return hasRequiredCapability(requiredSkills) ? "EXPLICIT" : "NONE";
    }

    private String standardCandidatePoolMode(String persistedMode) {
        // V38-7A1: Agent Pool membership is the canonical candidate boundary.
        // Persisted legacy tokens must never switch runtime back to flow_agent_assignments.
        return "SOURCE_SYSTEM_POOL";
    }

    private boolean hasRequiredCapability(List<String> requiredSkills) {
        return requiredSkills != null && requiredSkills.stream().anyMatch(value -> !blank(value));
    }

    private boolean sameNormalizedIdentifier(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return !blank(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private FlowRuleRoutingPlan resolvePersistedFlowRule(TaskRecord task, String eventStage, String source, String target, String eventType, String requestedSkill, Map<String,Object> matchAttributes, boolean simulationMode) {
        if (task == null) {
            log.warn("flow_rule_db_lookup_skipped reason=TASK_MISSING");
            return null;
        }
        if (repository == null) {
            log.warn("flow_rule_db_lookup_skipped taskId={} tenantId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} requestedSkill={} reason=FLOW_RULE_ROUTING_REPOSITORY_BEAN_MISSING",
                    task.getTaskId(), task.getTenantId(), source, eventStage, eventType, normalize(task.getObjectType()), normalize(task.getErrorCode()), requestedSkill);
            return null;
        }
        FlowRuleRuntimeQuery query = runtimeQuery(task, eventStage, source, target, eventType, requestedSkill, matchAttributes);
        try {
            FlowRuleEvaluation evaluation = simulationMode ? repository.evaluateForSimulation(query) : repository.evaluate(query);
            if (evaluation == null) {
                return FlowRuleRoutingPlan.notMatched("Flow Rule evaluator returned no decision");
            }
            if (evaluation.isAmbiguous()) {
                FlowRuleRoutingPlan plan = FlowRuleRoutingPlan.ambiguous(
                        "FLOW_RULE_SAME_PRIORITY_AMBIGUOUS: minimumPriority=" + evaluation.minimumMatchedPriority()
                                + "; ruleIds=" + evaluation.minimumPriorityMatches().stream().map(c -> c.rule().getRuleId()).toList());
                plan.setEvaluation(evaluation);
                plan.setMatchAttributes(matchAttributes);
                plan.setFlowEvaluationSetRef("PENDING_TASK_MATERIALIZATION");
                return plan;
            }
            if (evaluation.isNoMatch()) {
                String closest = evaluation.closestRules().isEmpty() ? "none"
                        : evaluation.closestRules().stream()
                                .map(c -> c.rule().getRuleId() + " missing=" + c.failedCriteria())
                                .toList().toString();
                String lifecycleScope = simulationMode
                        ? "No enabled deterministic Flow Rule on the explicitly selected Draft/Active Flow matched"
                        : "No ACTIVE deterministic Flow Rule matched";
                FlowRuleRoutingPlan plan = FlowRuleRoutingPlan.notMatched(
                        lifecycleScope + "; semantic triage required. closest=" + closest);
                plan.setRoutingPath("FLOW_RULE_NO_MATCH_TRIAGE");
                plan.setEvaluation(evaluation);
                plan.setMatchAttributes(matchAttributes);
                plan.setFlowEvaluationSetRef("PENDING_TASK_MATERIALIZATION");
                return plan;
            }
            FlowRuleRuntimeMatch match = evaluation.selectedMatch().orElseThrow(
                    () -> new IllegalStateException("MATCHED evaluation must contain exactly one minimum-priority winner"));
            FlowRuleRoutingPlan plan = new FlowRuleRoutingPlan();
            plan.setMatchResult(FlowMatchDecision.MatchResult.MATCHED);
            plan.setEvaluation(evaluation);
            plan.setMatchAttributes(matchAttributes);
            plan.setFlowEvaluationSetRef("PENDING_TASK_MATERIALIZATION");
            plan.setFlowId(match.getFlowId());
            plan.setFlowVersion(match.getFlowVersion());
            plan.setRuleId(match.getRuleId());
            plan.setServiceCode(match.getServiceCode());
            plan.setEventStage(firstNonBlank(match.getEventStage(), eventStage));
            plan.setRuleScope(firstNonBlank(match.getRuleScope(), scopeFor(eventStage)));
            String skill = firstNonBlank(match.getRequestedSkill(), firstOf(match.getRequiredSkills()), requestedSkill);
            plan.setRequestedSkill(skill);
            plan.setTargetSystem(firstNonBlank(match.getTargetSystem(), target));
            plan.setHandoffMode(firstNonBlank(match.getHandoffMode(), cleanRoutingToken(task.getHandoffMode())));
            plan.setRoutingPath("FLOW_RULE");
            plan.setRequiredSkills(match.getRequiredSkills());
            plan.setTargetPoolId(match.getTargetPoolId());
            plan.setTargetPoolCode(match.getTargetPoolCode());
            plan.setDefaultPoolId(match.getDefaultPoolId());
            plan.setSelectionStrategy(firstNonBlank(match.getSelectionStrategy(), "LOWEST_LOAD"));
            plan.setSourceDefaultPool(false);
            plan.setCapabilityRequirementMode(standardCapabilityRequirementMode(match.getCapabilityRequirementMode(), match.getRequiredSkills(), match.getRequestedSkill()));
            plan.setRequiredOperation(match.getRequiredOperation());
            plan.setSideEffectLevel(firstNonBlank(match.getSideEffectLevel(), "NONE"));
            plan.setCandidatePoolMode(standardCandidatePoolMode(match.getCandidatePoolMode()));
            plan.setRoutingStrategy(firstNonBlank(match.getRoutingStrategy(), "WEIGHTED_SCORE"));
            plan.setExplicitActionAuthorizationRequired(match.getExplicitActionAuthorizationRequired());
            plan.setRequirementModelVersion(match.getRequirementModelVersion());
            IssuePolicyResolution issuePolicy = resolveIssuePolicy(match);
            plan.setIssueSyncPolicy(issuePolicy.policy());
            plan.setIssueSyncPolicySource(issuePolicy.source());
            plan.setReason(firstNonBlank(match.getMatchReason(), "A0-R3 deterministic Flow Rule matched"));
            log.info("a0r3_flow_match_plan_resolved taskId={} matchedFlowId={} matchedRuleId={} serviceCode={} priority={} requiredCapabilities={} ruleIssueSyncPolicy={} flowIssueSyncPolicy={} flowIssuePolicyExplicitlyManaged={} issueSyncPolicySource={} effectiveIssueSyncPolicy={} evaluatedRuleCount={}",
                    task.getTaskId(), plan.getFlowId(), plan.getRuleId(), plan.getServiceCode(), match.getPriority(), plan.getRequiredSkills(),
                    match.getRuleIssueSyncPolicy(), match.getFlowIssueSyncPolicy(), match.isFlowIssuePolicyExplicitlyManaged(), plan.getIssueSyncPolicySource(), plan.getIssueSyncPolicy(), evaluation.evaluatedRules().size());
            return plan;
        } catch (RuntimeException ex) {
            log.warn("a0r3_flow_match_failed taskId={} sourceSystem={} eventStage={} eventType={} reason={}",
                    task.getTaskId(), source, eventStage, eventType, ex.getMessage());
            return FlowRuleRoutingPlan.notMatched("Flow Rule DB lookup failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private IssuePolicyResolution resolveIssuePolicy(FlowRuleRuntimeMatch match) {
        String policy = firstNonBlank(match.getIssueSyncPolicy(), "OPTIONAL");
        String source = firstNonBlank(match.getIssueSyncPolicySource(), "SYSTEM_FALLBACK");
        boolean inheritedFlowDefault = "FLOW_DEFAULT".equalsIgnoreCase(source)
                && blank(match.getRuleIssueSyncPolicy());
        boolean migrationOnlyOptional = inheritedFlowDefault
                && "OPTIONAL".equalsIgnoreCase(policy)
                && !match.isFlowIssuePolicyExplicitlyManaged();
        if (legacyCreateOnCompletedCompatibilityEnabled && migrationOnlyOptional) {
            log.warn("dispatch_flow_issue_policy_legacy_compatibility_applied flowId={} ruleId={} persistedFlowPolicy={} effectiveIssueSyncPolicy=REQUIRED source=LEGACY_CREATE_ON_COMPLETED_COMPAT reason=MIGRATION_ONLY_FLOW_WITH_LEGACY_CREATE_ON_COMPLETED",
                    match.getFlowId(), match.getRuleId(), match.getFlowIssueSyncPolicy());
            return new IssuePolicyResolution("REQUIRED", "LEGACY_CREATE_ON_COMPLETED_COMPAT");
        }
        return new IssuePolicyResolution(policy, source);
    }

    private FlowRuleRuntimeQuery runtimeQuery(TaskRecord task, String eventStage, String source, String target, String eventType, String requestedSkill, Map<String,Object> matchAttributes) {
        FlowRuleRuntimeQuery query = new FlowRuleRuntimeQuery();
        query.setTenantId(task.getTenantId());
        query.setFlowId(cleanIdentifier(task.getMatchedFlowId()));
        query.setSourceSystem(source);
        query.setOriginSourceSystem(task.getOriginSourceSystem());
        query.setTargetSystem(target);
        query.setEventStage(eventStage);
        query.setEventType(eventType);
        query.setObjectType(task.getObjectType());
        query.setErrorCode(task.getErrorCode());
        query.setSeverity(task.getSeverity() == null ? null : task.getSeverity().name());
        // requestedSkill remains compatibility/evidence input only; Jdbc evaluator never uses it as a match criterion.
        query.setRequestedSkill(requestedSkill);
        query.setMatchAttributes(matchAttributes);
        return query;
    }

    /**
     * Persist A0-R3 FlowMatchDecision only after the Task row is materialized. Calling this more than
     * once is safe: the JDBC adapter uses deterministic IDs plus a one-authority-decision-per-Task index.
     */
    public void recordAuthoritativeDecision(TaskRecord task, FlowRuleRoutingPlan plan) {
        if (task == null || plan == null || plan.getEvaluation() == null || repository == null) return;
        String eventStage = firstNonBlank(normalize(task.getEventStage()), "EXTERNAL");
        String source = firstNonBlank(normalize(task.getSourceSystem()), normalize(task.getOriginSourceSystem()));
        FlowRuleRuntimeQuery query = runtimeQuery(task, eventStage, source, normalize(task.getTargetSystem()),
                firstNonBlank(normalize(task.getEventType()), "*"), normalize(task.getRequestedSkill()), plan.getMatchAttributes());
        repository.recordAuthoritativeDecision(task.getTaskId(), task.getVersion(), query, plan.getEvaluation());
    }

    public void applyToTask(TaskRecord task, FlowRuleRoutingPlan plan) {
        if (task == null || plan == null || !plan.isMatched()) {
            return;
        }
        task.setMatchedFlowId(plan.getFlowId());
        task.setMatchedRuleId(plan.getRuleId());
        task.setEventStage(firstNonBlank(plan.getEventStage(), task.getEventStage(), "EXTERNAL"));
        task.setRequestedSkill(firstNonBlank(plan.getRequestedSkill(), task.getRequestedSkill()));
        task.setTargetSystem(firstNonBlank(plan.getTargetSystem(), task.getTargetSystem()));
        task.setHandoffMode(firstNonBlank(plan.getHandoffMode(), task.getHandoffMode()));
        task.setTargetPoolId(firstNonBlank(plan.getTargetPoolId(), task.getTargetPoolId()));
        task.setAssignedPoolId(firstNonBlank(plan.getTargetPoolId(), task.getAssignedPoolId()));
        task.setRoutingPath(plan.getRoutingPath());
        task.setRoutingPolicy("FLOW_RULE");
        if (!blank(plan.getServiceCode())) task.setTaskTypeCode(plan.getServiceCode());
        task.setRequiredCapabilities(plan.getRequiredSkills());
        task.setIssueSyncPolicy(issueSyncPolicy(plan.getIssueSyncPolicy()));
        task.setIssueSyncPolicySource(firstNonBlank(plan.getIssueSyncPolicySource(), "SYSTEM_FALLBACK"));
        task.setIssueSyncPolicyInheritanceMode("NONE");
        task.setIssueSyncPolicyInheritedFromTaskId(null);
        log.debug("flow_rule_plan_applied taskId={} matchedFlowId={} matchedRuleId={} targetPoolId={} sourceDefaultPool={} eventStage={} targetSystem={} handoffMode={} routingPath={} issueSyncPolicy={} issueSyncPolicySource={}",
                task.getTaskId(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getTargetPoolId(), plan.isSourceDefaultPool(), task.getEventStage(),
                task.getTargetSystem(), task.getHandoffMode(), task.getRoutingPath(), task.getIssueSyncPolicy(), task.getIssueSyncPolicySource());
    }

    private TaskIssueSyncPolicy issueSyncPolicy(String value) {
        String normalized = firstNonBlank(normalize(value), "OPTIONAL");
        try {
            return TaskIssueSyncPolicy.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            log.warn("flow_rule_issue_sync_policy_invalid value={} fallback=OPTIONAL", value);
            return TaskIssueSyncPolicy.OPTIONAL;
        }
    }

    private void markNotMatched(Observation observation, String blockingReasonCode) {
        low(observation, LowCardinalityKeyNames.RESULT, "not_matched");
        low(observation, LowCardinalityKeyNames.MATCHED, "false");
        low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, blockingReasonCode);
    }

    private void low(Observation observation, LowCardinalityKeyNames key, String value) {
        if (observation != null && value != null && !value.isBlank()) {
            observation.lowCardinalityKeyValue(key.withValue(value));
        }
    }

    private void high(Observation observation, HighCardinalityKeyNames key, String value) {
        if (observation != null) {
            observation.highCardinalityKeyValue(key.withValue(valueOrNone(value)));
        }
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private enum ResolutionOrigin {
        EXISTING,
        DATABASE
    }

    private String scopeFor(String eventStage) {
        return switch (firstNonBlank(eventStage, "EXTERNAL")) {
            case "A2A" -> "A2A_DISPATCH";
            case "RESULT" -> "RESULT_CALLBACK";
            case "ISSUE" -> "ISSUE_TRACKING";
            default -> "EXTERNAL_INTAKE";
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private String firstOf(List<String> values) {
        if (values == null) return null;
        for (String value : values) {
            String normalized = normalize(value);
            if (!blank(normalized)) return normalized;
        }
        return null;
    }

    private String cleanRoutingToken(String value) {
        String normalized = normalize(value);
        return blank(normalized) ? null : normalized;
    }

    /**
     * Flow and Rule identifiers are persisted identifiers, not event/capability
     * tokens. Preserve their original hyphen/dot shape when carrying saved task
     * evidence into the generic routing authority; repositories may compare a
     * normalized projection for backward compatibility, but the runtime plan
     * itself must keep the DB identity stable.
     */
    private String cleanIdentifier(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() || cleaned.toUpperCase(Locale.ROOT).startsWith("NO_") ? null : cleaned;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return normalized.startsWith("NO_") ? null : normalized;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
    private record IssuePolicyResolution(String policy, String source) {}

}
