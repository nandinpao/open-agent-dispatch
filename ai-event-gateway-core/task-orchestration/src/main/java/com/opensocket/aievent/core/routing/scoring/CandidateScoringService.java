package com.opensocket.aievent.core.routing.scoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.skill.AgentDispatchSkillEvaluationService;
import com.opensocket.aievent.core.agent.skill.AgentSkillDefinition;
import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationRequest;
import com.opensocket.aievent.core.agent.skill.AgentSkillEvaluationResult;
import com.opensocket.aievent.core.agent.skill.AgentSkillRegistryService;
import com.opensocket.aievent.core.routing.AgentCandidateScore;
import com.opensocket.aievent.core.routing.RoutingPolicy;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.routing.eligibility.RuntimeEligibilityEvaluator;
import com.opensocket.aievent.core.task.TaskRecord;

/**
 * Candidate scoring boundary for current routing.
 *
 * <p>Phase 3-8 moves the former RoutingDecisionService scoring formula here
 * without changing scoreBreakdown keys, final-score clamping, backend eligibility
 * blocking behavior, skill-aware scoring, or skill-version compatibility scoring.</p>
 */
public class CandidateScoringService {
    private final RoutingProperties properties;
    private final RuntimeEligibilityEvaluator runtimeEligibilityEvaluator;
    private final AgentSkillRegistryService skillRegistryService;
    private final AgentDispatchSkillEvaluationService dispatchSkillEvaluationService;

    public CandidateScoringService(RoutingProperties properties,
                                   RuntimeEligibilityEvaluator runtimeEligibilityEvaluator,
                                   AgentSkillRegistryService skillRegistryService,
                                   AgentDispatchSkillEvaluationService dispatchSkillEvaluationService) {
        this.properties = properties;
        this.runtimeEligibilityEvaluator = runtimeEligibilityEvaluator;
        this.skillRegistryService = skillRegistryService;
        this.dispatchSkillEvaluationService = dispatchSkillEvaluationService;
    }

