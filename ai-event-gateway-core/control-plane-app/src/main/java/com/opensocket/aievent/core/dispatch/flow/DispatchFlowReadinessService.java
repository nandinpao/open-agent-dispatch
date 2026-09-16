package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Read-only readiness projection over the canonical Dispatch Simulation path.
 *
 * <p>V38-7A2 deliberately removes the former independent SQL eligibility
 * implementation. Readiness now projects the same DispatchDecisionEngine result
 * used by runtime and simulation: Flow -> Agent Pool membership -> Core-approved
 * Capability -> Agent governance -> Runtime/capacity -> routing score.</p>
 */
@Service
public class DispatchFlowReadinessService {
    private static final Logger log = LoggerFactory.getLogger(DispatchFlowReadinessService.class);

    private final DispatchSimulationApplicationService simulation;

    public DispatchFlowReadinessService(DispatchSimulationApplicationService simulation) {
        this.simulation = simulation;
    }

    public DispatchFlowReadinessResponse dryRun(DispatchFlowReadinessRequest request) {
        DispatchFlowReadinessRequest normalized = normalizeRequest(request);
        DispatchSimulationResponse canonical = simulation.simulate(toSimulationRequest(normalized));
        DispatchFlowReadinessResponse response = project(normalized, canonical);
        if (Boolean.TRUE.equals(response.getDispatchable())) {
            log.info("dispatch_flow_readiness_ready tenantId={} flowId={} ruleId={} selectedAgentId={} authority=DISPATCH_DECISION_ENGINE",
                    response.getTenantId(), response.getFlowId(), response.getRuleId(), response.getSelectedAgentId());
        } else {
            log.warn("dispatch_flow_readiness_blocked tenantId={} flowId={} ruleId={} blockerCode={} reason={} authority=DISPATCH_DECISION_ENGINE",
                    response.getTenantId(), response.getFlowId(), response.getRuleId(), response.getFirstBlockingCode(), response.getFirstBlockingReason());
        }
        return response;
    }

    private DispatchSimulationRequest toSimulationRequest(DispatchFlowReadinessRequest request) {
        DispatchSimulationRequest simulationRequest = new DispatchSimulationRequest();
        simulationRequest.setTenantId(request.getTenantId());
        simulationRequest.setFlowId(request.getFlowId());
        simulationRequest.setSourceSystem(request.getSourceSystem());
        simulationRequest.setOriginSourceSystem(request.getOriginSourceSystem());
        simulationRequest.setTargetSystem(request.getTargetSystem());
        simulationRequest.setEventStage(request.getEventStage());
        simulationRequest.setObjectType(wildcardToNull(request.getObjectType()));
        simulationRequest.setEventType(wildcardToNull(request.getEventType()));
        simulationRequest.setErrorCode(wildcardToNull(request.getErrorCode()));
        simulationRequest.setSeverity(request.getSeverity());
        simulationRequest.setMessage(request.getMessage());
        simulationRequest.setAttributes(request.getAttributes());
        simulationRequest.setIncludeRuntimeSnapshot(Boolean.TRUE);
        simulationRequest.setEvaluationMode(blank(request.getFlowId()) ? "RUNTIME_READINESS" : "DRAFT_SIMULATION");
        return simulationRequest;
    }

