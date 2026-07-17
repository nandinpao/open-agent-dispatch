package com.opensocket.aievent.core.gateway;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.agent.AgentDirectoryService;
import com.opensocket.aievent.core.agent.AgentStatus;

@Service
public class GatewayDirectoryService {
    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(45);

    private final GatewayNodeRepository repository;
    private final AgentDirectoryService agentDirectoryService;

    public GatewayDirectoryService(GatewayNodeRepository repository, AgentDirectoryService agentDirectoryService) {
        this.repository = repository;
        this.agentDirectoryService = agentDirectoryService;
    }

    public GatewayNode register(GatewayNode node) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (node.getRegisteredAt() == null) {
            node.setRegisteredAt(now);
        }
        node.setLastHeartbeatAt(now);
        node.setUpdatedAt(now);
        if (node.getLeaseExpiresAt() == null) {
            node.setLeaseExpiresAt(now.plus(DEFAULT_LEASE));
        }
        if (node.getStatus() == null) {
            node.setStatus(GatewayNodeStatus.ONLINE);
        }
        if (node.getVersion() == null || node.getVersion().isBlank()) {
            node.setVersion("unknown");
        }
        return repository.upsert(node);
    }

    public GatewayNode heartbeat(String gatewayNodeId, GatewayNodeStatus status, Duration leaseTtl) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        GatewayNode node = repository.findById(gatewayNodeId).orElseGet(() -> {
            GatewayNode created = new GatewayNode();
            created.setGatewayNodeId(gatewayNodeId);
            created.setRegisteredAt(now);
            return created;
        });
        node.setStatus(status == null ? GatewayNodeStatus.ONLINE : status);
        node.setLastHeartbeatAt(now);
        node.setUpdatedAt(now);
        node.setLeaseExpiresAt(now.plus(leaseTtl == null || leaseTtl.isNegative() || leaseTtl.isZero() ? DEFAULT_LEASE : leaseTtl));
        if (node.getVersion() == null || node.getVersion().isBlank()) {
            node.setVersion("unknown");
        }
        return repository.upsert(node);
    }

    public List<GatewayNode> search(GatewayNodeQuery query) {
        return repository.search(query);
    }

    public GatewayNode get(String gatewayNodeId) {
        return repository.findById(gatewayNodeId)
                .orElseThrow(() -> new IllegalArgumentException("Gateway node not found: " + gatewayNodeId));
    }

    @Scheduled(fixedDelayString = "${gateway-nodes.lease-reaper.fixed-delay:10000}")
    @Transactional
    public void expireLeases() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int expired = repository.expireLeases(now);
        if (expired > 0) {
            GatewayNodeQuery query = new GatewayNodeQuery();
            query.setStatus(GatewayNodeStatus.EXPIRED);
            query.setLimit(1000);
            repository.search(query).forEach(node ->
                    agentDirectoryService.markAgentsByGatewayNode(node.getGatewayNodeId(), AgentStatus.EXPIRED, now));
        }
    }

    public String mode() { return repository.mode(); }
}