    public AgentCandidateScore score(TaskRecord task, AgentSnapshot agent, RoutingPolicy policy, boolean flowRuleTask) {
        BackendEligibilityScore backendEligibility = evaluateBackendEligibility(task, agent);
        List<String> requiredCapabilities = routingRequiredCapabilities(task, flowRuleTask);
        List<String> effectiveCapabilitiesWork = effectiveCapabilities(agent);
        if (backendEligibility.applied() && !backendEligibility.blockingFailure()) {
            // Backend eligibility is the authority for Admin-managed Dispatch Flow coverage grants.
            // If an Agent has the required Dispatch Flow coverage, the scope's capability bindings should
            // be considered effective for scoring even when the legacy/runtime capability snapshot
            // does not contain those business capabilities.
            effectiveCapabilitiesWork = mergeDistinct(effectiveCapabilitiesWork, requiredCapabilities);
        }
        final List<String> effectiveCapabilities = effectiveCapabilitiesWork;
        List<String> matched = requiredCapabilities.stream()
                .filter(effectiveCapabilities::contains)
                .toList();
        List<String> missing = requiredCapabilities.stream()
                .filter(cap -> !effectiveCapabilities.contains(cap))
                .toList();
        int capabilityScore = requiredCapabilities.isEmpty()
                ? 40
                : (int) Math.round(40.0 * matched.size() / requiredCapabilities.size());
        boolean runtimeAssignable = agent.isAssignable();
        boolean runtimeCapacityFull = runtimeEligibilityEvaluator.isCapacityFull(agent);
        int availabilityScore = runtimeAssignable ? 20 : 0;
        int slotScore = Math.min(10, Math.max(0, agent.getAvailableSlots()) * 5);
        int loadScore = runtimeLoadScore(agent, policy);
        int siteScore = task.getSiteId() != null && task.getSiteId().equals(agent.getSiteId()) ? 10 : 0;
        int healthScore = Math.round(agent.getHealthScore() / 10.0f);
        int policyBonus = policyBonus(policy, missing, siteScore);
        int penalty = runtimePenalty(agent);

        SkillScore skill = evaluateSkillAware(task, agent, flowRuleTask);
        SkillVersionScore skillVersion = evaluateSkillVersionCompatibility(task, agent);
        int backendEligibilityScore = backendEligibility.score();
        int backendEligibilityPenalty = backendEligibility.penalty();
        int skillScore = skill.score();
        int skillPenalty = skill.penalty();
        int skillVersionScore = skillVersion.score();
        int skillVersionPenalty = skillVersion.penalty();
        List<String> matchedDiagnostics = mergeDistinct(mergeDistinct(mergeDistinct(matched, backendEligibility.matchedDiagnostics()), skill.matchedDiagnostics()), skillVersion.matchedDiagnostics());
        List<String> missingDiagnostics = mergeDistinct(mergeDistinct(mergeDistinct(missing, backendEligibility.missingDiagnostics()), skill.missingDiagnostics()), skillVersion.missingDiagnostics());

        int score = Math.max(0, Math.min(100,
                capabilityScore + availabilityScore + slotScore + loadScore + siteScore + healthScore
                        + policyBonus + backendEligibilityScore + skillScore + skillVersionScore - penalty - backendEligibilityPenalty - skillPenalty - skillVersionPenalty));
        String reason = "capability=" + capabilityScore
                + ", availability=" + availabilityScore
                + ", slots=" + slotScore + "(available=" + agent.getAvailableSlots() + ")"
                + ", load=" + loadScore
                + "(current=" + agent.getCurrentTaskCount() + ",reserved=" + agent.getReservedTaskCount()
                + ",utilization=" + agent.getCapacityUtilization() + ")"
                + ", site=" + siteScore
                + ", health=" + healthScore
                + (backendEligibility.applied() ? ", backendEligibility=" + backendEligibilityScore + "(" + backendEligibility.reason() + ")" : "")
                + (skillVersion.applied() ? ", capabilityPolicyVersion=" + skillVersionScore + "(" + skillVersion.reason() + ")" : "")
                + (backendEligibilityPenalty > 0 ? ", backendEligibilityPenalty=" + backendEligibilityPenalty : "")
                + (skill.applied() ? ", capabilityContract=" + skillScore + "(" + skill.reason() + ")" : "")
                + (skillPenalty > 0 ? ", capabilityContractPenalty=" + skillPenalty : "")
                + (skillVersionPenalty > 0 ? ", capabilityPolicyVersionPenalty=" + skillVersionPenalty : "")
                + (penalty > 0 ? ", runtimePenalty=" + penalty + "(outboxPending=" + agent.getOutboxPending()
                + ",recoveryPending=" + agent.getRecoveryPendingAssignments() + ",draining=" + agent.isDraining() + ")" : "")
                + (policyBonus > 0 ? ", policyBonus=" + policyBonus : "")
                + (agent.getRuntimeFailureCount() > 0 ? ", runtimeFailureCount=" + agent.getRuntimeFailureCount() : "");
        boolean hasMissing = !missingDiagnostics.isEmpty() || backendEligibility.blockingFailure() || (skill.applied() && skill.blockingFailure()) || skillVersion.blockingFailure();
        int finalScore = backendEligibility.blockingFailure() ? 0 : (!runtimeAssignable || hasMissing) ? Math.min(score, 49) : score;
        Map<String, Object> scoreBreakdown = new LinkedHashMap<>();
        scoreBreakdown.put("capabilityScore", capabilityScore);
        scoreBreakdown.put("availabilityScore", availabilityScore);
        scoreBreakdown.put("slotScore", slotScore);
        scoreBreakdown.put("loadScore", loadScore);
        scoreBreakdown.put("siteScore", siteScore);
        scoreBreakdown.put("healthScore", healthScore);
        scoreBreakdown.put("policyBonus", policyBonus);
        scoreBreakdown.put("runtimePenalty", penalty);
        scoreBreakdown.put("runtimeAssignable", runtimeAssignable);
        scoreBreakdown.put("agentStatus", agent.getStatus() == null ? "UNKNOWN" : agent.getStatus().name());
        scoreBreakdown.put("runtimeCapacityAvailable", agent.getAvailableSlots() > 0 || agent.getEffectiveTaskCount() < Math.max(1, agent.getMaxConcurrentTasks()));
        scoreBreakdown.put("runtimeCapacityFull", runtimeCapacityFull);
        scoreBreakdown.put("backendEligibilityApplied", backendEligibility.applied());
        scoreBreakdown.put("backendEligibilityScore", backendEligibilityScore);
        scoreBreakdown.put("backendEligibilityPenalty", backendEligibilityPenalty);
        scoreBreakdown.put("backendEligibilityReason", backendEligibility.reason());
        scoreBreakdown.put("backendEligibilityRequiredProfiles", backendEligibility.requiredProfiles());
        scoreBreakdown.put("backendEligibilityApprovedProfiles", backendEligibility.approvedProfiles());
        scoreBreakdown.put("backendEligibilityBlocking", backendEligibility.blockingFailure());
        scoreBreakdown.put("skillApplied", skill.applied());
        scoreBreakdown.put("skillScore", skillScore);
        scoreBreakdown.put("skillPenalty", skillPenalty);
        scoreBreakdown.put("skillVersionApplied", skillVersion.applied());
        scoreBreakdown.put("skillVersionScore", skillVersionScore);
        scoreBreakdown.put("skillVersionPenalty", skillVersionPenalty);
        scoreBreakdown.put("rawScore", score);
        scoreBreakdown.put("finalScore", finalScore);
        scoreBreakdown.put("routingPolicy", policy.name());
        scoreBreakdown.put("routingPath", firstNonBlank(task == null ? null : task.getRoutingPath(), ""));
        scoreBreakdown.put("matchedFlowId", firstNonBlank(task == null ? null : task.getMatchedFlowId(), ""));
        scoreBreakdown.put("matchedRuleId", firstNonBlank(task == null ? null : task.getMatchedRuleId(), ""));
        scoreBreakdown.put("requestedSkill", firstNonBlank(task == null ? null : task.getRequestedSkill(), ""));
        scoreBreakdown.put("matchedCapabilities", matchedDiagnostics);
        scoreBreakdown.put("missingCapabilities", missingDiagnostics);
        scoreBreakdown.put("effectiveCapabilities", effectiveCapabilities);
        scoreBreakdown.put("skillReason", skill.reason());
        scoreBreakdown.put("skillVersionReason", skillVersion.reason());
        scoreBreakdown.put("blockingFailure", backendEligibility.blockingFailure() || skill.blockingFailure() || skillVersion.blockingFailure());
        scoreBreakdown.put("runtime", Map.of(
                "availableSlots", agent.getAvailableSlots(),
                "currentTaskCount", agent.getCurrentTaskCount(),
                "reservedTaskCount", agent.getReservedTaskCount(),
                "capacityUtilization", agent.getCapacityUtilization(),
                "outboxPending", agent.getOutboxPending(),
                "recoveryPendingAssignments", agent.getRecoveryPendingAssignments(),
                "runtimeFailureCount", agent.getRuntimeFailureCount(),
                "draining", agent.isDraining()
        ));
        return new AgentCandidateScore(
                agent.getAgentId(), agent.getOwnerGatewayNodeId(), agent.getAgentSessionId(), agent.getSiteId(),
                agent.getStatus() == null ? null : agent.getStatus().name(),
                finalScore, matchedDiagnostics, missingDiagnostics, reason, immutableNullableMap(scoreBreakdown));
    }

