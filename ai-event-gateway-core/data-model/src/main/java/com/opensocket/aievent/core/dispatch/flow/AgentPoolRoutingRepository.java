package com.opensocket.aievent.core.dispatch.flow;

import java.util.Optional;

/** Repository port for Pool-first runtime candidate selection. */
public interface AgentPoolRoutingRepository {
    Optional<AgentPoolRoutingSnapshot> findActivePool(String tenantId, String poolId);
}
