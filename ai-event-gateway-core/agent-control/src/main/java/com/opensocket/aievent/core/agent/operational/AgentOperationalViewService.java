package com.opensocket.aievent.core.agent.operational;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentRuntimeDescriptor;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.eligibility.AgentDispatchEligibility;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityCheck;
import com.opensocket.aievent.core.agent.eligibility.DispatchEligibilityService;
import com.opensocket.aievent.core.agent.eligibility.DispatchNextAction;
import com.opensocket.aievent.core.agent.setup.AgentSetupReadinessCheck;
import com.opensocket.aievent.core.agent.setup.AgentSetupReadinessResponse;
import com.opensocket.aievent.core.agent.setup.AgentSetupService;

@Service
public class AgentOperationalViewService {
    private static final Logger log = LoggerFactory.getLogger(AgentOperationalViewService.class);
    private final AgentSetupService setupService;
    private final DispatchEligibilityService eligibilityService;
    private final AgentDirectoryService directoryService;

    public AgentOperationalViewService(AgentSetupService setupService,
                                       DispatchEligibilityService eligibilityService,
                                       AgentDirectoryService directoryService) {
        this.setupService = setupService;
        this.eligibilityService = eligibilityService;
        this.directoryService = directoryService;
    }

    public AgentOperationalView getOperationalView(String agentId) {
        if (blank(agentId)) {
            throw new IllegalArgumentException("agentId is required");
        }
        String normalizedAgentId = agentId.trim();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AgentSetupReadinessResponse setupReadiness = safeSetupReadiness(normalizedAgentId);
        AgentDispatchEligibility dispatchEligibility = safeDispatchEligibility(normalizedAgentId);
        Optional<AgentSnapshot> snapshot = directoryService.findAgent(normalizedAgentId);
        Optional<AgentRuntimeDescriptor> descriptor = directoryService.findRuntimeDescriptor(normalizedAgentId);

        List<AgentOperationalAuthorityCheck> authorityChecks = new ArrayList<>();
        appendSetupChecks(authorityChecks, setupReadiness, normalizedAgentId);
        appendDispatchEligibilityChecks(authorityChecks, dispatchEligibility, normalizedAgentId);
        appendRuntimeAssignableCheck(authorityChecks, snapshot, descriptor, normalizedAgentId);

        AgentOperationalAuthorityCheck firstBlocking = authorityChecks.stream()
                .filter(AgentOperationalAuthorityCheck::isBlocking)
                .findFirst()
                .orElse(null);
        boolean canReceiveTask = firstBlocking == null
                && setupReadiness != null && setupReadiness.isReady()
                && (dispatchEligibility == null || dispatchEligibility.isEligible());

        AgentOperationalView view = new AgentOperationalView();
        view.setAgentId(normalizedAgentId);
        view.setTenantId(setupReadiness == null ? null : setupReadiness.getTenantId());
        view.setCanReceiveTask(canReceiveTask);
        view.setReadinessLevel(canReceiveTask ? "READY" : firstBlocking == null ? "WARN" : "BLOCKED");
        view.setReadinessStatus(canReceiveTask ? "READY" : firstNonBlank(firstBlocking == null ? null : firstBlocking.getCode(), setupReadiness == null ? null : setupReadiness.getStatus(), "NOT_READY"));
        view.setSummary(summary(canReceiveTask, firstBlocking, setupReadiness, dispatchEligibility));
        view.setFirstBlockingCode(firstBlocking == null ? null : firstBlocking.getCode());
        view.setFirstBlockingReason(firstBlocking == null ? null : firstBlocking.getMessage());
        view.setNextAction(firstBlocking == null ? null : firstBlocking.getNextAction());
        view.setAuthorityChecks(authorityChecks);
        view.setRuntime(runtimeSummary(snapshot, descriptor, dispatchEligibility));
        view.setSetupReadiness(setupReadiness);
        view.setDispatchEligibility(dispatchEligibility);
        view.setDiagnostics(diagnostics(setupReadiness, dispatchEligibility, snapshot, descriptor));
        view.setGeneratedAt(now);
        List<String> blockers = authorityChecks.stream().filter(AgentOperationalAuthorityCheck::isBlocking)
                .map(check -> check.getCode() + ":" + firstNonBlank(check.getMessage(), "-"))
                .toList();
        if (canReceiveTask) {
            log.info("agent_operational_readiness_pass tenantId={} agentId={} connectionStatus={} runtimeAssignable={} setupStatus={} dispatchStatus={} checks={}",
                    view.getTenantId(), normalizedAgentId, view.getRuntime().getConnectionStatus(), view.getRuntime().isAssignable(),
                    setupReadiness == null ? null : setupReadiness.getStatus(),
                    dispatchEligibility == null ? null : dispatchEligibility.getDispatchStatus(), authorityChecks.size());
        } else {
            log.warn("agent_operational_readiness_blocked tenantId={} agentId={} connectionStatus={} runtimeAssignable={} setupStatus={} dispatchStatus={} firstBlockingCode={} firstBlockingReason={} blockers={} nextAction={}",
                    view.getTenantId(), normalizedAgentId, view.getRuntime().getConnectionStatus(), view.getRuntime().isAssignable(),
                    setupReadiness == null ? null : setupReadiness.getStatus(),
                    dispatchEligibility == null ? null : dispatchEligibility.getDispatchStatus(),
                    view.getFirstBlockingCode(), view.getFirstBlockingReason(), blockers,
                    view.getNextAction() == null ? null : view.getNextAction().getCode());
        }
        return view;
    }