    private BackendEligibilityScore evaluateBackendEligibility(TaskRecord task, AgentSnapshot agent) {
        return BackendEligibilityScore.notApplied();
    }

    private int policyBonus(RoutingPolicy policy, List<String> missing, int siteScore) {
        boolean completeCapability = missing == null || missing.isEmpty();
        return switch (policy) {
            case FLOW_RULE -> completeCapability ? 20 : 0;
            case CAPABILITY_FIRST -> completeCapability ? 10 : 0;
            case LOCAL_FIRST -> siteScore > 0 ? 15 : 0;
            case LOAD_BALANCED -> 5;
            default -> 0;
        };
    }

    private SkillScore evaluateSkillAware(TaskRecord task, AgentSnapshot agent, boolean flowRuleTask) {
        if (!properties.isSkillAwareEnabled() || skillRegistryService == null) {
            return SkillScore.notApplied();
        }
        AgentSkillEvaluationRequest request = skillRequestFor(task, flowRuleTask);
        if (!requiresSkillEvaluation(request)) {
            return SkillScore.notApplied();
        }
        if (dispatchSkillEvaluationService == null) {
            return SkillScore.notApplied();
        }
        AgentSkillEvaluationResult result = dispatchSkillEvaluationService.evaluate(agent, request);
        List<String> matched = result.getMatchedSkillCodes().stream().map(value -> "skill:" + value).toList();
        List<String> missing = result.getMissingRequirements().stream().map(value -> "skill:" + value).toList();
        if (result.isEligible()) {
            int bonus = 20 + Math.min(10, result.getMatchedSkillCodes().size() * 5);
            return new SkillScore(true, Math.min(30, bonus), 0, matched, List.of(),
                    "eligible matched=" + result.getMatchedSkillCodes(), false);
        }
        int penalty = properties.isSkillAwareEnforced() ? 40 : 0;
        return new SkillScore(true, 0, penalty, matched, missing,
                "ineligible missing=" + result.getMissingRequirements(), properties.isSkillAwareEnforced());
    }

