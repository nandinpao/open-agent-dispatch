package com.opensocket.aievent.core.agent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentDirectoryRepository {
    AgentSnapshot upsert(AgentSnapshot agent);
    Optional<AgentSnapshot> findById(String agentId);
    List<AgentSnapshot> search(AgentQuery query);
    void updateStatus(String agentId, AgentStatus status);
    boolean reserveCapacity(String agentId);
    boolean releaseCapacity(String agentId);
    int markByGatewayNodeId(String gatewayNodeId, AgentStatus status, OffsetDateTime disconnectedAt);
    int expireLeases(OffsetDateTime now);
    String mode();
}
