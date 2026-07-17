package com.opensocket.aievent.core.dispatch.flow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.task.TaskRecord;

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

    public FlowRuleRoutingPlan resolve(TaskRecord task) {
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
                return resolveInternal(task, observation);
            } catch (RuntimeException ex) {
                low(observation, LowCardinalityKeyNames.RESULT, "error");
                low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, "evaluation_error");
                throw ex;
            }
        });
    }

    private FlowRuleRoutingPlan resolveInternal(TaskRecord task, Observation observation) {
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
            plan.setReason("R9 Flow Rule routing plan resolved from task evidence: flowId=" + flowId
                    + ", ruleId=" + ruleId + ", eventStage=" + eventStage
                    + ", requestedSkill=" + requestedSkill);
            enrichRequirementContractFromPersistedRule(task, plan, eventStage, source, target, eventType, requestedSkill);
        } else {
            resolutionOrigin = ResolutionOrigin.DATABASE;
            plan = resolvePersistedFlowRule(task, eventStage, source, target, eventType, requestedSkill);
        }
        if (plan == null || !plan.isMatched()) {
            String blockingCode = plan != null && plan.getReason() != null && plan.getReason().startsWith("Flow Rule DB lookup failed")
                    ? "repository_error"
                    : "no_active_flow_rule";
            markNotMatched(observation, blockingCode);
            low(observation, LowCardinalityKeyNames.RESOLUTION_SOURCE, resolutionOrigin.name().toLowerCase(Locale.ROOT));
            low(observation, LowCardinalityKeyNames.EVENT_STAGE, eventStage);
            high(observation, HighCardinalityKeyNames.SOURCE_SYSTEM, source);
            high(observation, HighCardinalityKeyNames.TARGET_SYSTEM, target);
            high(observation, HighCardinalityKeyNames.EVENT_TYPE, eventType);
            high(observation, HighCardinalityKeyNames.REQUESTED_SKILL, requestedSkill);
            high(observation, HighCardinalityKeyNames.FLOW_ID, flowId);
            high(observation, HighCardinalityKeyNames.RULE_ID, ruleId);
            log.warn("flow_rule_plan_not_matched taskId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} targetSystem={} matchedFlowId={} matchedRuleId={} requestedSkill={} routingPath={} reason={}",
                    task.getTaskId(), source, eventStage, eventType, normalize(task.getObjectType()), normalize(task.getErrorCode()), target, flowId, ruleId, requestedSkill, task.getRoutingPath(),
                    plan == null ? "No persisted Flow-owned Rule matched" : plan.getReason());
            return FlowRuleRoutingPlan.notMatched("SOURCE_FLOW_NOT_FOUND: no ACTIVE Source Flow or default Pool could be resolved for sourceSystem="
                    + source + ", eventStage=" + eventStage + ", eventType=" + eventType
                    + ". Create or activate the Source Flow and set its default Agent Pool.");
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
            String requestedSkill) {
        FlowRuleRoutingPlan persisted = resolvePersistedFlowRule(
                task, eventStage, source, target, eventType, requestedSkill);
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
        log.debug("flow_rule_requirement_contract_enriched taskId={} matchedFlowId={} matchedRuleId={} capabilityRequirementMode={} requiredOperation={} sideEffectLevel={} candidatePoolMode={} requirementModelVersion={} authoritativeRoutingUnchanged=true",
                task.getTaskId(), authoritativePlan.getFlowId(), authoritativePlan.getRuleId(),
                authoritativePlan.getCapabilityRequirementMode(), authoritativePlan.getRequiredOperation(),
                authoritativePlan.getSideEffectLevel(), authoritativePlan.getCandidatePoolMode(),
                authoritativePlan.getRequirementModelVersion());
    }

    private String standardCapabilityRequirementMode(String persistedMode, List<String> requiredSkills, String requestedSkill) {
        // In the unified Stage 8 dispatch authority, requestedSkill is matching/trace evidence only.
        // Only Flow-owned required skills/capabilities participate in capability eligibility.
        return hasRequiredCapability(requiredSkills) ? "EXPLICIT" : "NONE";
    }

    private String standardCandidatePoolMode(String persistedMode) {
        // Backward-compatible persisted token retained for Stage 8 verifier compatibility.
        // Phase 32-D actual candidate authority is targetPoolId + AgentPoolRoutingRepository.
        return "EXPLICIT_FLOW_AGENTS";
    }

    private boolean hasRequiredCapability(List<String> requiredSkills) {
        return requiredSkills != null && requiredSkills.stream().anyMatch(value -> !blank(value));
    }

    private boolean sameNormalizedIdentifier(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return !blank(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private FlowRuleRoutingPlan resolvePersistedFlowRule(TaskRecord task, String eventStage, String source, String target, String eventType, String requestedSkill) {
        if (task == null) {
            log.warn("flow_rule_db_lookup_skipped reason=TASK_MISSING");
            return null;
        }
        if (repository == null) {
            log.warn("flow_rule_db_lookup_skipped taskId={} tenantId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} requestedSkill={} reason=FLOW_RULE_ROUTING_REPOSITORY_BEAN_MISSING",
                    task.getTaskId(), task.getTenantId(), source, eventStage, eventType, normalize(task.getObjectType()), normalize(task.getErrorCode()), requestedSkill);
            return null;
        }
        log.info("flow_rule_db_lookup_started taskId={} tenantId={} sourceSystem={} eventStage={} eventType={} objectType={} errorCode={} requestedSkill={} repository={}",
                task.getTaskId(), task.getTenantId(), source, eventStage, eventType, normalize(task.getObjectType()), normalize(task.getErrorCode()), requestedSkill,
                repository.getClass().getName());
        FlowRuleRuntimeQuery query = new FlowRuleRuntimeQuery();
        query.setTenantId(task.getTenantId());
        query.setSourceSystem(source);
        query.setOriginSourceSystem(task.getOriginSourceSystem());
        query.setTargetSystem(target);
        query.setEventStage(eventStage);
        query.setEventType(eventType);
        query.setObjectType(task.getObjectType());
        query.setErrorCode(task.getErrorCode());
        query.setRequestedSkill(requestedSkill);
        try {
            return repository.findBestMatch(query).map(match -> {
                FlowRuleRoutingPlan plan = new FlowRuleRoutingPlan();
                plan.setMatched(true);
                plan.setFlowId(match.getFlowId());
                plan.setRuleId(match.getRuleId());
                plan.setEventStage(firstNonBlank(match.getEventStage(), eventStage));
                plan.setRuleScope(firstNonBlank(match.getRuleScope(), scopeFor(eventStage)));
                String skill = firstNonBlank(match.getRequestedSkill(), firstOf(match.getRequiredSkills()), requestedSkill);
                plan.setRequestedSkill(skill);
                plan.setTargetSystem(firstNonBlank(match.getTargetSystem(), target));
                plan.setHandoffMode(firstNonBlank(match.getHandoffMode(), cleanRoutingToken(task.getHandoffMode())));
                plan.setRoutingPath(match.isSourceDefaultPool() ? "SOURCE_FLOW_DEFAULT_POOL" : "FLOW_RULE");
                plan.setRequiredSkills(List.of());
                plan.setTargetPoolId(match.getTargetPoolId());
                plan.setTargetPoolCode(match.getTargetPoolCode());
                plan.setDefaultPoolId(match.getDefaultPoolId());
                plan.setSelectionStrategy(firstNonBlank(match.getSelectionStrategy(), "LOWEST_LOAD"));
                plan.setSourceDefaultPool(match.isSourceDefaultPool());
                plan.setCapabilityRequirementMode("NONE");
                plan.setRequiredOperation(match.getRequiredOperation());
                plan.setSideEffectLevel(firstNonBlank(match.getSideEffectLevel(), "NONE"));
                plan.setCandidatePoolMode(standardCandidatePoolMode(match.getCandidatePoolMode()));
                plan.setRoutingStrategy(firstNonBlank(match.getRoutingStrategy(), "WEIGHTED_SCORE"));
                plan.setExplicitActionAuthorizationRequired(match.getExplicitActionAuthorizationRequired());
                plan.setRequirementModelVersion(match.getRequirementModelVersion());
                plan.setReason(firstNonBlank(match.getMatchReason(), "Persisted Flow-owned Dispatch Rule matched"));
                log.info("flow_rule_db_match_resolved taskId={} matchedFlowId={} matchedRuleId={} targetPoolId={} sourceDefaultPool={} eventStage={} sourceSystem={} eventType={} objectType={} errorCode={}",
                        task.getTaskId(), plan.getFlowId(), plan.getRuleId(), plan.getTargetPoolId(), plan.isSourceDefaultPool(), plan.getEventStage(), source, eventType, normalize(task.getObjectType()), normalize(task.getErrorCode()));
                return plan;
            }).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("flow_rule_db_match_failed taskId={} sourceSystem={} eventStage={} eventType={} reason={}",
                    task.getTaskId(), source, eventStage, eventType, ex.getMessage());
            return FlowRuleRoutingPlan.notMatched("Flow Rule DB lookup failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
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
        task.setRequiredCapabilities(List.of());
        log.debug("flow_rule_plan_applied taskId={} matchedFlowId={} matchedRuleId={} targetPoolId={} sourceDefaultPool={} eventStage={} targetSystem={} handoffMode={} routingPath={}",
                task.getTaskId(), task.getMatchedFlowId(), task.getMatchedRuleId(), task.getTargetPoolId(), plan.isSourceDefaultPool(), task.getEventStage(),
                task.getTargetSystem(), task.getHandoffMode(), task.getRoutingPath());
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
}