    private SkillVersionScore evaluateSkillVersionCompatibility(TaskRecord task, AgentSnapshot agent) {
        if (!properties.isSkillVersionCompatibilityEnabled()) {
            return SkillVersionScore.notApplied();
        }
        Map<String, Integer> required = requiredSkillVersions(task);
        if (required.isEmpty()) {
            return SkillVersionScore.notApplied();
        }
        Map<String, Integer> agentVersions = agentSkillVersions(agent);
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int actual = agentVersions.getOrDefault(entry.getKey(), 0);
            if (actual >= entry.getValue()) {
                matched.add("skillVersion:" + entry.getKey() + ">=" + entry.getValue() + " actual=" + actual);
            } else {
                missing.add("skillVersion:" + entry.getKey() + ">=" + entry.getValue() + " actual=" + actual);
            }
        }
        if (missing.isEmpty()) {
            return new SkillVersionScore(true, Math.min(10, matched.size() * 5), 0, matched, List.of(),
                    "compatible " + matched, false);
        }
        int penalty = properties.isSkillVersionEnforced() ? 40 : 0;
        return new SkillVersionScore(true, 0, penalty, matched, missing,
                "incompatible " + missing, properties.isSkillVersionEnforced());
    }

    private AgentSkillEvaluationRequest skillRequestFor(TaskRecord task, boolean flowRuleTask) {
        List<String> requiredCapabilities = routingRequiredCapabilities(task, flowRuleTask);
        AgentSkillEvaluationRequest request = new AgentSkillEvaluationRequest();
        request.setRequiredCapabilities(requiredCapabilities);
        request.setSiteCode(task.getSiteId());
        KnownSkillMatch known = findKnownSkill(requiredCapabilities, task);
        if (known != null) {
            request.setDomain(known.domain());
            request.setTaskType(known.taskType());
            request.setProvider(known.provider());
            request.setRequiredToolPolicy(known.toolPolicy());
            request.setOperation(known.operation());
        }
        for (String required : requiredCapabilities) {
            if (isOperation(required)) request.setOperation(required);
            if (isToolPolicy(required)) request.setRequiredToolPolicy(required);
            if (required.startsWith("DATA_CLASS:")) request.getDataClasses().add(required.substring("DATA_CLASS:".length()));
            if (required.startsWith("DATA:")) request.getDataClasses().add(required.substring("DATA:".length()));
            if (required.startsWith("PROVIDER:")) request.setProvider(required.substring("PROVIDER:".length()));
            if (required.startsWith("DOMAIN:")) request.setDomain(required.substring("DOMAIN:".length()));
        }
        return request;
    }

    private KnownSkillMatch findKnownSkill(List<String> requiredCapabilities, TaskRecord task) {
        if (skillRegistryService == null) return null;
        List<AgentSkillDefinition> skills = skillRegistryService.search(null, true);
        for (AgentSkillDefinition skill : skills) {
            String skillCode = normalize(skill.getSkillCode());
            if (requiredCapabilities.contains(skillCode) || intersectsNormalized(skill.getTaskTypes(), requiredCapabilities)) {
                return new KnownSkillMatch(
                        skill.getDomain(),
                        firstOrDefault(intersection(skill.getTaskTypes(), requiredCapabilities), skillCode),
                        firstOrDefault(intersection(skill.getProviders(), requiredCapabilities), first(skill.getProviders())),
                        firstOrDefault(intersection(skill.getToolPolicies(), requiredCapabilities), first(skill.getToolPolicies())),
                        firstOrDefault(intersection(skill.getOperations(), requiredCapabilities), null));
            }
        }
        String taskTypeFromTask = taskTypeCode(task);
        if (!blank(taskTypeFromTask)) {
            String taskType = normalize(taskTypeFromTask);
            for (AgentSkillDefinition skill : skills) {
                if (containsNormalized(skill.getTaskTypes(), taskType)) {
                    return new KnownSkillMatch(skill.getDomain(), taskType, first(skill.getProviders()), first(skill.getToolPolicies()), first(skill.getOperations()));
                }
            }
        }
        return null;
    }

    private boolean requiresSkillEvaluation(AgentSkillEvaluationRequest request) {
        return request != null
                && (!blank(request.getDomain())
                || !blank(request.getTaskType())
                || !blank(request.getProvider())
                || !blank(request.getOperation())
                || !blank(request.getRequiredToolPolicy())
                || !request.getDataClasses().isEmpty());
    }

    private List<String> effectiveCapabilities(AgentSnapshot agent) {
        if (dispatchSkillEvaluationService != null) {
            return dispatchSkillEvaluationService.effectiveDispatchCapabilities(agent);
        }
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (agent.getCapabilities() != null) {
            agent.getCapabilities().stream().map(this::normalize).filter(value -> !blank(value)).forEach(capabilities::add);
        }
        Map<String, Object> profile = agent.getCapabilityProfile();
        if (profile != null && !profile.isEmpty()) {
            addCapabilityValues(capabilities, profile.get("supportedTaskTypes"));
            addCapabilityValues(capabilities, profile.get("supportedIssueProviders"));
            addCapabilityValues(capabilities, profile.get("toolPolicies"));
            addCapabilityValues(capabilities, profile.get("domains"));
            addCapabilityValues(capabilities, profile.get("domain"));
            addCapabilityValues(capabilities, profile.get("systems"));
            addCapabilityValues(capabilities, profile.get("system"));
            addCapabilityValues(capabilities, profile.get("skills"));
            Object executorMode = profile.get("executorMode");
            if (executorMode != null && !executorMode.toString().isBlank()) {
                capabilities.add(normalize(executorMode.toString()));
            }
        }
        return capabilities.stream().toList();
    }

    private void addCapabilityValues(Set<String> target, Object value) {
        if (target == null || value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addCapabilityValue(target, item);
            }
            return;
        }
        addCapabilityValue(target, value);
    }

    @SuppressWarnings("unchecked")
    private void addCapabilityValue(Set<String> target, Object item) {
        if (item == null) return;
        if (item instanceof Map<?, ?> map) {
            addCapabilityValue(target, map.get("skillCode"));
            addCapabilityValue(target, map.get("code"));
            addCapabilityValue(target, map.get("name"));
            addCapabilityValue(target, map.get("taskType"));
            addCapabilityValue(target, map.get("provider"));
            addCapabilityValue(target, map.get("operation"));
            addCapabilityValue(target, map.get("toolPolicy"));
            addCapabilityValue(target, map.get("domain"));
            addCapabilityValues(target, map.get("domains"));
            addCapabilityValues(target, map.get("taskTypes"));
            addCapabilityValues(target, map.get("providers"));
            addCapabilityValues(target, map.get("operations"));
            addCapabilityValues(target, map.get("toolPolicies"));
            addCapabilityValues(target, map.get("dataClasses"));
            return;
        }
        String normalized = normalize(item.toString());
        if (!blank(normalized)) {
            target.add(normalized);
        }
    }

    private int runtimeLoadScore(AgentSnapshot agent, RoutingPolicy policy) {
        if (!properties.isLoadAwareScoringEnabled() || agent.isDraining()) {
            return 0;
        }
        double utilization = Math.max(0.0d, Math.min(1.0d, agent.getCapacityUtilization()));
        int maxScore = policy == RoutingPolicy.LOAD_BALANCED ? 20 : 15;
        int utilizationScore = (int) Math.round(maxScore * (1.0d - utilization));
        int effectiveTaskPenalty = Math.max(0, agent.getEffectiveTaskCount() * 2);
        return Math.max(0, utilizationScore - effectiveTaskPenalty);
    }

    private int runtimePenalty(AgentSnapshot agent) {
        int penalty = 0;
        if (agent.isDraining()) {
            penalty += 100;
        }
        penalty += Math.min(20, Math.max(0, agent.getOutboxPending()) * 2);
        penalty += Math.min(20, Math.max(0, agent.getRecoveryPendingAssignments()) * 5);
        if (properties.isRuntimeFailurePenaltyEnabled()) {
            penalty += Math.min(20, Math.max(0, agent.getRuntimeFailureCount()) * 3);
        }
        return penalty;
    }

    private String taskTypeCode(TaskRecord task) {
        return task == null ? null : normalize(task.getEffectiveTaskTypeCode());
    }

    private List<String> normalizedRequiredCapabilities(TaskRecord task) {
        return task == null || task.getRequiredCapabilities() == null ? List.of() : task.getRequiredCapabilities().stream()
                .map(this::normalize)
                .filter(value -> !blank(value))
                .distinct()
                .toList();
    }

    private List<String> routingRequiredCapabilities(TaskRecord task, boolean flowRuleTask) {
        if (flowRuleTask) {
            // Capability is Agent metadata only. Source Flow / Pool-first routing
            // must not turn requestedSkill or required_capabilities_json into a blocking gate.
            return List.of();
        }
        return normalizedRequiredCapabilities(task).stream()
                .filter(value -> !isSkillVersionHint(value))
                .toList();
    }

    private boolean isSkillVersionHint(String value) {
        return !blank(value) && (value.startsWith("SKILL_VERSION:") || value.contains("@"));
    }

    private Map<String, Integer> requiredSkillVersions(TaskRecord task) {
        LinkedHashMap<String, Integer> required = new LinkedHashMap<>();
        for (String capability : normalizedRequiredCapabilities(task)) {
            addRequiredSkillVersion(required, capability);
        }
        return required;
    }

    private void addRequiredSkillVersion(Map<String, Integer> required, String value) {
        if (blank(value)) return;
        String normalized = normalize(value);
        if (normalized.startsWith("SKILL_VERSION:")) {
            String rest = normalized.substring("SKILL_VERSION:".length());
            int split = rest.lastIndexOf(':');
            if (split > 0) {
                putVersion(required, rest.substring(0, split), intValue(rest.substring(split + 1)));
                return;
            }
        }
        int at = normalized.lastIndexOf('@');
        if (at > 0) {
            putVersion(required, normalized.substring(0, at), intValue(normalized.substring(at + 1).replace("V", "")));
        }
    }

    private Map<String, Integer> agentSkillVersions(AgentSnapshot agent) {
        LinkedHashMap<String, Integer> versions = new LinkedHashMap<>();
        if (agent == null || agent.getCapabilityProfile() == null) {
            return versions;
        }
        Map<String, Object> profile = agent.getCapabilityProfile();
        addSkillVersions(versions, profile.get("skillVersions"));
        addSkillVersions(versions, profile.get("skills"));
        return versions;
    }

    private void addSkillVersions(Map<String, Integer> versions, Object raw) {
        if (raw == null) return;
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) addSkillVersions(versions, item);
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            String skillCode = firstNonBlank(map.get("skillCode"), map.get("code"), map.get("name"));
            Integer version = firstInt(map.get("version"), map.get("publishedVersion"), map.get("policyVersion"));
            putVersion(versions, skillCode, version);
            return;
        }
        addRequiredSkillVersion(versions, raw.toString());
    }

    private void putVersion(Map<String, Integer> versions, String skillCode, Integer version) {
        String code = normalize(skillCode);
        if (blank(code) || version == null || version < 1) return;
        versions.merge(code, version, Math::max);
    }

    private Integer firstInt(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            Integer parsed = intValue(value);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && !text.isBlank()) {
            try { return Integer.parseInt(text.trim()); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private String firstNonBlank(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }

    private List<String> mergeDistinct(List<String> left, List<String> right) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (left != null) merged.addAll(left);
        if (right != null) merged.addAll(right);
        return merged.stream().toList();
    }

    private boolean intersectsNormalized(List<String> left, List<String> right) {
        return !intersection(left, right).isEmpty();
    }

    private boolean containsNormalized(List<String> values, String value) {
        if (values == null || blank(value)) return false;
        String normalized = normalize(value);
        return values.stream().map(this::normalize).anyMatch(normalized::equals);
    }

    private List<String> intersection(List<String> left, List<String> right) {
        if (left == null || right == null) return List.of();
        Set<String> rightSet = new LinkedHashSet<>(right.stream().map(this::normalize).filter(value -> !blank(value)).toList());
        return left.stream()
                .map(this::normalize)
                .filter(value -> !blank(value) && rightSet.contains(value))
                .distinct()
                .toList();
    }

    private String first(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        return normalize(values.getFirst());
    }

    private String firstOrDefault(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? normalize(fallback) : normalize(values.getFirst());
    }

    private boolean isOperation(String value) {
        String normalized = normalize(value);
        return Set.of("READ", "ANALYZE", "PROPOSE", "WRITE", "EXECUTE", "ANSWER").contains(normalized);
    }

    private boolean isToolPolicy(String value) {
        String normalized = normalize(value);
        return normalized != null && (normalized.endsWith("_ONLY") || normalized.endsWith("_ALLOWED") || normalized.equals("WRITE_WITH_APPROVAL"));
    }

    private String normalize(String value) {
        return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Map.copyOf rejects null values. Routing score diagnostics intentionally keep optional
     * fields such as skillReason / eligibility reason even when they are absent, so use an
     * unmodifiable LinkedHashMap copy that preserves null diagnostic values instead of
     * crashing assignment before dispatch delivery.
     */
    private Map<String, Object> immutableNullableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private record BackendEligibilityScore(boolean applied, int score, int penalty, List<String> matchedDiagnostics, List<String> missingDiagnostics, String reason, boolean blockingFailure, List<String> requiredProfiles, List<String> approvedProfiles) {
        static BackendEligibilityScore notApplied() {
            return new BackendEligibilityScore(false, 0, 0, List.of(), List.of(),
                    "backend Dispatch Flow Agent Assignment eligibility service not available", false, List.of(), List.of());
        }
    }

    private record SkillScore(boolean applied, int score, int penalty, List<String> matchedDiagnostics, List<String> missingDiagnostics, String reason, boolean blockingFailure) {
        static SkillScore notApplied() {
            return new SkillScore(false, 0, 0, List.of(), List.of(), "skill-aware routing disabled or no known skill requirement", false);
        }
    }

    private record SkillVersionScore(boolean applied, int score, int penalty, List<String> matchedDiagnostics, List<String> missingDiagnostics, String reason, boolean blockingFailure) {
        static SkillVersionScore notApplied() {
            return new SkillVersionScore(false, 0, 0, List.of(), List.of(), "skill-version compatibility disabled or not requested", false);
        }
    }

    private record KnownSkillMatch(String domain, String taskType, String provider, String toolPolicy, String operation) {}
}