    private AgentSetupReadinessResponse safeSetupReadiness(String agentId) {
        try {
            return setupService.getSetupReadiness(agentId);
        } catch (RuntimeException ex) {
            log.warn("agent_setup_readiness_unavailable agentId={} exception={} message={}",
                    agentId, ex.getClass().getSimpleName(), ex.getMessage());
            AgentSetupReadinessResponse response = new AgentSetupReadinessResponse();
            response.setAgentId(agentId);
            response.setReady(false);
            response.setStatus("SETUP_READINESS_UNAVAILABLE");
            response.setSummary("Backend setup-readiness contract could not be evaluated: " + ex.getMessage());
            response.setBlockingReasons(List.of("SETUP_READINESS_UNAVAILABLE"));
            response.setChecks(List.of(AgentSetupReadinessCheck.pending(
                    "SETUP_READINESS_UNAVAILABLE",
                    "Setup readiness unavailable",
                    "Backend setup-readiness contract could not be evaluated: " + ex.getMessage(),
                    "Open Diagnostics")));
            response.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return response;
        }
    }

    private AgentDispatchEligibility safeDispatchEligibility(String agentId) {
        try {
            return eligibilityService.evaluateAgent(agentId);
        } catch (RuntimeException ex) {
            log.warn("agent_dispatch_eligibility_unavailable agentId={} exception={} message={}",
                    agentId, ex.getClass().getSimpleName(), ex.getMessage());
            AgentDispatchEligibility result = new AgentDispatchEligibility();
            result.setAgentId(agentId);
            result.setEligible(false);
            result.setDispatchStatus("DISPATCH_ELIGIBILITY_UNAVAILABLE");
            result.setConnectionStatus("UNKNOWN");
            result.setChecks(List.of(DispatchEligibilityCheck.block(
                    "DISPATCH_ELIGIBILITY_UNAVAILABLE",
                    "Backend dispatch eligibility could not be evaluated: " + ex.getMessage())));
            result.setGeneratedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return result;
        }
    }

    private void appendSetupChecks(List<AgentOperationalAuthorityCheck> output,
                                   AgentSetupReadinessResponse setupReadiness,
                                   String agentId) {
        if (setupReadiness == null || setupReadiness.getChecks() == null) {
            output.add(AgentOperationalAuthorityCheck.of(
                    "READINESS",
                    "SETUP_READINESS_UNAVAILABLE",
                    "Setup readiness unavailable",
                    false,
                    true,
                    "Core did not return setup-readiness. Agent dispatch authority cannot be trusted until the backend contract is available.",
                    "CORE_SETUP_READINESS")
                    .withMetadata("agentId", agentId));
            return;
        }
        for (AgentSetupReadinessCheck check : setupReadiness.getChecks()) {
            if (check == null || blank(check.getCode())) continue;
            boolean blocking = !check.isReady();
            AgentOperationalAuthorityCheck authority = AgentOperationalAuthorityCheck.of(
                    categoryForCode(check.getCode()),
                    check.getCode(),
                    firstNonBlank(check.getLabel(), check.getCode()),
                    check.isReady(),
                    blocking,
                    firstNonBlank(check.getDescription(), check.getLabel(), check.getCode()),
                    "CORE_SETUP_READINESS");
            authority.setStatus(firstNonBlank(check.getStatus(), check.isReady() ? "PASS" : "BLOCKED"));
            authority.setMetadata(check.getMetadata());
            if (blocking) {
                authority.setNextAction(nextAction(check.getCode(), firstNonBlank(check.getAction(), actionLabelForCode(check.getCode())), targetForCode(agentId, check.getCode()), "BLOCKING"));
            }
            output.add(authority);
        }
    }

