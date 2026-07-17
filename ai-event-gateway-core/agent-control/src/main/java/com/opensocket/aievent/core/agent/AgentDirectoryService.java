package com.opensocket.aievent.core.agent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.agent.assignment.AgentAssignmentService;

@Service
public class AgentDirectoryService implements AgentDirectoryFacade, AgentControlOperationalQuery {
    private static final Duration DEFAULT_AGENT_LEASE = Duration.ofSeconds(45);

    private final AgentDirectoryRepository repository;
    private final AgentRuntimeStateRepository runtimeStateRepository;
    private final com.opensocket.aievent.core.gateway.GatewayNodeRepository gatewayNodeRepository;
    private final AgentAssignmentService assignmentService;

    @Autowired
    public AgentDirectoryService(AgentDirectoryRepository repository,
                                 com.opensocket.aievent.core.gateway.GatewayNodeRepository gatewayNodeRepository,
                                 AgentRuntimeStateRepository runtimeStateRepository,
                                 ObjectProvider<AgentAssignmentService> assignmentServiceProvider) {
        this.repository = repository;
        this.gatewayNodeRepository = gatewayNodeRepository;
        this.runtimeStateRepository = runtimeStateRepository;
        this.assignmentService = assignmentServiceProvider == null ? null : assignmentServiceProvider.getIfAvailable();
    }

    /** Compatibility constructor for focused unit tests. */
    public AgentDirectoryService(AgentDirectoryRepository repository) {
        this(repository, new com.opensocket.aievent.core.gateway.InMemoryGatewayNodeRepository(), new InMemoryAgentRuntimeStateRepository(), null);
    }

    /** Compatibility constructor for focused unit tests. */
    public AgentDirectoryService(AgentDirectoryRepository repository,
                                 com.opensocket.aievent.core.gateway.GatewayNodeRepository gatewayNodeRepository) {
        this(repository, gatewayNodeRepository, new InMemoryAgentRuntimeStateRepository(), null);
    }

    /** Compatibility constructor for focused unit tests that need to share runtime state. */
    public AgentDirectoryService(AgentDirectoryRepository repository,
                                 com.opensocket.aievent.core.gateway.GatewayNodeRepository gatewayNodeRepository,
                                 AgentRuntimeStateRepository runtimeStateRepository) {
        this(repository, gatewayNodeRepository, runtimeStateRepository, null);
    }

