package com.opensocket.aievent.core.assignment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.AgentSnapshot;
import com.opensocket.aievent.core.agent.CapacityReservationResult;
import com.opensocket.aievent.core.agent.governance.AgentGovernanceRepository;
import com.opensocket.aievent.core.agent.governance.AgentProfile;
import com.opensocket.aievent.core.task.domain.TaskParticipant;
import com.opensocket.aievent.core.task.domain.TaskParticipantOperationLevel;
import com.opensocket.aievent.core.task.domain.TaskParticipantRepository;
import com.opensocket.aievent.core.task.domain.TaskParticipantRole;
import com.opensocket.aievent.core.task.domain.TaskParticipantType;
import com.opensocket.aievent.core.task.domain.TaskParticipantVisibilityLevel;
import com.opensocket.aievent.core.dispatch.DispatchDecisionResult;
import com.opensocket.aievent.core.routing.DispatchUserFacingError;
import com.opensocket.aievent.core.routing.DispatchUserFacingErrorCode;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.routing.RoutingDecisionService;
import com.opensocket.aievent.core.routing.RoutingDecisionStatus;
import com.opensocket.aievent.core.routing.RoutingProperties;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryProperties;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;

@Service
public class TaskAssignmentService {
    private final RoutingDecisionService routingDecisionService;
    private final TaskAssignmentRepository assignmentRepository;
    private final AgentDirectoryFacade agentDirectory;
    private final TaskRepository taskRepository;
    private final RoutingProperties properties;
    private final TaskDispatchRecoveryProperties dispatchRecoveryProperties;
    private final TaskDispatchPort dispatchPort;

    @Autowired(required = false)
    private TaskDispatchAttemptHistoryPort attemptHistoryPort = TaskDispatchAttemptHistoryPort.noop();

    /** RS4 canonical Agent ownership source. Optional only for focused unit-test constructors. */
    @Autowired(required = false)
    private AgentGovernanceRepository agentGovernanceRepository;

    /** RS4 operational participant evidence; Resource Access remains the authorization authority. */
    @Autowired(required = false)
    private TaskParticipantRepository taskParticipantRepository;

    @Autowired(required = false)
    private TaskAuthorizationProjectionPort authorizationProjectionPort;

    /** RS4 fail-closed only when the canonical Resource Access runtime is enabled. */
    @Value("${resource-access.enabled:false}")
    private boolean resourceAccessEnabled;

    @Autowired
    public TaskAssignmentService(RoutingDecisionService routingDecisionService,
                                 TaskAssignmentRepository assignmentRepository,
                                 AgentDirectoryFacade agentDirectory,
                                 TaskRepository taskRepository,
                                 RoutingProperties properties,
                                 TaskDispatchRecoveryProperties dispatchRecoveryProperties,
                                 TaskDispatchPort dispatchPort) {
        this.routingDecisionService = routingDecisionService;
        this.assignmentRepository = assignmentRepository;
        this.agentDirectory = agentDirectory;
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.dispatchRecoveryProperties = dispatchRecoveryProperties == null ? new TaskDispatchRecoveryProperties() : dispatchRecoveryProperties;
        this.dispatchPort = dispatchPort;
    }

    /** Compatibility constructor for focused unit tests. */
    public TaskAssignmentService(RoutingDecisionService routingDecisionService,
                                 TaskAssignmentRepository assignmentRepository,
                                 AgentDirectoryFacade agentDirectory,
                                 TaskRepository taskRepository,
                                 RoutingProperties properties,
                                 TaskDispatchPort dispatchPort) {
        this(routingDecisionService, assignmentRepository, agentDirectory, taskRepository, properties,
                new TaskDispatchRecoveryProperties(), dispatchPort);
    }

    @Transactional
    public AssignmentDecisionResult assignIfPossible(TaskRecord task) {
        return assignmentRepository.findOpenByTaskId(task.getTaskId())
                .map(existing -> {
                    clearPendingDispatchDelay(task, "Task already has open assignment " + existing.getAssignmentId());
                    attemptHistoryPort.recordAssignmentReused(task, existing, "Task already has open assignment " + existing.getAssignmentId(), OffsetDateTime.now(ZoneOffset.UTC));
                    return AssignmentDecisionResult.withDispatch(
                            false,
                            existing.getAssignmentId(),
                            existing.getAgentId(),
                            existing.getOwnerGatewayNodeId(),
                            existing.getAgentSessionId(),
                            existing.getSiteId(),
                            existing.getRoutingDecisionId(),
                            existing.getStatus().name(),
                            "Task already has open assignment " + existing.getAssignmentId(),
                            dispatchPort.createIfEligible(existing, task));
                })
                .orElseGet(() -> createFromRoutingDecision(task));
    }

