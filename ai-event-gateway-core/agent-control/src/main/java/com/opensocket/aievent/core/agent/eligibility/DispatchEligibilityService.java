package com.opensocket.aievent.core.agent.eligibility;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentQuery;
import com.opensocket.aievent.core.agent.AgentRuntimeDescriptor;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.AgentStatus;
import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureTrustStatus;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeBinding;
import com.opensocket.aievent.core.agent.assignment.AgentRuntimeFeatureTrust;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignmentStatus;
import com.opensocket.aievent.core.agent.assignment.AgentCapabilityAssignment;
import com.opensocket.aievent.core.agent.governance.AgentApprovalStatus;
import com.opensocket.aievent.core.agent.governance.AgentCredentialStatus;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceService;
import com.opensocket.aievent.core.task.TaskRecord;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.opensocket.aievent.core.agent.eligibility.observation.DispatchEligibilityObservationDocumentation;
import com.opensocket.aievent.core.agent.eligibility.observation.DispatchEligibilityObservationDocumentation.HighCardinalityKeyNames;
import com.opensocket.aievent.core.agent.eligibility.observation.DispatchEligibilityObservationDocumentation.LowCardinalityKeyNames;

@Service("agentDispatchEligibilityService")
public class DispatchEligibilityService {
    private static final Logger log = LoggerFactory.getLogger(DispatchEligibilityService.class);
    private static final int DEFAULT_LIMIT = 500;

    private final AgentDirectoryService agentDirectoryService;
    private final AgentAssignmentService assignmentService;
    private final AgentGovernanceService governanceService;
    private final ObservationRegistry observationRegistry;

    public DispatchEligibilityService(AgentDirectoryService agentDirectoryService,
                                      AgentAssignmentService assignmentService,
                                      AgentGovernanceService governanceService) {
        this(agentDirectoryService, assignmentService, governanceService, ObservationRegistry.create());
    }

    @Autowired
    public DispatchEligibilityService(AgentDirectoryService agentDirectoryService,
                                      AgentAssignmentService assignmentService,
                                      AgentGovernanceService governanceService,
                                      ObservationRegistry observationRegistry) {
        this.agentDirectoryService = agentDirectoryService;
        this.assignmentService = assignmentService;
        this.governanceService = governanceService;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.create() : observationRegistry;
    }

    public AgentDispatchEligibility evaluateAgent(String agentId) {
        return evaluateAgent(agentId, null);
    }

