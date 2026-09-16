package com.opensocket.aievent.core.dispatch.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.api.StandardApiErrorCode;
import com.opensocket.aievent.core.api.StandardApiException;
import com.opensocket.aievent.core.routing.RoutingSimulationService;

/**
 * Activation authority for Source Flows.
 *
 * <p>Activation validates durable dispatch configuration, not transient runtime availability.
 * Core saves the requested aggregate inside the current transaction and re-runs the production
 * routing boundary against the just-written ACTIVE aggregate. Structural failures such as an
 * unmatched Rule, a missing target Pool, or a Pool with no active member fail activation.
 * Runtime facts such as an Agent not yet connected, temporarily offline, capacity-full, or in
 * backoff are operational readiness signals and must not make durable Flow configuration
 * impossible to activate. Those conditions remain authoritative at real dispatch time.</p>
 *
 * <p>This distinction is intentional: an Agent can disconnect immediately after activation, so
 * WebSocket/runtime presence cannot be a durable activation invariant. Admin UI and operational
 * readiness surfaces should continue to expose those runtime blockers after activation.</p>
 */
@Service
public class DispatchFlowActivationService {
    private static final Logger log = LoggerFactory.getLogger(DispatchFlowActivationService.class);

    private static final Set<String> STRUCTURAL_ACTIVATION_BLOCKERS = Set.of(
            "TENANT_ID_REQUIRED",
            "SOURCE_SYSTEM_REQUIRED",
            "SOURCE_FLOW_NOT_MATCHED",
            "SOURCE_FLOW_HAS_NO_DEFAULT_POOL",
            "AGENT_POOL_REPOSITORY_UNAVAILABLE",
            "RULE_TARGET_POOL_NOT_FOUND",
            "POOL_HAS_NO_ACTIVE_MEMBER",
            "ASSIGNMENT_ROUTING_DISABLED"
    );

    private final DispatchFlowManagementService flows;
    private final RoutingSimulationService simulation;

    public DispatchFlowActivationService(DispatchFlowManagementService flows,
                                         RoutingSimulationService simulation) {
        this.flows = flows;
        this.simulation = simulation;
    }

    @Transactional
    public DispatchFlowView saveAndValidateActivation(DispatchFlowView request, Integer expectedVersion) {
        if (request == null || !isActive(request.getStatus())) {
            return flows.createOrUpdateFlow(request, expectedVersion);
        }

        DispatchFlowView saved = flows.createOrUpdateFlow(request, expectedVersion);
        List<DispatchFlowRuleView> executableRules = enabledExternalRules(saved);
        if (executableRules.isEmpty()) {
            throw new StandardApiException(StandardApiErrorCode.VALIDATION_ERROR,
                    "Activation requires at least one enabled EXTERNAL Flow Rule. Save as Draft, add a deterministic rule, run Draft Simulation, then activate.");
        }

        List<String> blockers = new ArrayList<>();
        for (DispatchFlowRuleView rule : executableRules) {
            DispatchSimulationResponse result = simulation.simulate(runtimeRequest(saved, rule));
            if (!configurationReady(result)) {
                blockers.add((rule.getRuleCode() == null ? rule.getRuleId() : rule.getRuleCode())
                        + "=" + firstNonBlank(result.getBlockerCode(), "CONFIGURATION_NOT_READY")
                        + (blank(result.getBlockerReason()) ? "" : " (" + result.getBlockerReason() + ")"));
                continue;
            }

            if (!Boolean.TRUE.equals(result.getDispatchable()) && !Boolean.TRUE.equals(result.getManualOnly())) {
                log.warn("dispatch_flow_activation_runtime_readiness_deferred tenantId={} flowId={} ruleId={} targetPoolId={} poolMemberCount={} blockerCode={} blockerReason={}",
                        saved.getTenantId(), saved.getFlowId(), rule.getRuleId(), result.getTargetPoolId(),
                        result.getPoolMemberCount(), firstNonBlank(result.getBlockerCode(), "RUNTIME_NOT_READY"),
                        firstNonBlank(result.getBlockerReason(), "Runtime readiness is currently unavailable."));
            }
        }

        if (!blockers.isEmpty()) {
            throw new StandardApiException(StandardApiErrorCode.CONFLICT,
                    "Dispatch Flow activation was rolled back because durable routing configuration is blocked: "
                            + String.join("; ", blockers));
        }
        return saved;
    }

    private boolean configurationReady(DispatchSimulationResponse result) {
        if (result == null) return false;
        if (Boolean.TRUE.equals(result.getDispatchable()) || Boolean.TRUE.equals(result.getManualOnly())) return true;

        String blocker = normalize(result.getBlockerCode());
        if (STRUCTURAL_ACTIVATION_BLOCKERS.contains(blocker)) return false;

        // Production routing resolved an existing target Pool with at least one active member.
        // Any remaining blocker is runtime/operational readiness and remains enforced when a
        // real Task is assigned; it is not a durable Flow configuration defect.
        return !blank(result.getTargetPoolId()) && result.getPoolMemberCount() > 0;
    }

    private DispatchSimulationRequest runtimeRequest(DispatchFlowView flow, DispatchFlowRuleView rule) {
        DispatchSimulationRequest request = new DispatchSimulationRequest();
        request.setTenantId(flow.getTenantId());
        request.setFlowId(flow.getFlowId());
        request.setSourceSystem(firstNonBlank(rule.getSourceSystem(), flow.getSourceSystem()));
        request.setOriginSourceSystem(rule.getOriginSourceSystem());
        request.setTargetSystem(rule.getTargetSystem());
        request.setEventStage(firstNonBlank(rule.getEventStage(), "EXTERNAL"));
        request.setEventType(wildcardToNull(rule.getEventType()));
        request.setObjectType(wildcardToNull(rule.getObjectType()));
        request.setErrorCode(wildcardToNull(rule.getErrorCode()));
        Object severity = rule.getCondition() == null ? null : rule.getCondition().get("severity");
        request.setSeverity(severity == null ? null : severity.toString());
        request.setAttributes(rule.getCondition() == null ? Map.of() : rule.getCondition());
        request.setIncludeRuntimeSnapshot(Boolean.TRUE);
        request.setEvaluationMode("RUNTIME_READINESS");
        request.setMessage("Transactional activation configuration validation for " + firstNonBlank(flow.getFlowCode(), flow.getFlowId()));
        return request;
    }

    private List<DispatchFlowRuleView> enabledExternalRules(DispatchFlowView flow) {
        if (flow == null || flow.getRules() == null) return List.of();
        return flow.getRules().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .filter(rule -> blank(rule.getEventStage()) || "EXTERNAL".equalsIgnoreCase(rule.getEventStage()))
                .toList();
    }

    private boolean isActive(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return "ACTIVE".equals(value) || "ENABLED".equals(value);
    }

    private String wildcardToNull(String value) {
        if (blank(value) || "*".equals(value.trim())) return null;
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values != null) for (String value : values) if (!blank(value)) return value;
        return null;
    }

    private String normalize(String value) {
        return blank(value) ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