    @Transactional
    public boolean releaseCapacityReservation(String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return false;
        }
        TaskAssignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null || !assignment.isCapacityReserved()) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!assignmentRepository.releaseCapacityReservation(assignmentId, now)) {
            return false;
        }
        agentDirectory.releaseCapacity(assignment.getAgentId());
        assignment.setCapacityReserved(false);
        assignment.setCapacityReleasedAt(now);
        return true;
    }

    private AssignmentDecisionResult createFromRoutingDecision(TaskRecord task) {
        Set<String> excluded = new HashSet<>();
        int maxAttempts = Math.max(1, properties.getMaxCandidates());
        RoutingDecisionRecord lastDecision = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            RoutingDecisionRecord decision = routingDecisionService.decide(task, excluded);
            lastDecision = decision;
            if (decision.getStatus() != RoutingDecisionStatus.SELECTED) {
                return noAssignment(task, decision);
            }

            CapacityReservationResult reservation = agentDirectory.reserveCapacity(decision.getSelectedAgentId());
            if (!reservation.reserved()) {
                excluded.add(decision.getSelectedAgentId());
                continue;
            }
            return persistReservedAssignment(task, decision);
        }
        String reason = lastDecision == null
                ? "No routing decision was produced"
                : "All selected candidates lost capacity before reservation; lastDecision=" + lastDecision.getDecisionId();
        return deferAssignmentRetry(task,
                lastDecision == null ? null : lastDecision.getDecisionId(),
                RoutingDecisionStatus.NO_CANDIDATE.name(),
                reason);
    }

    private AssignmentDecisionResult persistReservedAssignment(TaskRecord task, RoutingDecisionRecord decision) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assign-" + UUID.randomUUID());
        assignment.setTenantId(task.getTenantId());
        assignment.setTaskId(task.getTaskId());
        assignment.setIncidentId(task.getIncidentId());
        assignment.setAgentId(decision.getSelectedAgentId());
        AgentSnapshot agent = agentDirectory.findById(decision.getSelectedAgentId()).orElse(null);
        assignment.setAgentType(agent == null ? null : agent.getAgentType());
        assignment.setOwnerGatewayNodeId(decision.getSelectedGatewayNodeId());
        assignment.setAgentSessionId(decision.getSelectedAgentSessionId());
        assignment.setSiteId(decision.getSelectedSiteId());
        copyR3EnvelopeTrace(task, assignment);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setRoutingPolicy(decision.getRoutingPolicy().name());
        assignment.setRoutingDecisionId(decision.getDecisionId());
        assignment.setLeaseId("lease-" + UUID.randomUUID());
        assignment.setFencingToken("fence-" + UUID.randomUUID());
        assignment.setLeaseExpiresAt(now.plus(properties.getAssignmentLeaseTtl()));
        assignment.setScore(decision.getSelectedScore());
        assignment.setReason(decision.getDecisionReason() + "; agent capacity reserved atomically before assignment persistence");
        assignment.setCapacityReserved(true);
        assignment.setCapacityReservedAt(now);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);

        applyExecutorScopeSnapshot(task, assignment.getAgentId(), now);
        TaskAssignment saved = null;
        try {
            saved = assignmentRepository.save(assignment);
            if (properties.isUpdateTaskStatusOnAssignment()) {
                task.setStatus(TaskStatus.ASSIGNED);
                task.setAssignedPoolId(firstNonBlank(task.getAssignedPoolId(), task.getTargetPoolId()));
                task.setNextDispatchAttemptAt(null);
                task.setDispatchRetryReason(null);
                task.setDispatchRecoveryClaimedBy(null);
                task.setDispatchRecoveryClaimUntil(null);
                task.setUpdatedAt(now);
                task.setLifecycleReason("Assigned to agent " + saved.getAgentId());
                taskRepository.save(task);
            }
            projectTaskAuthorization(task);
            attemptHistoryPort.recordAssignmentCreated(task, saved, saved.getReason(), now);
            DispatchDecisionResult dispatch = dispatchPort.createIfEligible(saved, task);
            return AssignmentDecisionResult.withDispatch(true, saved.getAssignmentId(), saved.getAgentId(), saved.getOwnerGatewayNodeId(),
                    saved.getAgentSessionId(), saved.getSiteId(), saved.getRoutingDecisionId(), saved.getStatus().name(), saved.getReason(), dispatch);
        } catch (RuntimeException ex) {
            if (saved != null) {
                assignmentRepository.releaseCapacityReservation(saved.getAssignmentId(), OffsetDateTime.now(ZoneOffset.UTC));
            }
            agentDirectory.releaseCapacity(decision.getSelectedAgentId());
            throw ex;
        }
    }


    /**
     * Stage 4 capability-runtime assignment. The concrete Agent is trusted internal resolution
     * output from the MANAGED_AGENT adapter, never a caller preference. Existing Assignment /
     * DispatchRequest / Netty delivery remains the execution authority.
     */
    @Transactional
    public AssignmentDecisionResult assignGovernedManagedAgent(TaskRecord task, GovernedManagedAgentAssignmentRequest request) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            return AssignmentDecisionResult.none("Task is required for governed managed-agent assignment");
        }
        if (request == null || request.agentId() == null || request.agentId().isBlank()) {
            return AssignmentDecisionResult.none("Resolved managed Agent is required");
        }
        if (!task.getTaskId().equals(request.taskId())) {
            return AssignmentDecisionResult.none("Governed assignment task mismatch");
        }
        if (request.bindingId() == null || request.bindingId().isBlank()
                || request.providerId() == null || request.providerId().isBlank()
                || request.authorizationDecisionId() == null || request.authorizationDecisionId().isBlank()
                || request.routingDecisionId() == null || request.routingDecisionId().isBlank()
                || request.adapterResolutionId() == null || request.adapterResolutionId().isBlank()) {
            return AssignmentDecisionResult.none("Governed capability authority evidence is incomplete");
        }
        return assignmentRepository.findOpenByTaskId(task.getTaskId())
                .map(existing -> AssignmentDecisionResult.withDispatch(
                        false, existing.getAssignmentId(), existing.getAgentId(), existing.getOwnerGatewayNodeId(),
                        existing.getAgentSessionId(), existing.getSiteId(), existing.getRoutingDecisionId(),
                        existing.getStatus().name(), "Task already has open assignment " + existing.getAssignmentId(),
                        dispatchPort.createIfEligible(existing, task)))
                .orElseGet(() -> persistGovernedManagedAgentAssignment(task, request));
    }

    private AssignmentDecisionResult persistGovernedManagedAgentAssignment(TaskRecord task, GovernedManagedAgentAssignmentRequest request) {
        AgentSnapshot agent = agentDirectory.findById(request.agentId()).orElse(null);
        if (agent == null) {
            return AssignmentDecisionResult.none("Resolved managed Agent not found: " + request.agentId());
        }
        if (agent.getTenantId() != null && !agent.getTenantId().isBlank() && !task.getTenantId().equals(agent.getTenantId())) {
            return AssignmentDecisionResult.none("Resolved managed Agent Tenant mismatch");
        }
        if (!agent.isAssignable()) {
            return AssignmentDecisionResult.none("Resolved managed Agent is not assignable: " + request.agentId());
        }
        CapacityReservationResult reservation = agentDirectory.reserveCapacity(request.agentId());
        if (!reservation.reserved()) {
            return AssignmentDecisionResult.none("Resolved managed Agent capacity reservation failed: " + reservation.reason());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assign-" + UUID.randomUUID());
        assignment.setTenantId(task.getTenantId());
        assignment.setTaskId(task.getTaskId());
        assignment.setIncidentId(task.getIncidentId());
        assignment.setAgentId(request.agentId());
        assignment.setAgentType(agent.getAgentType());
        assignment.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        assignment.setAgentSessionId(agent.getAgentSessionId());
        assignment.setSiteId(agent.getSiteId());
        copyR3EnvelopeTrace(task, assignment);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setRoutingPolicy("CAPABILITY_GOVERNED");
        assignment.setRoutingDecisionId(request.routingDecisionId());
        assignment.setBindingId(request.bindingId());
        assignment.setExecutionTargetType("MANAGED_AGENT");
        assignment.setProviderType("MANAGED_AGENT");
        assignment.setProviderId(request.providerId());
        assignment.setExecutionSafetyMode(ExecutionSafetyMode.LOCAL_FENCED);
        assignment.setAttemptNumber(1);
        assignment.setLeaseId("lease-" + UUID.randomUUID());
        assignment.setFencingToken("fence-" + UUID.randomUUID());
        assignment.setLeaseExpiresAt(now.plus(properties.getAssignmentLeaseTtl()));
        assignment.setScore(100);
        assignment.setReason(firstNonBlank(request.reason(),
                "Capability runtime selected provider=" + request.providerId()
                        + "; binding=" + request.bindingId()
                        + "; authorizationDecision=" + request.authorizationDecisionId()
                        + "; adapterResolution=" + request.adapterResolutionId()));
        assignment.setCapacityReserved(true);
        assignment.setCapacityReservedAt(now);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        applyExecutorScopeSnapshot(task, assignment.getAgentId(), now);
        TaskAssignment saved = null;
        try {
            saved = assignmentRepository.save(assignment);
            if (properties.isUpdateTaskStatusOnAssignment()) {
                task.setStatus(TaskStatus.ASSIGNED);
                task.setNextDispatchAttemptAt(null);
                task.setDispatchRetryReason(null);
                task.setDispatchRecoveryClaimedBy(null);
                task.setDispatchRecoveryClaimUntil(null);
                task.setUpdatedAt(now);
                task.setLifecycleReason("Capability-governed assignment to managed Agent " + saved.getAgentId());
                taskRepository.save(task);
            }
            projectTaskAuthorization(task);
            attemptHistoryPort.recordAssignmentCreated(task, saved, saved.getReason(), now);
            DispatchDecisionResult dispatch = dispatchPort.createIfEligible(saved, task);
            return AssignmentDecisionResult.withDispatch(true, saved.getAssignmentId(), saved.getAgentId(), saved.getOwnerGatewayNodeId(),
                    saved.getAgentSessionId(), saved.getSiteId(), saved.getRoutingDecisionId(), saved.getStatus().name(), saved.getReason(), dispatch);
        } catch (RuntimeException ex) {
            if (saved != null) assignmentRepository.releaseCapacityReservation(saved.getAssignmentId(), OffsetDateTime.now(ZoneOffset.UTC));
            agentDirectory.releaseCapacity(request.agentId());
            throw ex;
        }
    }


    /**
     * Stage 6/7 provider-neutral execution assignment for MCP or Remote A2A.
     * The provider target is trusted internal resolution output, never caller input.
     * No Agent capacity is reserved and no Agent DispatchRequest is created.
     */
    @Transactional
    public AssignmentDecisionResult assignGovernedExternalProvider(TaskRecord task, GovernedExternalProviderAssignmentRequest request) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            return AssignmentDecisionResult.none("Task is required for governed external-provider assignment");
        }
        if (request == null || request.providerId() == null || request.providerId().isBlank()
                || request.bindingId() == null || request.bindingId().isBlank()
                || request.authorizationDecisionId() == null || request.authorizationDecisionId().isBlank()
                || request.routingDecisionId() == null || request.routingDecisionId().isBlank()
                || request.adapterResolutionId() == null || request.adapterResolutionId().isBlank()) {
            return AssignmentDecisionResult.none("Governed external-provider authority evidence is incomplete");
        }
        if (!task.getTaskId().equals(request.taskId())) {
            return AssignmentDecisionResult.none("Governed external-provider assignment task mismatch");
        }
        String targetType = request.executionTargetType() == null ? "" : request.executionTargetType().trim().toUpperCase(java.util.Locale.ROOT);
        String providerType = request.providerType() == null ? "" : request.providerType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!("MCP_TOOL".equals(targetType) && "MCP_TOOL".equals(providerType))
                && !("REMOTE_A2A_AGENT".equals(targetType) && "REMOTE_A2A_AGENT".equals(providerType))) {
            return AssignmentDecisionResult.none("External provider target type is not allowed: " + targetType + "/" + providerType);
        }
        if ("MCP_TOOL".equals(targetType)
                && (request.selectedMcpServerId() == null || request.selectedMcpServerId().isBlank() || request.selectedMcpToolId() == null || request.selectedMcpToolId().isBlank())) {
            return AssignmentDecisionResult.none("MCP execution requires selected server and tool");
        }
        if ("REMOTE_A2A_AGENT".equals(targetType) && (request.selectedPeerInterfaceId() == null || request.selectedPeerInterfaceId().isBlank())) {
            return AssignmentDecisionResult.none("Remote A2A execution requires selected peer interface");
        }
        return assignmentRepository.findOpenByTaskId(task.getTaskId())
                .map(existing -> AssignmentDecisionResult.withDispatch(false, existing.getAssignmentId(), existing.getAgentId(),
                        existing.getOwnerGatewayNodeId(), existing.getAgentSessionId(), existing.getSiteId(), existing.getRoutingDecisionId(),
                        existing.getStatus().name(), "Task already has open assignment " + existing.getAssignmentId(),
                        DispatchDecisionResult.none("External provider execution does not use Agent DispatchRequest")))
                .orElseGet(() -> persistGovernedExternalProviderAssignment(task, request, targetType, providerType));
    }

    private AssignmentDecisionResult persistGovernedExternalProviderAssignment(TaskRecord task,
            GovernedExternalProviderAssignmentRequest request, String targetType, String providerType) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assign-" + UUID.randomUUID());
        assignment.setTenantId(task.getTenantId());
        assignment.setTaskId(task.getTaskId());
        assignment.setIncidentId(task.getIncidentId());
        assignment.setAgentId(null);
        assignment.setAgentType(null);
        assignment.setOwnerGatewayNodeId(null);
        assignment.setAgentSessionId(null);
        assignment.setSiteId(null);
        copyR3EnvelopeTrace(task, assignment);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setRoutingPolicy("CAPABILITY_GOVERNED");
        assignment.setRoutingDecisionId(request.routingDecisionId());
        assignment.setBindingId(request.bindingId());
        assignment.setExecutionTargetType(targetType);
        assignment.setProviderType(providerType);
        assignment.setProviderId(request.providerId());
        assignment.setSelectedPeerInterfaceId(request.selectedPeerInterfaceId());
        assignment.setSelectedMcpServerId(request.selectedMcpServerId());
        assignment.setSelectedMcpToolId(request.selectedMcpToolId());
        assignment.setExecutionSafetyMode(ExecutionSafetyMode.REMOTE_NATIVE_IDEMPOTENT);
        assignment.setAttemptNumber(1);
        assignment.setLeaseId("lease-" + UUID.randomUUID());
        assignment.setFencingToken("fence-" + UUID.randomUUID());
        assignment.setLeaseExpiresAt(now.plus(properties.getAssignmentLeaseTtl()));
        assignment.setScore(100);
        assignment.setReason(firstNonBlank(request.reason(), "Capability runtime selected external provider=" + request.providerId()
                + "; binding=" + request.bindingId() + "; authorizationDecision=" + request.authorizationDecisionId()
                + "; adapterResolution=" + request.adapterResolutionId()));
        assignment.setCapacityReserved(false);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        TaskAssignment saved = assignmentRepository.save(assignment);
        if (properties.isUpdateTaskStatusOnAssignment()) {
            task.setStatus(TaskStatus.ASSIGNED);
            task.setNextDispatchAttemptAt(null);
            task.setDispatchRetryReason(null);
            task.setDispatchRecoveryClaimedBy(null);
            task.setDispatchRecoveryClaimUntil(null);
            task.setUpdatedAt(now);
            task.setLifecycleReason("Capability-governed assignment to external provider " + request.providerId());
            taskRepository.save(task);
        }
        attemptHistoryPort.recordAssignmentCreated(task, saved, saved.getReason(), now);
        return AssignmentDecisionResult.withDispatch(true, saved.getAssignmentId(), null, null, null, null,
                saved.getRoutingDecisionId(), saved.getStatus().name(), saved.getReason(),
                DispatchDecisionResult.none("External provider execution queued by adapter; no Agent DispatchRequest"));
    }



    @Transactional
    public AssignmentDecisionResult assignToSpecificAgent(TaskRecord task, String agentId, String reason) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            return AssignmentDecisionResult.none("Task is required for target-agent assignment");
        }
        if (agentId == null || agentId.isBlank()) {
            return AssignmentDecisionResult.none("agentId is required for target-agent assignment");
        }
        return assignmentRepository.findOpenByTaskId(task.getTaskId())
                .map(existing -> AssignmentDecisionResult.withDispatch(
                        false,
                        existing.getAssignmentId(),
                        existing.getAgentId(),
                        existing.getOwnerGatewayNodeId(),
                        existing.getAgentSessionId(),
                        existing.getSiteId(),
                        existing.getRoutingDecisionId(),
                        existing.getStatus().name(),
                        "Task already has open assignment " + existing.getAssignmentId(),
                        dispatchPort.createIfEligible(existing, task)))
                .orElseGet(() -> persistReservedTargetAgentAssignment(task, agentId, reason));
    }

    private AssignmentDecisionResult persistReservedTargetAgentAssignment(TaskRecord task, String agentId, String reason) {
        AgentSnapshot agent = agentDirectory.findById(agentId).orElse(null);
        if (agent == null) {
            return AssignmentDecisionResult.none("Target agent not found: " + agentId);
        }
        if (!agent.isAssignable()) {
            return AssignmentDecisionResult.none("Target agent is not assignable: " + agentId);
        }
        CapacityReservationResult reservation = agentDirectory.reserveCapacity(agentId);
        if (!reservation.reserved()) {
            return AssignmentDecisionResult.none("Target agent capacity reservation failed: " + reservation.reason());
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        TaskAssignment assignment = new TaskAssignment();
        assignment.setAssignmentId("assign-" + UUID.randomUUID());
        assignment.setTenantId(task.getTenantId());
        assignment.setTaskId(task.getTaskId());
        assignment.setIncidentId(task.getIncidentId());
        assignment.setAgentId(agentId);
        assignment.setAgentType(agent.getAgentType());
        assignment.setOwnerGatewayNodeId(agent.getOwnerGatewayNodeId());
        assignment.setAgentSessionId(agent.getAgentSessionId());
        assignment.setSiteId(agent.getSiteId());
        copyR3EnvelopeTrace(task, assignment);
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setRoutingPolicy("CERTIFICATION_TARGET_AGENT");
        assignment.setRoutingDecisionId("cert-routing-" + UUID.randomUUID());
        assignment.setLeaseId("lease-" + UUID.randomUUID());
        assignment.setFencingToken("fence-" + UUID.randomUUID());
        assignment.setLeaseExpiresAt(now.plus(properties.getAssignmentLeaseTtl()));
        assignment.setScore(100);
        assignment.setReason(firstNonBlank(reason, "Certification task pinned to target agent " + agentId));
        assignment.setCapacityReserved(true);
        assignment.setCapacityReservedAt(now);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        applyExecutorScopeSnapshot(task, assignment.getAgentId(), now);
        try {
            TaskAssignment saved = assignmentRepository.save(assignment);
            task.setStatus(TaskStatus.ASSIGNED);
            task.setUpdatedAt(now);
            task.setLifecycleReason("Certification task assigned to target agent " + saved.getAgentId());
            taskRepository.save(task);
            projectTaskAuthorization(task);
            attemptHistoryPort.recordAssignmentCreated(task, saved, saved.getReason(), now);
            DispatchDecisionResult dispatch = dispatchPort.createIfEligible(saved, task);
            return AssignmentDecisionResult.withDispatch(true, saved.getAssignmentId(), saved.getAgentId(), saved.getOwnerGatewayNodeId(),
                    saved.getAgentSessionId(), saved.getSiteId(), saved.getRoutingDecisionId(), saved.getStatus().name(), saved.getReason(), dispatch);
        } catch (RuntimeException ex) {
            agentDirectory.releaseCapacity(agentId);
            throw ex;
        }
    }


    /**
     * RS4 execution scope snapshot. Assignment never changes Task primary ownership; it adds an explicit
     * operational execution scope derived from the governed Agent profile. Capability/pool membership is not ACL.
     */
    private void applyExecutorScopeSnapshot(TaskRecord task, String agentId, OffsetDateTime now) {
        if (task == null || agentId == null || agentId.isBlank()) {
            return;
        }
        if (agentGovernanceRepository == null) {
            if (resourceAccessEnabled) {
                throw new IllegalStateException("RS4_RESOURCE_ACCESS_CONFIGURATION_INVALID: AgentGovernanceRepository is required");
            }
            return; // focused unit-test compatibility while Resource Access is disabled
        }
        AgentProfile profile = agentGovernanceRepository.findProfile(agentId)
                .orElseThrow(() -> new IllegalStateException("RS4_AGENT_SCOPE_UNRESOLVED: governed Agent profile not found: " + agentId));
        if (!profile.allowsConnection()) {
            throw new IllegalStateException("RS4_AGENT_SCOPE_DENIED: Agent is not approved/enabled for execution: " + agentId);
        }
        if (task.getTenantId() == null || !task.getTenantId().equals(profile.getTenantId())) {
            throw new IllegalStateException("RS4_AGENT_CROSS_TENANT_DENIED: Task and Agent Tenant must match");
        }
        String departmentId = firstNonBlank(profile.getOwnerDepartmentId(), "UNASSIGNED");
        String groupId = firstNonBlank(profile.getOwnerGroupId());
        if ("UNASSIGNED".equalsIgnoreCase(departmentId) && groupId == null) {
            throw new IllegalStateException("RS4_AGENT_SCOPE_UNRESOLVED: Agent must have Department or Group ownership before assignment");
        }
        task.setExecutorDepartmentId(departmentId);
        task.setExecutorGroupId(groupId);
        task.setExecutorDomainId(firstNonBlank(profile.getServiceDomainId(), "UNASSIGNED"));
        persistAgentExecutorParticipant(task, agentId, now);
    }

    private void persistAgentExecutorParticipant(TaskRecord task, String agentId, OffsetDateTime now) {
        if (taskParticipantRepository == null) {
            if (resourceAccessEnabled) {
                throw new IllegalStateException("RS4_RESOURCE_ACCESS_CONFIGURATION_INVALID: TaskParticipantRepository is required");
            }
            return;
        }
        for (TaskParticipant participant : taskParticipantRepository.findByTask(task.getTenantId(), task.getTaskId(), 500)) {
            if (participant.getParticipantType() == TaskParticipantType.AGENT
                    && participant.getParticipantRole() == TaskParticipantRole.EXECUTOR
                    && !agentId.equals(participant.getParticipantRefId())) {
                taskParticipantRepository.delete(task.getTenantId(), participant.getParticipantId());
            }
        }
        if (taskParticipantRepository.findNatural(task.getTenantId(), task.getTaskId(), TaskParticipantType.AGENT, agentId, TaskParticipantRole.EXECUTOR).isPresent()) {
            return;
        }
        TaskParticipant participant = new TaskParticipant();
        participant.setTenantId(task.getTenantId());
        participant.setParticipantId("task-participant-" + UUID.randomUUID());
        participant.setTaskId(task.getTaskId());
        participant.setParticipantType(TaskParticipantType.AGENT);
        participant.setParticipantRefId(agentId);
        participant.setParticipantRole(TaskParticipantRole.EXECUTOR);
        participant.setVisibilityLevel(TaskParticipantVisibilityLevel.FULL);
        participant.setOperationLevel(TaskParticipantOperationLevel.OPERATE);
        participant.setCreatedAt(now);
        participant.setCreatedBy("RS4_DISPATCH_ASSIGNMENT");
        taskParticipantRepository.save(participant);
    }

    private void projectTaskAuthorization(TaskRecord task) {
        if (authorizationProjectionPort == null) {
            if (resourceAccessEnabled) {
                throw new IllegalStateException("RS4_RESOURCE_ACCESS_CONFIGURATION_INVALID: TaskAuthorizationProjectionPort is required");
            }
            return;
        }
        authorizationProjectionPort.projectTask(task.getTenantId(), task.getTaskId(), task.getCorrelationId());
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private void clearPendingDispatchDelay(TaskRecord task, String reason) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        if (task.getNextDispatchAttemptAt() == null
                && task.getDispatchRetryReason() == null
                && task.getDispatchRecoveryClaimedBy() == null
                && task.getDispatchRecoveryClaimUntil() == null) {
            return;
        }
        task.setNextDispatchAttemptAt(null);
        task.setDispatchRetryReason(null);
        task.setDispatchRecoveryClaimedBy(null);
        task.setDispatchRecoveryClaimUntil(null);
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        task.setLifecycleReason(reason);
        taskRepository.save(task);
    }

    private AssignmentDecisionResult noAssignment(TaskRecord task, RoutingDecisionRecord decision) {
        persistRoutingEvidence(task, decision);
        String configurationBlocker = configurationBlockerCode(task, decision.getDecisionReason());
        if (configurationBlocker != null) {
            return suspendUntilConfigurationChange(task, decision.getDecisionId(), decision.getStatus().name(), configurationBlocker, decision.getDecisionReason());
        }
        if (decision.getStatus() == RoutingDecisionStatus.NO_CANDIDATE) {
            return deferAssignmentRetry(task, decision.getDecisionId(), decision.getStatus().name(), decision.getDecisionReason());
        }
        return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null,
                decision.getDecisionId(), decision.getStatus().name(), decision.getDecisionReason(),
                DispatchDecisionResult.none("No assignment was created"));
    }

    private void persistRoutingEvidence(TaskRecord task, RoutingDecisionRecord decision) {
        if (task == null || task.getTaskId() == null) {
            return;
        }
        if (task.getTargetPoolId() == null && task.getAssignedPoolId() == null
                && task.getMatchedFlowId() == null && task.getMatchedRuleId() == null) {
            return;
        }
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        task.setLifecycleReason(firstNonBlank(task.getLifecycleReason(), decision == null ? null : decision.getDecisionReason(), "Routing evidence recorded"));
        taskRepository.save(task);
    }

    private AssignmentDecisionResult suspendUntilConfigurationChange(TaskRecord task, String routingDecisionId, String status, String blockerCode, String reason) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String message = "WAITING_CONFIGURATION:" + blockerCode + ":" + stripTechnicalDetails(reason);
        taskRepository.suspendDispatchUntilConfigurationChange(task.getTaskId(), blockerCode, stripTechnicalDetails(reason), now);
        task.setStatus(TaskStatus.RETRY_WAIT);
        task.setNextDispatchAttemptAt(null);
        task.setDispatchRetryReason(message);
        task.setLifecycleReason("Waiting for dispatch configuration change: " + blockerCode);
        attemptHistoryPort.recordDelayedDispatch(task, routingDecisionId, message, null, now);
        return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null, routingDecisionId, status, message,
                DispatchDecisionResult.none("No assignment was created; task is waiting for a dispatch configuration change"));
    }

    private String configurationBlockerCode(TaskRecord task, String reason) {
        String normalized = ((reason == null ? "" : reason) + " " + (task == null ? "" : String.valueOf(task.getRoutingPath()))).toUpperCase(java.util.Locale.ROOT);
        if (normalized.contains("SOURCE_FLOW_NOT_FOUND")) return "SOURCE_FLOW_NOT_FOUND";
        if (normalized.contains("SOURCE_FLOW_HAS_NO_DEFAULT_POOL")) return "SOURCE_FLOW_HAS_NO_DEFAULT_POOL";
        if (normalized.contains("RULE_TARGET_POOL_NOT_FOUND")) return "RULE_TARGET_POOL_NOT_FOUND";
        if (normalized.contains("POOL_HAS_NO_ACTIVE_MEMBER")) return "POOL_HAS_NO_ACTIVE_MEMBER";
        if (normalized.contains("POOL_AGENT_RUNTIME_NOT_FOUND")) return "POOL_AGENT_RUNTIME_NOT_FOUND";
        if (normalized.contains("POOL_AGENT_OFFLINE")) return "POOL_AGENT_OFFLINE";
        if (normalized.contains("POOL_AGENT_CAPACITY_FULL")) return "POOL_AGENT_CAPACITY_FULL";
        if (normalized.contains("POOL_AGENT_BACKOFF")) return "POOL_AGENT_BACKOFF";
        if (normalized.contains("NO_ELIGIBLE_AGENT_IN_POOL")) return "NO_ELIGIBLE_AGENT_IN_POOL";
        if (normalized.contains("NO_ACTIVE_FLOW_RULE") || normalized.contains("FLOW_RULE_REQUIRED_BLOCKED")) return "SOURCE_FLOW_NOT_FOUND";
        if (normalized.contains("NO_FLOW_SELECTED_AGENT") || normalized.contains("FLOW_SELECTED_AGENT_REQUIRED")) return "NO_ELIGIBLE_AGENT_IN_POOL";
        return null;
    }


    private String userFacingDelayedDispatchReason(OffsetDateTime nextAttemptAt, String status, String reason) {
        String readableReason = stripTechnicalDetails(reason);
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_DELAYED_NO_ELIGIBLE_AGENT,
                "MEDIUM",
                "目標 Agent Pool 目前沒有可用成員可派工，系統已安排稍後自動重試。原因：" + readableReason + " 下次重試：" + nextAttemptAt,
                "請先檢查 Source Flow default Pool / Rule target Pool 是否正確，Pool 內是否有已核准、已連線且有容量的 Agent；修正後系統會依重試時間再次派工。",
                "runbooks/dispatch/pool-first-delayed-no-eligible-agent",
                details("routingStatus", status),
                details("nextDispatchAttemptAt", nextAttemptAt, "routingStatus", status, "routingReason", reason)
        ).toLegacyDecisionReason();
    }

    private String stripTechnicalDetails(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Core routing 回報沒有可派工 Agent。";
        }
        int marker = reason.indexOf("Technical details:");
        return marker >= 0 ? reason.substring(0, marker).trim() : reason.trim();
    }

    private String userFacingRecoveryExhaustedReason(int attempts, String routingDecisionId, String status, String reason) {
        return DispatchUserFacingError.of(
                DispatchUserFacingErrorCode.DISPATCH_RECOVERY_EXHAUSTED,
                "HIGH",
                "Dispatch recovery 已達最大重試次數，任務已轉為失敗狀態。原因：" + stripTechnicalDetails(reason),
                "請由 Operator 檢查 Dispatch Flow、Agent 核准狀態、Runtime 與容量後，決定重新開啟、改派或取消此任務。",
                "runbooks/dispatch/recovery-exhausted",
                details("routingDecisionId", routingDecisionId, "routingStatus", status),
                details("attempts", attempts, "routingDecisionId", routingDecisionId, "routingStatus", status, "routingReason", reason)
        ).toLegacyDecisionReason();
    }

    private Map<String, Object> details(Object... keyValues) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (keyValues == null) {
            return values;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (key != null) {
                values.put(String.valueOf(key), keyValues[i + 1]);
            }
        }
        return values;
    }

    private void copyR3EnvelopeTrace(TaskRecord task, TaskAssignment assignment) {
        if (task == null || assignment == null) {
            return;
        }
        assignment.setEventStage(task.getEventStage());
        assignment.setOriginSourceSystem(task.getOriginSourceSystem());
        assignment.setTargetSystem(task.getTargetSystem());
        assignment.setRequestedSkill(task.getRequestedSkill());
        assignment.setCorrelationId(task.getCorrelationId());
        assignment.setParentTaskId(task.getParentTaskId());
        assignment.setHandoffMode(task.getHandoffMode());
        assignment.setMatchedFlowId(task.getMatchedFlowId());
        assignment.setMatchedRuleId(task.getMatchedRuleId());
        assignment.setAssignedPoolId(firstNonBlank(task.getAssignedPoolId(), task.getTargetPoolId()));
        assignment.setTargetPoolId(task.getTargetPoolId());
        assignment.setRoutingPath(task.getRoutingPath());
    }

    private AssignmentDecisionResult deferAssignmentRetry(TaskRecord task, String routingDecisionId, String status, String reason) {
        if (task == null || task.getTaskId() == null || !dispatchRecoveryProperties.isEnabled()) {
            return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null, routingDecisionId, status, reason,
                    DispatchDecisionResult.none("No assignment was created"));
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int nextAttempt = task.getDispatchAttemptCount() + 1;
        if (dispatchRecoveryProperties.getMaxAttempts() > 0
                && nextAttempt > dispatchRecoveryProperties.getMaxAttempts()) {
            task.setStatus(TaskStatus.FAILED);
            task.setTerminalAt(now);
            task.setUpdatedAt(now);
            task.setDispatchRetryReason(userFacingRecoveryExhaustedReason(task.getDispatchAttemptCount(), routingDecisionId, status, reason));
            task.setLifecycleReason(task.getDispatchRetryReason());
            taskRepository.save(task);
            attemptHistoryPort.recordRecoveryExhausted(task, routingDecisionId, task.getDispatchRetryReason(), now);
            return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null, routingDecisionId, status,
                    task.getDispatchRetryReason(), DispatchDecisionResult.none("No assignment was created; task dispatch recovery exhausted"));
        }
        OffsetDateTime nextAttemptAt = now.plus(dispatchRecoveryProperties.delayForAttempt(nextAttempt));
        String recoveryReason = userFacingDelayedDispatchReason(nextAttemptAt, status, reason);
        taskRepository.deferDispatchAttempt(task.getTaskId(), nextAttemptAt, nextAttempt, recoveryReason, now);
        task.setNextDispatchAttemptAt(nextAttemptAt);
        task.setDispatchAttemptCount(nextAttempt);
        task.setDispatchRetryReason(recoveryReason);
        attemptHistoryPort.recordDelayedDispatch(task, routingDecisionId, recoveryReason, nextAttemptAt, now);
        return AssignmentDecisionResult.withDispatch(false, null, null, null, null, null, routingDecisionId, status, recoveryReason,
                DispatchDecisionResult.none("No assignment was created; task dispatch recovery scheduled at " + nextAttemptAt));
    }
}