    private DispatchFlowReadinessResponse project(
            DispatchFlowReadinessRequest request,
            DispatchSimulationResponse canonical) {
        DispatchFlowReadinessResponse response = new DispatchFlowReadinessResponse();
        response.setTenantId(firstNonBlank(canonical == null ? null : canonical.getTenantId(), request.getTenantId()));
        response.setFlowId(canonical == null ? request.getFlowId() : firstNonBlank(canonical.getMatchedFlowId(), request.getFlowId()));
        response.setRuleId(canonical == null ? null : canonical.getMatchedRuleId());
        response.setSourceSystem(firstNonBlank(canonical == null ? null : canonical.getSourceSystem(), request.getSourceSystem()));
        response.setOriginSourceSystem(request.getOriginSourceSystem());
        response.setTargetSystem(request.getTargetSystem());
        response.setEventStage(firstNonBlank(canonical == null ? null : canonical.getEventStage(), request.getEventStage()));
        response.setObjectType(firstNonBlank(canonical == null ? null : canonical.getObjectType(), request.getObjectType()));
        response.setEventType(firstNonBlank(canonical == null ? null : canonical.getEventType(), request.getEventType()));
        response.setErrorCode(firstNonBlank(canonical == null ? null : canonical.getErrorCode(), request.getErrorCode()));
        response.setGeneratedAt(canonical == null || canonical.getGeneratedAt() == null ? OffsetDateTime.now() : canonical.getGeneratedAt());

        List<String> requiredCapabilities = stringList(canonical == null ? null : canonical.getDiagnostics().get("requiredCapabilities"));
        response.setRequiredSkills(requiredCapabilities);
        response.setRequestedSkill(requiredCapabilities.isEmpty() ? null : requiredCapabilities.getFirst());
        response.setSelectedAgentId(canonical == null ? null : canonical.getSelectedAgentId());

        List<DispatchFlowCandidateAgentView> candidates = new ArrayList<>();
        if (canonical != null) {
            for (DispatchSimulationCandidateView candidate : canonical.getCandidateEvidence()) {
                candidates.add(candidate(candidate, true));
            }
            for (DispatchSimulationCandidateView candidate : canonical.getBlockedCandidates()) {
                candidates.add(candidate(candidate, false));
            }
        }
        response.setCandidateAgents(candidates);

        boolean manual = canonical != null && Boolean.TRUE.equals(canonical.getManualOnly());
        boolean dispatchable = canonical != null && Boolean.TRUE.equals(canonical.getDispatchable());
        response.setReady(dispatchable || manual);
        response.setDispatchable(dispatchable);
        response.setStatus(manual ? "MANUAL_ASSIGNMENT_REQUIRED" : dispatchable ? "READY" : "BLOCKED");
        response.setSummary(canonical == null ? "Canonical readiness projection is unavailable." : canonical.getSummary());
        response.setFirstBlockingCode(dispatchable ? null : canonical == null ? "CANONICAL_READINESS_UNAVAILABLE" : canonical.getBlockerCode());
        response.setFirstBlockingReason(dispatchable ? null : canonical == null ? "Canonical Dispatch Simulation returned no result." : canonical.getBlockerReason());
        response.setChecks(checks(canonical, requiredCapabilities));

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("mode", "V38_7A2_CANONICAL_READINESS_PROJECTION");
        diagnostics.put("dispatchAuthority", "DISPATCH_DECISION_ENGINE");
        diagnostics.put("candidateAuthority", "AGENT_POOL_MEMBERSHIP");
        diagnostics.put("capabilityAuthority", "CORE_APPROVED_AGENT_CAPABILITY");
        diagnostics.put("runtimeReportedCapabilitiesAuthority", false);
        diagnostics.put("legacyFlowAgentReadinessUsed", false);
        if (canonical != null) diagnostics.putAll(canonical.getDiagnostics());
        response.setDiagnostics(diagnostics);
        return response;
    }

    private List<DispatchFlowReadinessCheck> checks(DispatchSimulationResponse canonical, List<String> requiredCapabilities) {
        if (canonical == null) {
            return List.of(check("CANONICAL_READINESS", "BLOCKED", "Canonical Dispatch Simulation returned no result.", true,
                    "Repair the DispatchDecisionEngine path and retry readiness."));
        }
        List<DispatchFlowReadinessCheck> checks = new ArrayList<>();
        checks.add(check("FLOW_RULE_MATCH", blank(canonical.getMatchedFlowId()) ? "BLOCKED" : "PASS",
                blank(canonical.getMatchedFlowId()) ? "No canonical Source Flow / Rule matched." : "Canonical Source Flow / Rule matched.",
                blank(canonical.getMatchedFlowId()), "Review Source Flow matching criteria."));
        checks.add(check("AGENT_POOL_BOUNDARY", blank(canonical.getTargetPoolId()) ? "BLOCKED" : "PASS",
                blank(canonical.getTargetPoolId()) ? "No target Agent Pool was resolved." : "Agent Pool is the canonical candidate boundary.",
                blank(canonical.getTargetPoolId()), "Configure a target/default Agent Pool on the Source Flow."));
        checks.add(check("REQUIRED_CAPABILITY_AUTHORITY", "PASS",
                requiredCapabilities.isEmpty()
                        ? "No Required Capability is configured for this Task."
                        : "Required Capability is enforced from Core APPROVED Agent Capability assignments: " + String.join(", ", requiredCapabilities) + ".",
                false, null));
        boolean manual = Boolean.TRUE.equals(canonical.getManualOnly());
        boolean ready = Boolean.TRUE.equals(canonical.getDispatchable());
        checks.add(check("CANONICAL_AGENT_ELIGIBILITY", ready || manual ? "PASS" : "BLOCKED",
                manual ? "Manual assignment pending; automatic selection is intentionally disabled."
                        : ready ? "At least one Agent Pool member passed canonical eligibility."
                        : firstNonBlank(canonical.getBlockerReason(), "No Agent Pool member passed canonical eligibility."),
                !ready && !manual,
                !ready && !manual ? "Use the canonical blocker code and candidate evidence below; do not repair legacy Flow-Agent assignments." : null));
        return checks;
    }