    private void appendDispatchEligibilityChecks(List<AgentOperationalAuthorityCheck> output,
                                                 AgentDispatchEligibility eligibility,
                                                 String agentId) {
        if (eligibility == null || eligibility.getChecks() == null || eligibility.getChecks().isEmpty()) {
            output.add(AgentOperationalAuthorityCheck.of(
                    "DISPATCH",
                    "DISPATCH_ELIGIBILITY_UNAVAILABLE",
                    "Dispatch eligibility unavailable",
                    false,
                    true,
                    "Core did not return dispatch eligibility. Actual routing may disagree with UI readiness.",
                    "CORE_DISPATCH_ELIGIBILITY")
                    .withMetadata("agentId", agentId));
            return;
        }
        for (DispatchEligibilityCheck check : eligibility.getChecks()) {
            if (check == null || blank(check.getCode())) continue;
            boolean ready = !check.isBlocking() && "PASS".equalsIgnoreCase(firstNonBlank(check.getStatus(), ""));
            boolean blocking = check.isBlocking() || "BLOCKED".equalsIgnoreCase(firstNonBlank(check.getStatus(), ""));
            AgentOperationalAuthorityCheck authority = AgentOperationalAuthorityCheck.of(
                    categoryForCode(check.getCode()),
                    check.getCode(),
                    labelForCode(check.getCode()),
                    ready,
                    blocking,
                    firstNonBlank(check.getMessage(), check.getCode()),
                    "CORE_DISPATCH_ELIGIBILITY");
            authority.setStatus(firstNonBlank(check.getStatus(), ready ? "PASS" : blocking ? "BLOCKED" : "INFO"));
            authority.setMetadata(check.getDetails());
            if (blocking) {
                DispatchNextAction action = matchingNextAction(eligibility, check.getCode());
                authority.setNextAction(action == null
                        ? nextAction(check.getCode(), actionLabelForCode(check.getCode()), targetForCode(agentId, check.getCode()), "BLOCKING")
                        : nextAction(action.getAction(), action.getLabel(), targetForCode(agentId, check.getCode()), action.getSeverity()).withPayload("sourcePayload", action.getPayload()));
            }
            output.add(authority);
        }
    }

    private void appendRuntimeAssignableCheck(List<AgentOperationalAuthorityCheck> output,
                                              Optional<AgentSnapshot> snapshot,
                                              Optional<AgentRuntimeDescriptor> descriptor,
                                              String agentId) {
        boolean snapshotAssignable = snapshot.map(AgentSnapshot::isAssignable).orElse(false);
        boolean descriptorAssignable = descriptor.map(value -> !value.isDraining()
                && online(value.getStatus())
                && (value.getAvailableSlots() > 0 || value.getActiveTasks() < Math.max(1, value.getMaxConcurrentTasks())))
                .orElse(false);
        boolean ready = snapshotAssignable || descriptorAssignable;
        AgentOperationalAuthorityCheck check = AgentOperationalAuthorityCheck.of(
                "RUNTIME",
                "RUNTIME_ASSIGNABLE_CANONICAL",
                "Runtime assignable by canonical operational view",
                ready,
                !ready,
                ready
                        ? "Runtime has an online, non-draining state with available capacity."
                        : "Runtime is not assignable according to the canonical operational view. Check online status, draining state, backoff, and capacity.",
                "AGENT_OPERATIONAL_VIEW");
        check.withMetadata("snapshotStatus", snapshot.map(AgentSnapshot::getStatus).map(Enum::name).orElse(null))
                .withMetadata("snapshotAssignable", snapshotAssignable)
                .withMetadata("descriptorStatus", descriptor.map(AgentRuntimeDescriptor::getStatus).map(Enum::name).orElse(null))
                .withMetadata("descriptorAssignable", descriptorAssignable)
                .withMetadata("snapshotAvailableSlots", snapshot.map(AgentSnapshot::getAvailableSlots).orElse(null))
                .withMetadata("descriptorAvailableSlots", descriptor.map(AgentRuntimeDescriptor::getAvailableSlots).orElse(null));
        if (!ready) {
            check.setNextAction(nextAction("CHECK_RUNTIME_ASSIGNABLE", "Open Runtime Connection", targetForCode(agentId, "RUNTIME_CONNECTED"), "BLOCKING"));
        }
        output.add(check);
    }