    public AgentDispatchEligibility evaluateAgent(String agentId, TaskRecord task) {
        Observation observation = DispatchEligibilityObservationDocumentation.AGENT_EVALUATION
                .observation(observationRegistry)
                .lowCardinalityKeyValue(LowCardinalityKeyNames.RESULT.withValue("processing"))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.BLOCKING_REASON_CODE.withValue("none"))
                .highCardinalityKeyValue(HighCardinalityKeyNames.AGENT_ID.withValue(valueOrNone(agentId)))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TASK_ID.withValue(valueOrNone(task == null ? null : task.getTaskId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TASK_TYPE.withValue(valueOrNone(task == null ? null : task.getEffectiveTaskTypeCode())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.SOURCE_SYSTEM.withValue(valueOrNone(task == null ? null : task.getSourceSystem())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.FLOW_ID.withValue(valueOrNone(task == null ? null : task.getMatchedFlowId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.RULE_ID.withValue(valueOrNone(task == null ? null : task.getMatchedRuleId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.REQUESTED_SKILL.withValue(valueOrNone(task == null ? null : task.getRequestedSkill())));
        return observation.observe(() -> {
            try {
                return evaluateAgentObserved(agentId, task, observation);
            } catch (RuntimeException ex) {
                low(observation, LowCardinalityKeyNames.RESULT, "error");
                low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, "evaluation_error");
                throw ex;
            }
        });
    }

    private AgentDispatchEligibility evaluateAgentObserved(String agentId, TaskRecord task, Observation observation) {
        OffsetDateTime now = now();
        Optional<AgentSnapshot> snapshot = safeAgentSnapshot(agentId);
        Optional<AgentRuntimeDescriptor> runtimeDescriptor = agentDirectoryService.findRuntimeDescriptor(agentId);
        Optional<AgentProfile> profile = safeAgentProfile(agentId);
        TaskDispatchRequirements requirements = task == null ? null : resolveTaskRequirements(task);
        List<DispatchEligibilityCheck> checks = new ArrayList<>();
        List<DispatchNextAction> nextActions = new ArrayList<>();

        evaluateProfile(agentId, profile, checks, nextActions);
        evaluateCredential(profile, checks, nextActions);
        evaluateRuntime(agentId, snapshot, runtimeDescriptor, checks, nextActions);
        evaluateRuntimeBinding(agentId, checks, nextActions);
        evaluateStandardFlowRequirementResolution(requirements, checks);
        evaluateCapabilityAssignments(agentId, requirements, now, checks, nextActions);
        evaluateCapacity(snapshot, runtimeDescriptor, checks, nextActions);

        boolean blocked = checks.stream().anyMatch(DispatchEligibilityCheck::isBlocking);
        boolean warned = checks.stream().anyMatch(check -> "WARN".equalsIgnoreCase(check.getStatus()));
        AgentDispatchEligibility result = new AgentDispatchEligibility();
        result.setAgentId(agentId);
        result.setTaskId(task == null ? null : task.getTaskId());
        result.setTaskType(task == null ? null : task.getEffectiveTaskTypeCode());
        result.setEligible(!blocked);
        result.setDispatchStatus(blocked ? "BLOCKED" : warned ? "LIMITED" : "ELIGIBLE");
        result.setConnectionStatus(connectionStatus(snapshot, runtimeDescriptor));
        result.setApprovedProfiles(List.of());
        result.setRequiredProfiles(List.of());
        result.setRuntimeDescriptor(runtimeDescriptor.orElse(null));
        result.setChecks(checks);
        result.setNextActions(nextActions);
        result.setGeneratedAt(now);
        List<String> blockers = checks.stream().filter(DispatchEligibilityCheck::isBlocking)
                .map(check -> check.getCode() + ":" + firstNonBlank(check.getMessage(), "-"))
                .toList();
        if (blocked) {
            log.warn("agent_direct_dispatch_blocked agentId={} tenantId={} taskId={} taskType={} connectionStatus={} requiredCapabilities={} blockers={} nextActions={}",
                    agentId, profile.map(AgentProfile::getTenantId).orElse(null), task == null ? null : task.getTaskId(),
                    task == null ? null : task.getEffectiveTaskTypeCode(), result.getConnectionStatus(),
                    requirements == null ? List.of() : requirements.getRequiredCapabilities(), blockers,
                    nextActions.stream().map(DispatchNextAction::getAction).toList());
        } else {
            log.info("agent_direct_dispatch_pass agentId={} tenantId={} taskId={} taskType={} connectionStatus={} requiredCapabilities={}",
                    agentId, profile.map(AgentProfile::getTenantId).orElse(null), task == null ? null : task.getTaskId(),
                    task == null ? null : task.getEffectiveTaskTypeCode(), result.getConnectionStatus(),
                    requirements == null ? List.of() : requirements.getRequiredCapabilities());
        }
        low(observation, LowCardinalityKeyNames.RESULT, blocked ? "blocked" : warned ? "limited" : "eligible");
        low(observation, LowCardinalityKeyNames.DISPATCH_STATUS, normalizeObservationValue(result.getDispatchStatus()));
        low(observation, LowCardinalityKeyNames.CONNECTION_STATUS, normalizeObservationValue(result.getConnectionStatus()));
        low(observation, LowCardinalityKeyNames.REQUIREMENT_RESOLUTION, requirementResolution(requirements));
        low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, firstBlockingReasonCode(checks));
        high(observation, HighCardinalityKeyNames.TENANT_ID, profile.map(AgentProfile::getTenantId).orElse(task == null ? null : task.getTenantId()));
        return result;
    }


    public TaskDispatchRequirements resolveTaskRequirements(TaskRecord task) {
        TaskDispatchRequirements requirements = new TaskDispatchRequirements();
        requirements.setTaskId(task == null ? null : task.getTaskId());
        requirements.setTenantId(defaultTenant(task == null ? null : task.getTenantId()));
        requirements.setSourceSystem(normalizeToken(task == null ? null : task.getSourceSystem()));
        requirements.setTaskType(normalizeToken(task == null ? null : task.getEffectiveTaskTypeCode()));
        requirements.setRequiredProfiles(List.of());
        requirements.setProfiles(List.of());
        requirements.setRequiredPolicyCodes(List.of());
        requirements.setRequiredRuntimeFeatures(List.of());
        requirements.setTaskDefinitionIds(List.of());
        requirements.setRequiredCapabilities(explicitTaskCapabilities(task));
        requirements.setRequirementSource("DISPATCH_FLOW_DIRECT");
        requirements.setGeneratedAt(now());
        return requirements;
    }


    public TaskEligibleAgentsResponse eligibleAgents(TaskRecord task, int limit) {
        Observation observation = DispatchEligibilityObservationDocumentation.CANDIDATE_SELECTION
                .observation(observationRegistry)
                .lowCardinalityKeyValue(LowCardinalityKeyNames.CANDIDATE_RESULT.withValue("processing"))
                .lowCardinalityKeyValue(LowCardinalityKeyNames.BLOCKING_REASON_CODE.withValue("none"))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TENANT_ID.withValue(valueOrNone(task == null ? null : task.getTenantId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TASK_ID.withValue(valueOrNone(task == null ? null : task.getTaskId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.TASK_TYPE.withValue(valueOrNone(task == null ? null : task.getEffectiveTaskTypeCode())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.SOURCE_SYSTEM.withValue(valueOrNone(task == null ? null : task.getSourceSystem())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.FLOW_ID.withValue(valueOrNone(task == null ? null : task.getMatchedFlowId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.RULE_ID.withValue(valueOrNone(task == null ? null : task.getMatchedRuleId())))
                .highCardinalityKeyValue(HighCardinalityKeyNames.REQUESTED_SKILL.withValue(valueOrNone(task == null ? null : task.getRequestedSkill())));
        return observation.observe(() -> eligibleAgentsObserved(task, limit, observation));
    }

    private TaskEligibleAgentsResponse eligibleAgentsObserved(TaskRecord task, int limit, Observation observation) {
        TaskDispatchRequirements requirements = resolveTaskRequirements(task);
        if (contractUnresolved(requirements)) {
            TaskEligibleAgentsResponse response = new TaskEligibleAgentsResponse();
            response.setTaskId(task == null ? null : task.getTaskId());
            response.setRequirements(requirements);
            response.setEligibleAgents(List.of());
            response.setBlockedAgents(List.of());
            response.setGeneratedAt(now());
            low(observation, LowCardinalityKeyNames.CANDIDATE_RESULT, "contract_unresolved");
            low(observation, LowCardinalityKeyNames.REQUIREMENT_RESOLUTION, "unresolved");
            low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, "requirement_contract_unresolved");
            return response;
        }
        List<AgentProfile> agentProfiles = governanceService.searchProfiles(null, safeLimit(limit));
        List<EligibleAgentCandidate> eligible = new ArrayList<>();
        List<EligibleAgentCandidate> blocked = new ArrayList<>();
        for (AgentProfile profile : agentProfiles) {
            if (profile == null || blank(profile.getAgentId())) continue;
            AgentDispatchEligibility evaluation = evaluateAgent(profile.getAgentId(), task);
            EligibleAgentCandidate candidate = toCandidate(profile, evaluation, requirements);
            if (evaluation.isEligible()) eligible.add(candidate); else blocked.add(candidate);
        }
        eligible.sort(Comparator.comparing(EligibleAgentCandidate::getScore).reversed().thenComparing(EligibleAgentCandidate::getAgentId));
        blocked.sort(Comparator.comparing(EligibleAgentCandidate::getAgentId));
        TaskEligibleAgentsResponse response = new TaskEligibleAgentsResponse();
        response.setTaskId(task == null ? null : task.getTaskId());
        response.setRequirements(requirements);
        response.setEligibleAgents(eligible);
        response.setBlockedAgents(blocked);
        response.setGeneratedAt(now());
        low(observation, LowCardinalityKeyNames.CANDIDATE_RESULT, eligible.isEmpty() ? "no_eligible_candidate" : "candidates_available");
        low(observation, LowCardinalityKeyNames.REQUIREMENT_RESOLUTION, "resolved");
        low(observation, LowCardinalityKeyNames.BLOCKING_REASON_CODE, eligible.isEmpty() ? firstBlockedCandidateCode(blocked) : "none");
        return response;
    }

    private boolean contractUnresolved(TaskDispatchRequirements requirements) {
        return false;
    }


    private EligibleAgentCandidate toCandidate(AgentProfile profile, AgentDispatchEligibility evaluation, TaskDispatchRequirements requirements) {
        List<String> matchedProfiles = evaluation.getApprovedProfiles().stream()
                .filter(value -> requirements.getRequiredProfiles().isEmpty() || requirements.getRequiredProfiles().contains(value))
                .toList();
        EligibleAgentCandidate candidate = new EligibleAgentCandidate();
        candidate.setAgentId(profile.getAgentId());
        candidate.setAgentType(firstNonBlank(profile.getAgentType(), evaluation.getRuntimeDescriptor() == null ? null : evaluation.getRuntimeDescriptor().getAgentType()));
        candidate.setMatchedProfiles(matchedProfiles);
        candidate.setProfileCode(matchedProfiles.isEmpty() ? null : matchedProfiles.get(0));
        candidate.setEligible(evaluation.isEligible());
        candidate.setDispatchStatus(evaluation.getDispatchStatus());
        candidate.setChecks(evaluation.getChecks());
        candidate.setReason(firstBlockingReason(evaluation));
        candidate.setScore(scoreCandidate(evaluation, matchedProfiles));
        return candidate;
    }

    private int scoreCandidate(AgentDispatchEligibility evaluation, List<String> matchedProfiles) {
        int score = 0;
        if (evaluation.isEligible()) score += 50;
        score += Math.min(30, matchedProfiles.size() * 10);
        AgentRuntimeDescriptor descriptor = evaluation.getRuntimeDescriptor();
        if (descriptor != null) {
            score += Math.max(0, Math.min(20, descriptor.getAvailableSlots() * 5));
            score -= (int) Math.round(descriptor.getCapacityUtilization() * 10.0d);
        }
        return Math.max(0, Math.min(100, score));
    }

    private String firstBlockingReason(AgentDispatchEligibility evaluation) {
        return evaluation.getChecks().stream()
                .filter(DispatchEligibilityCheck::isBlocking)
                .map(DispatchEligibilityCheck::getMessage)
                .findFirst()
                .orElse(evaluation.getDispatchStatus());
    }

    private void evaluateProfile(String agentId, Optional<AgentProfile> profile, List<DispatchEligibilityCheck> checks, List<DispatchNextAction> nextActions) {
        if (profile.isEmpty()) {
            checks.add(DispatchEligibilityCheck.block("AGENT_PROFILE_EXISTS", "Core Agent profile does not exist or is not visible to governance."));
            nextActions.add(DispatchNextAction.of("CREATE_ENROLLMENT", "Create or approve Core Agent enrollment", "BLOCKING").withPayload("agentId", agentId));
            return;
        }
        AgentProfile value = profile.get();
        checks.add(DispatchEligibilityCheck.pass("AGENT_PROFILE_EXISTS", "Core Agent profile exists."));
        if (value.getApprovalStatus() == AgentApprovalStatus.APPROVED && value.isEnabled()) {
            checks.add(DispatchEligibilityCheck.pass("AGENT_APPROVED", "Agent identity is approved and enabled by backend governance."));
        } else {
            checks.add(DispatchEligibilityCheck.block("AGENT_APPROVED", "Agent identity is not approved and enabled by backend governance.")
                    .withDetail("approvalStatus", value.getApprovalStatus())
                    .withDetail("enabled", value.isEnabled()));
            nextActions.add(DispatchNextAction.of("APPROVE_AGENT", "Approve and enable the Agent identity", "BLOCKING").withPayload("agentId", agentId));
        }
        if (value.getRiskStatus() != null && !"NORMAL".equalsIgnoreCase(value.getRiskStatus().name())) {
            checks.add(DispatchEligibilityCheck.block("AGENT_RISK_STATUS", "Agent risk status blocks dispatch.")
                    .withDetail("riskStatus", value.getRiskStatus().name()));
            nextActions.add(DispatchNextAction.of("RESOLVE_RISK", "Resolve Agent risk status before dispatch", "BLOCKING"));
        } else {
            checks.add(DispatchEligibilityCheck.pass("AGENT_RISK_STATUS", "Agent risk status is normal."));
        }
    }

    private void evaluateCredential(Optional<AgentProfile> profile, List<DispatchEligibilityCheck> checks, List<DispatchNextAction> nextActions) {
        if (profile.isEmpty() || profile.get().getCredential() == null) {
            checks.add(DispatchEligibilityCheck.block("CREDENTIAL_VALID", "Agent has no active credential summary."));
            nextActions.add(DispatchNextAction.of("ISSUE_CREDENTIAL", "Issue or rotate Agent credential", "BLOCKING"));
            return;
        }
        AgentCredentialStatus status = profile.get().getCredential().getCredentialStatus();
        if (status == AgentCredentialStatus.ACTIVE) {
            checks.add(DispatchEligibilityCheck.pass("CREDENTIAL_VALID", "Agent credential is active."));
        } else {
            checks.add(DispatchEligibilityCheck.block("CREDENTIAL_VALID", "Agent credential is not active.").withDetail("credentialStatus", status));
            nextActions.add(DispatchNextAction.of("ROTATE_CREDENTIAL", "Rotate Agent credential", "BLOCKING"));
        }
    }

    private void evaluateRuntime(String agentId, Optional<AgentSnapshot> snapshot, Optional<AgentRuntimeDescriptor> descriptor, List<DispatchEligibilityCheck> checks, List<DispatchNextAction> nextActions) {
        boolean online = snapshot.map(agent -> agent.getStatus() != null && agent.getStatus() != AgentStatus.OFFLINE && agent.getStatus() != AgentStatus.EXPIRED).orElse(false)
                || descriptor.map(value -> value.getStatus() != null && value.getStatus() != AgentStatus.OFFLINE && value.getStatus() != AgentStatus.EXPIRED).orElse(false);
        if (online) {
            checks.add(DispatchEligibilityCheck.pass("RUNTIME_ONLINE", "Agent runtime is online or recently observed."));
        } else {
            checks.add(DispatchEligibilityCheck.block("RUNTIME_ONLINE", "Agent runtime is not online."));
            nextActions.add(DispatchNextAction.of("START_AGENT", "Start or reconnect the Agent runtime", "BLOCKING").withPayload("agentId", agentId));
        }
        if (descriptor.isPresent()) {
            checks.add(DispatchEligibilityCheck.pass("RUNTIME_DESCRIPTOR", "Agent runtime descriptor is available."));
        } else {
            checks.add(DispatchEligibilityCheck.warn("RUNTIME_DESCRIPTOR", "Agent runtime descriptor is missing. Legacy runtime state may still exist, but P0D prefers runtime facts."));
        }
    }



    private void evaluateRuntimeBinding(String agentId, List<DispatchEligibilityCheck> checks, List<DispatchNextAction> nextActions) {
        Optional<AgentRuntimeBinding> binding;
        try {
            binding = Optional.ofNullable(assignmentService.getActiveRuntimeBinding(agentId));
        } catch (Exception ex) {
            binding = Optional.empty();
        }
        if (binding.isPresent()) {
            AgentRuntimeBinding value = binding.get();
            checks.add(DispatchEligibilityCheck.pass("RUNTIME_BINDING_ACTIVE", "Agent has an ACTIVE Runtime Binding. Core treats Agent + Runtime Binding as the formal service unit.")
                    .withDetail("bindingId", value.getBindingId())
                    .withDetail("runtimeId", value.getRuntimeId())
                    .withDetail("runtimeCode", value.getRuntimeCode())
                    .withDetail("capacityLimit", value.getCapacityLimit())
                    .withDetail("riskLimit", value.getRiskLimit())
                    .withDetail("dataScope", value.getDataScope()));
            return;
        }
        checks.add(DispatchEligibilityCheck.block("RUNTIME_BINDING_ACTIVE", "Agent has no ACTIVE Runtime Binding. Runtime online status alone is not enough for dispatch authority."));
        nextActions.add(DispatchNextAction.of("BIND_RUNTIME", "Create and activate an Agent Runtime Binding before dispatch", "BLOCKING")
                .withPayload("agentId", agentId));
    }

    private void evaluateStandardFlowRequirementResolution(TaskDispatchRequirements requirements, List<DispatchEligibilityCheck> checks) {
        if (requirements == null) {
            checks.add(DispatchEligibilityCheck.info("DISPATCH_FLOW_REQUIREMENT_RESOLVED", "No task-specific Dispatch Flow requirement was requested."));
            return;
        }
        checks.add(DispatchEligibilityCheck.pass("DISPATCH_FLOW_REQUIREMENT_RESOLVED", "Standard direct dispatch uses Dispatch Flow Agent selection and optional required Capability only.")
                .withDetail("sourceSystem", requirements.getSourceSystem())
                .withDetail("taskType", requirements.getTaskType())
                .withDetail("requiredCapabilities", requirements.getRequiredCapabilities()));
    }

    private void evaluateCapabilityAssignments(String agentId, TaskDispatchRequirements requirements, OffsetDateTime now, List<DispatchEligibilityCheck> checks, List<DispatchNextAction> nextActions) {
        List<String> requiredCapabilities = requirements == null ? List.of() : requirements.getRequiredCapabilities();
        if (requiredCapabilities.isEmpty()) {
            checks.add(DispatchEligibilityCheck.info("AGENT_CAPABILITY_APPROVED", "No governed capability requirement was resolved for this task."));
            return;
        }

        List<AgentCapabilityAssignment> assignments = assignmentService.findAgentCapabilities(agentId);
        List<String> approved = assignments.stream()
                .filter(assignment -> assignment.getStatus() == AgentCapabilityAssignmentStatus.APPROVED)
                .filter(assignment -> assignment.getExpiresAt() == null || assignment.getExpiresAt().isAfter(now))
                .map(AgentCapabilityAssignment::getCapabilityCode)
                .map(this::normalizeToken)
                .distinct()
                .toList();
        List<String> missing = requiredCapabilities.stream()
                .filter(required -> approved.stream().noneMatch(value -> value.equals(normalizeToken(required))))
                .toList();
        if (missing.isEmpty()) {
            checks.add(DispatchEligibilityCheck.pass("AGENT_CAPABILITY_APPROVED", "Agent has APPROVED Capability assignments for all Dispatch Flow required capabilities.")
                    .withDetail("requiredCapabilities", requiredCapabilities)
                    .withDetail("approvedCapabilities", approved));
            return;
        }
        List<String> pending = capabilityStatusMatches(assignments, missing, AgentCapabilityAssignmentStatus.PENDING_APPROVAL, AgentCapabilityAssignmentStatus.DECLARED);
        List<String> revoked = capabilityStatusMatches(assignments, missing, AgentCapabilityAssignmentStatus.REVOKED, AgentCapabilityAssignmentStatus.SUSPENDED, AgentCapabilityAssignmentStatus.EXPIRED, AgentCapabilityAssignmentStatus.REJECTED);
        String code = !pending.isEmpty() ? "DISPATCH_AGENT_CAPABILITY_PENDING_APPROVAL" : !revoked.isEmpty() ? "DISPATCH_AGENT_CAPABILITY_REVOKED" : "DISPATCH_REQUIRED_CAPABILITY_MISSING";
        checks.add(DispatchEligibilityCheck.block(code, "Agent does not have APPROVED Capability assignments required by the Dispatch Flow.")
                .withDetail("missingCapabilities", missing)
                .withDetail("pendingCapabilities", pending)
                .withDetail("revokedOrSuspendedCapabilities", revoked)
                .withDetail("approvedCapabilities", approved));
        nextActions.add(DispatchNextAction.of("APPROVE_AGENT_CAPABILITY", "Request and approve the missing Agent Capability cards", "BLOCKING")
                .withPayload("agentId", agentId)
                .withPayload("missingCapabilities", missing));
    }

    private void evaluateTrustedRuntimeFeatures(String agentId, Optional<AgentRuntimeDescriptor> descriptor, TaskDispatchRequirements requirements, OffsetDateTime now, List<DispatchEligibilityCheck> checks, List<DispatchNextAction> nextActions) {
        List<String> requiredFeatures = requirements == null ? List.of() : requirements.getRequiredRuntimeFeatures();
        if (requiredFeatures.isEmpty()) {
            checks.add(DispatchEligibilityCheck.info("RUNTIME_FEATURE_TRUSTED", "No explicit runtime feature trust requirement was resolved for this task."));
            return;
        }
        if (requirements != null && requirements.getRequiredCapabilities() != null && !requirements.getRequiredCapabilities().isEmpty()) {
            checks.add(DispatchEligibilityCheck.info("RUNTIME_FEATURE_TRUSTED", "Runtime feature trust is diagnostic for Admin-managed capability dispatch and does not block this task.")
                    .withDetail("diagnosticRuntimeFeatures", requiredFeatures)
                    .withDetail("requiredCapabilities", requirements.getRequiredCapabilities()));
            return;
        }
        List<AgentRuntimeFeatureTrust> trusts = assignmentService.findRuntimeFeatureTrusts(agentId);
        List<String> trusted = trusts.stream()
                .filter(trust -> trust.getTrustStatus() == AgentRuntimeFeatureTrustStatus.TRUSTED)
                .filter(trust -> trust.getExpiresAt() == null || trust.getExpiresAt().isAfter(now))
                .map(AgentRuntimeFeatureTrust::getFeatureCode)
                .map(this::normalizeFeature)
                .distinct()
                .toList();
        List<String> missingTrust = requiredFeatures.stream()
                .filter(required -> trusted.stream().noneMatch(value -> value.equals(normalizeFeature(required))))
                .toList();
        if (missingTrust.isEmpty()) {
            checks.add(DispatchEligibilityCheck.pass("RUNTIME_FEATURE_TRUSTED", "Agent has TRUSTED Runtime Feature records for all required features.")
                    .withDetail("requiredFeatures", requiredFeatures)
                    .withDetail("trustedFeatures", trusted));
            return;
        }
        List<String> reportedOnly = descriptor.map(AgentRuntimeDescriptor::getRuntimeFeatures).orElse(List.of()).stream()
                .map(this::normalizeFeature)
                .filter(value -> missingTrust.stream().map(this::normalizeFeature).anyMatch(value::equals))
                .distinct()
                .toList();
        List<String> revoked = trusts.stream()
                .filter(trust -> trust.getTrustStatus() == AgentRuntimeFeatureTrustStatus.REVOKED || trust.getTrustStatus() == AgentRuntimeFeatureTrustStatus.SUSPENDED)
                .map(AgentRuntimeFeatureTrust::getFeatureCode)
                .map(this::normalizeFeature)
                .filter(value -> missingTrust.stream().map(this::normalizeFeature).anyMatch(value::equals))
                .distinct()
                .toList();
        String code = !revoked.isEmpty() ? "DISPATCH_RUNTIME_FEATURE_REVOKED" : !reportedOnly.isEmpty() ? "DISPATCH_RUNTIME_FEATURE_UNTRUSTED" : "DISPATCH_RUNTIME_FEATURE_MISSING";
        checks.add(DispatchEligibilityCheck.block(code, "Agent runtime feature is missing TRUSTED governance state. Runtime descriptor observations are not sufficient for dispatch.")
                .withDetail("missingTrustedFeatures", missingTrust)
                .withDetail("reportedButUntrusted", reportedOnly)
                .withDetail("revokedOrSuspendedFeatures", revoked)
                .withDetail("trustedFeatures", trusted));
        nextActions.add(DispatchNextAction.of("TRUST_RUNTIME_FEATURE", "Verify and trust the missing Runtime Feature cards before dispatch", "BLOCKING")
                .withPayload("agentId", agentId)
                .withPayload("missingRuntimeFeatures", missingTrust));
    }

    private void evaluateCapacity(Optional<AgentSnapshot> snapshot, Optional<AgentRuntimeDescriptor> descriptor, List<DispatchEligibilityCheck> checks, List<DispatchNextAction> nextActions) {
        boolean draining = snapshot.map(AgentSnapshot::isDraining).orElse(false) || descriptor.map(AgentRuntimeDescriptor::isDraining).orElse(false);
        if (draining) {
            checks.add(DispatchEligibilityCheck.block("NOT_DRAINING", "Agent is draining and must not receive new tasks."));
            nextActions.add(DispatchNextAction.of("RESUME_AGENT", "Resume Agent before dispatch", "BLOCKING"));
        } else {
            checks.add(DispatchEligibilityCheck.pass("NOT_DRAINING", "Agent is not draining."));
        }
        boolean capacity = snapshot.map(AgentSnapshot::isAssignable).orElseGet(() -> descriptor.map(value -> value.getAvailableSlots() > 0 || value.getActiveTasks() < Math.max(1, value.getMaxConcurrentTasks())).orElse(false));
        if (capacity) {
            checks.add(DispatchEligibilityCheck.pass("CAPACITY_AVAILABLE", "Agent has available dispatch capacity."));
        } else {
            checks.add(DispatchEligibilityCheck.block("CAPACITY_AVAILABLE", "Agent has no available dispatch capacity."));
            nextActions.add(DispatchNextAction.of("WAIT_FOR_CAPACITY", "Wait for current task load to drop", "BLOCKING"));
        }
    }

    private List<String> explicitTaskCapabilities(TaskRecord task) {
        if (task == null || task.getRequiredCapabilities() == null) return List.of();
        return task.getRequiredCapabilities().stream()
                .map(this::normalizeToken)
                .filter(value -> !blank(value))
                .filter(value -> !isMetadataCapability(value))
                .filter(value -> !isSkillVersionHint(value))
                .distinct()
                .toList();
    }

    private boolean isMetadataCapability(String value) {
        if (blank(value)) return false;
        String normalized = normalizeToken(value);
        return normalized.startsWith("META:")
                || normalized.startsWith("METADATA:")
                || normalized.startsWith("RUNTIME:")
                || normalized.startsWith("FEATURE:");
    }

    private boolean isSkillVersionHint(String value) {
        if (blank(value)) return false;
        String normalized = normalizeToken(value);
        return normalized.startsWith("VERSION:")
                || normalized.startsWith("SKILL_VERSION:")
                || normalized.contains("@VERSION=");
    }


    private List<String> capabilityStatusMatches(List<AgentCapabilityAssignment> assignments, List<String> requiredCapabilities, AgentCapabilityAssignmentStatus... statuses) {
        if (assignments == null || assignments.isEmpty() || requiredCapabilities == null || requiredCapabilities.isEmpty() || statuses == null) return List.of();
        Set<AgentCapabilityAssignmentStatus> expected = Set.of(statuses);
        Set<String> required = requiredCapabilities.stream().map(this::normalizeToken).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return assignments.stream()
                .filter(assignment -> expected.contains(assignment.getStatus()))
                .map(AgentCapabilityAssignment::getCapabilityCode)
                .map(this::normalizeToken)
                .filter(required::contains)
                .distinct()
                .toList();
    }

    private String mostRestrictiveRisk(String current, String candidate) {
        if (blank(current)) return candidate;
        if (blank(candidate)) return current;
        return riskRank(candidate) > riskRank(current) ? candidate : current;
    }

    private int riskRank(String value) {
        if (value == null) return 0;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MIDDLE", "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private String md5(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }

    private String firstBlockingReasonCode(List<DispatchEligibilityCheck> checks) {
        if (checks == null) return "none";
        return checks.stream()
                .filter(DispatchEligibilityCheck::isBlocking)
                .map(DispatchEligibilityCheck::getCode)
                .filter(code -> code != null && !code.isBlank())
                .map(this::normalizeObservationValue)
                .findFirst()
                .orElse("none");
    }

    private String firstBlockedCandidateCode(List<EligibleAgentCandidate> blocked) {
        if (blocked == null) return "no_eligible_candidate";
        return blocked.stream()
                .flatMap(candidate -> candidate.getChecks() == null ? java.util.stream.Stream.<DispatchEligibilityCheck>empty() : candidate.getChecks().stream())
                .filter(DispatchEligibilityCheck::isBlocking)
                .map(DispatchEligibilityCheck::getCode)
                .filter(code -> code != null && !code.isBlank())
                .map(this::normalizeObservationValue)
                .findFirst()
                .orElse("no_eligible_candidate");
    }

    private String requirementResolution(TaskDispatchRequirements requirements) {
        if (requirements == null) return "not_applicable";
        return contractUnresolved(requirements) ? "unresolved" : "resolved";
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

    private String normalizeObservationValue(String value) {
        return value == null || value.isBlank() ? "none" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private boolean featureEquals(String left, String right) {
        return normalizeFeature(left).equals(normalizeFeature(right));
    }

    private String normalizeFeature(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private Optional<AgentSnapshot> safeAgentSnapshot(String agentId) {
        try { return agentDirectoryService.findAgent(agentId); } catch (Exception ex) { return Optional.empty(); }
    }

    private Optional<AgentProfile> safeAgentProfile(String agentId) {
        try { return Optional.ofNullable(governanceService.getProfile(agentId)); } catch (Exception ex) { return Optional.empty(); }
    }

    private String connectionStatus(Optional<AgentSnapshot> snapshot, Optional<AgentRuntimeDescriptor> descriptor) {
        if (snapshot.isPresent() && snapshot.get().getStatus() != null) return snapshot.get().getStatus().name();
        if (descriptor.isPresent() && descriptor.get().getStatus() != null) return descriptor.get().getStatus().name();
        return "UNKNOWN";
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, 5000));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String defaultTenant(String tenantId) {
        if (blank(tenantId)) throw new IllegalArgumentException("tenantId is required");
        return tenantId.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