    public AgentSnapshot register(AgentSnapshot agent) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (agent.getStatus() == null) {
            agent.setStatus(AgentStatus.IDLE);
        }
        if (agent.getConnectedAt() == null && agent.getOwnerGatewayNodeId() != null) {
            agent.setConnectedAt(now);
        }
        if (agent.getLastHeartbeatAt() == null) {
            agent.setLastHeartbeatAt(now);
        }
        if (agent.getLeaseExpiresAt() == null) {
            agent.setLeaseExpiresAt(now.plus(DEFAULT_AGENT_LEASE));
        }
        if (agent.getAgentSessionId() == null || agent.getAgentSessionId().isBlank()) {
            agent.setAgentSessionId("session-" + UUID.randomUUID());
        }
        return saveAndSync(agent);
    }

    public AgentSnapshot connected(String gatewayNodeId, String agentId, AgentSnapshot request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AgentSnapshot agent = repository.findById(agentId).orElseGet(AgentSnapshot::new);
        agent.setAgentId(agentId);
        agent.setOwnerGatewayNodeId(gatewayNodeId);
        agent.setAgentSessionId(blank(request.getAgentSessionId()) ? "session-" + UUID.randomUUID() : request.getAgentSessionId());
        agent.setAgentType(blank(request.getAgentType()) ? agent.getAgentType() : request.getAgentType());
        agent.setSiteId(blank(request.getSiteId()) ? agent.getSiteId() : request.getSiteId());
        agent.setSiteName(blank(request.getSiteName()) ? agent.getSiteName() : request.getSiteName());
        agent.setRegion(blank(request.getRegion()) ? agent.getRegion() : request.getRegion());
        agent.setZone(blank(request.getZone()) ? agent.getZone() : request.getZone());
        agent.setCapabilities(request.getCapabilities());
        agent.setMaxConcurrentTasks(request.getMaxConcurrentTasks());
        agent.setCurrentTaskCount(request.getCurrentTaskCount());
        agent.setHealthScore(request.getHealthScore() <= 0 ? 100 : request.getHealthScore());
        agent.setCapabilityProfile(request.getCapabilityProfile());
        agent.setRuntimeLoad(request.getRuntimeLoad());
        agent.setPluginName(request.getPluginName());
        agent.setPluginVersion(request.getPluginVersion());
        agent.setCapabilityRevision(request.getCapabilityRevision());
        agent.setAvailableSlots(request.getAvailableSlots());
        agent.setCapacityUtilization(request.getCapacityUtilization());
        agent.setOutboxPending(request.getOutboxPending());
        agent.setOutboxInFlight(request.getOutboxInFlight());
        agent.setRecoveryPendingAssignments(request.getRecoveryPendingAssignments());
        agent.setDraining(request.isDraining());
        agent.setStatus(resolveRuntimeStatus(request));
        agent.setConnectedAt(now);
        agent.setDisconnectedAt(null);
        agent.setLastHeartbeatAt(now);
        agent.setLeaseExpiresAt(request.getLeaseExpiresAt() == null ? now.plus(DEFAULT_AGENT_LEASE) : request.getLeaseExpiresAt());
        ensureRuntimeBindingAuthority(gatewayNodeId, agentId, agent);
        return saveAndSync(agent);
    }

    public AgentSnapshot heartbeat(String agentId, AgentStatus status, int currentTaskCount, int healthScore) {
        AgentSnapshot agent = repository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        agent.setCurrentTaskCount(currentTaskCount);
        agent.setHealthScore(healthScore <= 0 ? agent.getHealthScore() : healthScore);
        agent.setStatus(resolveRuntimeStatus(agent, status == null ? agent.getStatus() : status));
        agent.setLastHeartbeatAt(now);
        agent.setLeaseExpiresAt(now.plus(DEFAULT_AGENT_LEASE));
        return saveAndSync(agent);
    }

    public AgentSnapshot gatewayHeartbeat(String gatewayNodeId, String agentId, AgentStatus status, int currentTaskCount, int healthScore, String agentSessionId,
                                          Map<String, Object> runtimeLoad, String capabilityRevision, Map<String, Object> plugin, Map<String, Object> capabilityProfile) {
        AgentSnapshot agent = repository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        agent.setOwnerGatewayNodeId(gatewayNodeId);
        if (!blank(agentSessionId)) {
            agent.setAgentSessionId(agentSessionId);
        }
        agent.setCurrentTaskCount(currentTaskCount);
        agent.setHealthScore(healthScore <= 0 ? agent.getHealthScore() : healthScore);
        agent.setRuntimeLoad(runtimeLoad);
        if (!blank(capabilityRevision)) {
            agent.setCapabilityRevision(capabilityRevision);
        }
        if (capabilityProfile != null && !capabilityProfile.isEmpty()) {
            agent.setCapabilityProfile(capabilityProfile);
        }
        if (plugin != null && !plugin.isEmpty()) {
            agent.setPluginName(pluginText(plugin, "name"));
            agent.setPluginVersion(pluginText(plugin, "version"));
        }
        agent.setStatus(resolveRuntimeStatus(agent, status == null ? agent.getStatus() : status));
        agent.setLastHeartbeatAt(now);
        agent.setLeaseExpiresAt(now.plus(DEFAULT_AGENT_LEASE));
        ensureRuntimeBindingAuthority(gatewayNodeId, agentId, agent);
        return saveAndSync(agent);
    }

    public AgentSnapshot disconnected(String gatewayNodeId, String agentId, String agentSessionId, String reason) {
        AgentSnapshot agent = repository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (gatewayNodeId != null) {
            agent.setOwnerGatewayNodeId(gatewayNodeId);
        }
        if (!blank(agentSessionId)) {
            agent.setAgentSessionId(agentSessionId);
        }
        agent.setStatus(AgentStatus.OFFLINE);
        agent.setCurrentTaskCount(0);
        agent.setDisconnectedAt(now);
        agent.setLastHeartbeatAt(now);
        agent.setLeaseExpiresAt(now);
        return saveAndSync(agent);
    }

    public List<AgentSnapshot> replaceGatewaySnapshot(String gatewayNodeId, List<AgentSnapshot> agents) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        repository.markByGatewayNodeId(gatewayNodeId, AgentStatus.OFFLINE, now);
        return agents == null ? List.of() : agents.stream()
                .map(agent -> connected(gatewayNodeId, agent.getAgentId(), agent))
                .toList();
    }

    public int markAgentsByGatewayNode(String gatewayNodeId, AgentStatus status, OffsetDateTime at) {
        return repository.markByGatewayNodeId(gatewayNodeId, status == null ? AgentStatus.EXPIRED : status, at);
    }

    @Scheduled(fixedDelayString = "${agent-directory.lease-reaper.fixed-delay:10000}")
    @Transactional
    public void expireLeases() {
        repository.expireLeases(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public java.util.Optional<AgentSnapshot> findById(String agentId) {
        return repository.findById(agentId);
    }

    @Override
    public List<AgentSnapshot> findCandidates(AgentQuery query) {
        return repository.search(query == null ? new AgentQuery() : query);
    }

    @Override
    @Transactional
    public CapacityReservationResult reserveCapacity(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return CapacityReservationResult.rejected(agentId, "agentId is required");
        }
        if (!repository.reserveCapacity(agentId)) {
            return CapacityReservationResult.rejected(agentId, "Agent has no reservable capacity or is not assignable");
        }
        java.util.Optional<AgentSnapshot> reservedAgent = repository.findById(agentId);
        if (reservedAgent.isEmpty()) {
            repository.releaseCapacity(agentId);
            return CapacityReservationResult.rejected(agentId,
                    "Capacity reservation was rolled back because the agent could not be reloaded");
        }
        return CapacityReservationResult.reserved(reservedAgent.get());
    }

    @Override
    @Transactional
    public boolean releaseCapacity(String agentId) {
        return agentId != null && !agentId.isBlank() && repository.releaseCapacity(agentId);
    }


    @Override
    @Transactional
    public Optional<AgentSnapshot> applyRuntimeBackoff(String agentId, OffsetDateTime backoffUntil, String reason) {
        if (blank(agentId)) {
            return Optional.empty();
        }
        return repository.findById(agentId).map(agent -> {
            OffsetDateTime effectiveUntil = backoffUntil == null
                    ? OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30)
                    : backoffUntil;
            agent.setRuntimeBackoffUntil(effectiveUntil);
            agent.setRuntimeBackoffReason(blank(reason) ? "Runtime dispatch failure backoff" : reason);
            agent.setRuntimeFailureCount(agent.getRuntimeFailureCount() + 1);
            return saveAndSync(agent);
        });
    }

    @Override
    @Transactional
    public Optional<AgentSnapshot> clearRuntimeBackoff(String agentId, String reason) {
        if (blank(agentId)) {
            return Optional.empty();
        }
        return repository.findById(agentId).map(agent -> {
            agent.setRuntimeBackoffUntil(null);
            agent.setRuntimeBackoffReason(blank(reason) ? null : reason);
            agent.setRuntimeFailureCount(0);
            return saveAndSync(agent);
        });
    }

    public List<AgentSnapshot> search(AgentQuery query) {
        return findCandidates(query);
    }

    @Override
    public String mode() {
        return repository.mode();
    }

    @Override
    public Optional<AgentSnapshot> findAgent(String agentId) {
        return repository.findById(agentId);
    }

    @Override
    public List<AgentSnapshot> searchAgents(AgentQuery query) {
        return repository.search(query == null ? new AgentQuery() : query);
    }

    @Override
    public Optional<com.opensocket.aievent.core.gateway.GatewayNode> findGatewayNode(String gatewayNodeId) {
        return gatewayNodeRepository.findById(gatewayNodeId);
    }

    @Override
    public List<com.opensocket.aievent.core.gateway.GatewayNode> searchGatewayNodes(com.opensocket.aievent.core.gateway.GatewayNodeQuery query) {
        return gatewayNodeRepository.search(query == null ? new com.opensocket.aievent.core.gateway.GatewayNodeQuery() : query);
    }

    @Override
    public Map<String, Integer> agentStatusCounts(int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AgentStatus status : AgentStatus.values()) {
            AgentQuery query = new AgentQuery();
            query.setStatus(status);
            query.setLimit(Math.max(1, limit));
            counts.put(status.name(), repository.search(query).size());
        }
        return counts;
    }

    @Override
    public Map<String, Integer> gatewayStatusCounts(int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (com.opensocket.aievent.core.gateway.GatewayNodeStatus status : com.opensocket.aievent.core.gateway.GatewayNodeStatus.values()) {
            com.opensocket.aievent.core.gateway.GatewayNodeQuery query = new com.opensocket.aievent.core.gateway.GatewayNodeQuery();
            query.setStatus(status);
            query.setLimit(Math.max(1, limit));
            counts.put(status.name(), gatewayNodeRepository.search(query).size());
        }
        return counts;
    }

    @Override public String agentStoreMode() { return repository.mode(); }
    @Override public String gatewayStoreMode() { return gatewayNodeRepository.mode(); }


    public Optional<AgentRuntimeCapabilityProfile> findRuntimeCapabilityProfile(String agentId) {
        return runtimeStateRepository.findCapabilityProfile(agentId);
    }

    public Optional<AgentRuntimeDescriptor> findRuntimeDescriptor(String agentId) {
        return runtimeStateRepository.findRuntimeDescriptor(agentId);
    }

    public List<AgentRuntimeCapabilityItem> findRuntimeCapabilityItems(String agentId) {
        return runtimeStateRepository.findCapabilityItems(agentId);
    }

    public Optional<AgentRuntimeLoadSnapshot> findRuntimeLoadSnapshot(String agentId) {
        return runtimeStateRepository.findLoadSnapshot(agentId);
    }

    public String runtimeStateMode() {
        return runtimeStateRepository.mode();
    }


    private void ensureRuntimeBindingAuthority(String gatewayNodeId, String agentId, AgentSnapshot agent) {
        if (assignmentService == null || blank(agentId)) {
            return;
        }
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("gatewayNodeId", gatewayNodeId);
            metadata.put("agentSessionId", agent == null ? null : agent.getAgentSessionId());
            metadata.put("runtimeType", firstNonBlank(agent == null ? null : agent.getAgentType(), "AGENT_RUNTIME"));
            metadata.put("connectorType", "GATEWAY_RUNTIME");
            metadata.put("executionHost", gatewayNodeId);
            metadata.put("environment", "runtime-observation");
            assignmentService.ensureActiveRuntimeBindingForRuntimeObservation(
                    null,
                    agentId,
                    agent == null ? null : agent.getAgentSessionId(),
                    firstNonBlank(agent == null ? null : agent.getAgentType(), "AGENT_RUNTIME"),
                    gatewayNodeId);
        } catch (RuntimeException ignored) {
            // Runtime observation should not reject the heartbeat path. Readiness will expose
            // RUNTIME_BINDING_ACTIVE as pending if activation could not be persisted.
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value;
        }
        return null;
    }

    private AgentSnapshot saveAndSync(AgentSnapshot agent) {
        AgentSnapshot saved = repository.upsert(agent);
        runtimeStateRepository.upsertFromSnapshot(saved);
        return saved;
    }

    private AgentStatus resolveRuntimeStatus(AgentSnapshot request) {
        if (request == null || request.getStatus() == null) {
            return AgentStatus.IDLE;
        }
        return resolveRuntimeStatus(request, request.getStatus());
    }

    /**
     * Runtime payloads may use CONNECTED as a transport/session status.  Core dispatch
     * scoring, however, expects a workload status: IDLE/BUSY_ACCEPTING are assignable,
     * BUSY is not.  Normalize the transport status at the directory boundary so
     * readiness and actual routing consume the same dispatch authority state.
     */
    private AgentStatus resolveRuntimeStatus(AgentSnapshot snapshot, AgentStatus reportedStatus) {
        if (snapshot == null && reportedStatus == null) {
            return AgentStatus.IDLE;
        }
        AgentStatus status = reportedStatus == null ? AgentStatus.IDLE : reportedStatus;
        if ((snapshot != null && snapshot.isDraining()) || status == AgentStatus.DRAINING) {
            return AgentStatus.DRAINING;
        }
        if (status == AgentStatus.OFFLINE || status == AgentStatus.EXPIRED || status == AgentStatus.ERROR) {
            return status;
        }
        if (status == AgentStatus.CONNECTED || status == AgentStatus.IDLE || status == AgentStatus.BUSY_ACCEPTING) {
            int effectiveTasks = snapshot == null ? 0 : snapshot.getEffectiveTaskCount();
            int maxConcurrentTasks = snapshot == null ? 1 : Math.max(1, snapshot.getMaxConcurrentTasks());
            int availableSlots = snapshot == null ? 0 : Math.max(0, snapshot.getAvailableSlots());
            if (effectiveTasks <= 0) {
                return AgentStatus.IDLE;
            }
            if (effectiveTasks < maxConcurrentTasks || availableSlots > 0) {
                return AgentStatus.BUSY_ACCEPTING;
            }
            return AgentStatus.BUSY;
        }
        return status;
    }

    private String pluginText(Map<String, Object> plugin, String key) {
        if (plugin == null || key == null) {
            return null;
        }
        Object value = plugin.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