    private DispatchFlowCandidateAgentView candidate(DispatchSimulationCandidateView source, boolean eligible) {
        DispatchFlowCandidateAgentView view = new DispatchFlowCandidateAgentView();
        view.setAgentId(source.getAgentId());
        view.setRuntimeStatus(source.getStatus());
        view.setAssignmentStatus("POOL_MEMBER");
        view.setApprovalStatus(eligible ? "APPROVED" : "CANONICAL_CHECK_REQUIRED");
        view.setReadinessStatus(eligible ? "READY" : "BLOCKED");
        view.setSkillGrantStatus(contains(source.getBlockingReasons(), "REQUIRED_CAPABILITY_NOT_APPROVED") ? "MISSING" : "APPROVED_OR_NOT_REQUIRED");
        view.setAssignmentActive(true);
        view.setApprovalReady(eligible || !containsAny(source.getBlockingReasons(), "AGENT_NOT_APPROVED", "AGENT_PROFILE_NOT_FOUND", "AGENT_CREDENTIAL_NOT_ACTIVE", "AGENT_CREDENTIAL_EXPIRED"));
        view.setReadinessReady(eligible);
        view.setRequestedSkillGranted(!contains(source.getBlockingReasons(), "REQUIRED_CAPABILITY_NOT_APPROVED"));
        view.setDispatchable(eligible);
        view.setBlockingReasons(source.getBlockingReasons());
        return view;
    }

    private DispatchFlowReadinessCheck check(String code, String status, String message, boolean blocking, String nextAction) {
        return DispatchFlowReadinessCheck.of(code, status, message, blocking, nextAction);
    }

    private DispatchFlowReadinessRequest normalizeRequest(DispatchFlowReadinessRequest request) {
        DispatchFlowReadinessRequest normalized = request == null ? new DispatchFlowReadinessRequest() : request;
        normalized.setTenantId(require(normalized.getTenantId(), "tenantId"));
        normalized.setSourceSystem(normalize(firstNonBlank(normalized.getSourceSystem(), normalized.getOriginSourceSystem())));
        normalized.setOriginSourceSystem(normalizeNullable(normalized.getOriginSourceSystem()));
        normalized.setTargetSystem(normalizeNullable(normalized.getTargetSystem()));
        normalized.setEventStage(firstNonBlank(normalizeNullable(normalized.getEventStage()), "EXTERNAL"));
        normalized.setObjectType(firstNonBlank(normalized.getObjectType(), "*"));
        normalized.setEventType(firstNonBlank(normalized.getEventType(), "*"));
        normalized.setErrorCode(firstNonBlank(normalized.getErrorCode(), "*"));
        return normalized;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(java.util.Objects::nonNull).map(Object::toString).filter(v -> !v.isBlank()).toList();
    }

    private boolean contains(List<String> values, String expected) {
        return values != null && values.stream().anyMatch(value -> expected.equalsIgnoreCase(value));
    }

    private boolean containsAny(List<String> values, String... expected) {
        if (values == null || expected == null) return false;
        for (String item : expected) if (contains(values, item)) return true;
        return false;
    }

    private String wildcardToNull(String value) {
        return blank(value) || "*".equals(value.trim()) ? null : value.trim();
    }

    private String normalizeNullable(String value) { return blank(value) ? null : normalize(value); }
    private String normalize(String value) { return blank(value) ? null : value.trim().replace('-', '_').replace('.', '_').replace(' ', '_').toUpperCase(Locale.ROOT); }
    private String require(String value, String field) { if (blank(value)) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private String firstNonBlank(String... values) { if (values != null) for (String value : values) if (!blank(value)) return value; return null; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