    private AgentOperationalRuntimeSummary runtimeSummary(Optional<AgentSnapshot> snapshot,
                                                          Optional<AgentRuntimeDescriptor> descriptor,
                                                          AgentDispatchEligibility eligibility) {
        AgentOperationalRuntimeSummary summary = new AgentOperationalRuntimeSummary();
        AgentSnapshot agent = snapshot.orElse(null);
        AgentRuntimeDescriptor runtime = descriptor.orElse(null);
        AgentStatus status = agent == null ? runtime == null ? null : runtime.getStatus() : agent.getStatus();
        summary.setStatus(status == null ? "UNKNOWN" : status.name());
        summary.setConnectionStatus(firstNonBlank(eligibility == null ? null : eligibility.getConnectionStatus(), online(status) ? "ONLINE" : "OFFLINE"));
        summary.setOnline(online(status));
        summary.setAssignable(agent != null ? agent.isAssignable() : runtime != null && !runtime.isDraining() && online(runtime.getStatus())
                && (runtime.getAvailableSlots() > 0 || runtime.getActiveTasks() < Math.max(1, runtime.getMaxConcurrentTasks())));
        summary.setDraining(agent != null ? agent.isDraining() : runtime != null && runtime.isDraining());
        summary.setCurrentTaskCount(agent != null ? agent.getCurrentTaskCount() : runtime == null ? 0 : runtime.getActiveTasks());
        summary.setReservedTaskCount(agent == null ? 0 : agent.getReservedTaskCount());
        summary.setMaxConcurrentTasks(agent != null ? agent.getMaxConcurrentTasks() : runtime == null ? 0 : runtime.getMaxConcurrentTasks());
        summary.setAvailableSlots(agent != null ? agent.getAvailableSlots() : runtime == null ? 0 : runtime.getAvailableSlots());
        summary.setCapacityUtilization(agent != null ? agent.getCapacityUtilization() : runtime == null ? 0.0d : runtime.getCapacityUtilization());
        summary.setRuntimeFailureCount(agent == null ? 0 : agent.getRuntimeFailureCount());
        summary.setLastHeartbeatAt(agent != null ? agent.getLastHeartbeatAt() : runtime == null ? null : runtime.getLastHeartbeatAt());
        summary.setLeaseExpiresAt(agent == null ? null : agent.getLeaseExpiresAt());
        summary.setRuntimeBackoffUntil(agent == null ? null : agent.getRuntimeBackoffUntil());
        summary.setRuntimeBackoffReason(agent == null ? null : agent.getRuntimeBackoffReason());
        summary.setOwnerGatewayNodeId(firstNonBlank(agent == null ? null : agent.getOwnerGatewayNodeId(), runtime == null ? null : runtime.getOwnerGatewayNodeId()));
        summary.setAgentSessionId(firstNonBlank(agent == null ? null : agent.getAgentSessionId(), runtime == null ? null : runtime.getAgentSessionId()));
        return summary;
    }

    private Map<String, Object> diagnostics(AgentSetupReadinessResponse setupReadiness,
                                            AgentDispatchEligibility dispatchEligibility,
                                            Optional<AgentSnapshot> snapshot,
                                            Optional<AgentRuntimeDescriptor> descriptor) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("contract", "GET /admin/agents/{agentId}/operational-view");
        diagnostics.put("sourceOfTruth", "CORE_AGENT_OPERATIONAL_VIEW");
        diagnostics.put("setupReady", setupReadiness == null ? null : setupReadiness.isReady());
        diagnostics.put("setupStatus", setupReadiness == null ? null : setupReadiness.getStatus());
        diagnostics.put("dispatchEligible", dispatchEligibility == null ? null : dispatchEligibility.isEligible());
        diagnostics.put("dispatchStatus", dispatchEligibility == null ? null : dispatchEligibility.getDispatchStatus());
        diagnostics.put("snapshotPresent", snapshot.isPresent());
        diagnostics.put("descriptorPresent", descriptor.isPresent());
        diagnostics.put("runtimeObservationMode", "DIAGNOSTIC_ONLY_FOR_CAPABILITY; AUTHORITATIVE_FOR_CONNECTION_AND_CAPACITY");
        diagnostics.put("legacyUiFallback", false);
        return diagnostics;
    }

    private String summary(boolean canReceiveTask,
                           AgentOperationalAuthorityCheck firstBlocking,
                           AgentSetupReadinessResponse setupReadiness,
                           AgentDispatchEligibility dispatchEligibility) {
        if (canReceiveTask) {
            return "Agent is ready to receive qualified tasks according to the canonical backend operational view.";
        }
        if (firstBlocking != null) {
            return firstBlocking.getMessage();
        }
        if (setupReadiness != null && !blank(setupReadiness.getSummary())) return setupReadiness.getSummary();
        if (dispatchEligibility != null && !blank(dispatchEligibility.getDispatchStatus())) return dispatchEligibility.getDispatchStatus();
        return "Agent operational readiness could not be fully determined.";
    }

    private DispatchNextAction matchingNextAction(AgentDispatchEligibility eligibility, String code) {
        if (eligibility == null || eligibility.getNextActions() == null || eligibility.getNextActions().isEmpty()) return null;
        String normalized = normalize(code);
        return eligibility.getNextActions().stream()
                .filter(action -> action != null && (normalize(action.getAction()).contains(normalized) || normalized.contains(normalize(action.getAction()))))
                .findFirst()
                .orElse(eligibility.getNextActions().get(0));
    }

    private AgentOperationalNextAction nextAction(String code, String label, String target, String severity) {
        return AgentOperationalNextAction.of(firstNonBlank(code, "REVIEW"), firstNonBlank(label, "Review"), target, firstNonBlank(severity, "INFO"));
    }

    private String categoryForCode(String code) {
        String normalized = normalize(code);
        if (normalized.contains("CREDENTIAL")) return "IDENTITY";
        if (normalized.contains("AGENT_PROFILE") || normalized.contains("AGENT_APPROVED") || normalized.contains("RISK")) return "IDENTITY";
        if (normalized.contains("RUNTIME") || normalized.contains("CAPACITY") || normalized.contains("DRAINING")) return "RUNTIME";
        if (normalized.contains("SERVICE_SCOPE") || normalized.contains("QUALIFICATION") || normalized.contains("CERTIFICATION")) return "SERVICE_SCOPE";
        if (normalized.contains("CAPABILITY")) return "CAPABILITY";
        if (normalized.contains("POLICY") || normalized.contains("DISPATCH_RULE")) return "POLICY";
        return "DISPATCH";
    }

    private String labelForCode(String code) {
        String normalized = normalize(code).toLowerCase(Locale.ROOT).replace('_', ' ');
        if (blank(normalized)) return "Dispatch check";
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String actionLabelForCode(String code) {
        String normalized = normalize(code);
        if (normalized.contains("CAPACITY")) return "Wait for Capacity";
        if (normalized.contains("RUNTIME") || normalized.contains("DRAINING")) return "Open Runtime Connection";
        if (normalized.contains("CAPABILITY")) return "Assign Capability";
        if (normalized.contains("SERVICE_SCOPE") || normalized.contains("QUALIFICATION")) return "Assign Required Capability";
        if (normalized.contains("POLICY") || normalized.contains("DISPATCH")) return "Manage Dispatch Rules";
        return "Review";
    }

    private String targetForCode(String agentId, String code) {
        String normalized = normalize(code);
        String tab;
        if (normalized.contains("CAPABILITY") || normalized.contains("SERVICE_SCOPE") || normalized.contains("QUALIFICATION") || normalized.contains("CERTIFICATION")) {
            tab = "capabilities";
        } else if (normalized.contains("POLICY") || normalized.contains("DISPATCH_RULE")) {
            tab = "rules";
        } else if (normalized.contains("CAPACITY")) {
            tab = "tasks";
        } else {
            tab = "connection";
        }
        return "/agents/" + agentId + "?tab=" + tab;
    }

    private boolean online(AgentStatus status) {
        return status != null && status != AgentStatus.OFFLINE && status != AgentStatus.EXPIRED && status != AgentStatus.ERROR;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value.trim();
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
