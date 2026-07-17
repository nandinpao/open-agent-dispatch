package com.opensocket.aievent.core.gateway;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GatewayNodeRepository {
    GatewayNode upsert(GatewayNode node);
    Optional<GatewayNode> findById(String gatewayNodeId);
    List<GatewayNode> search(GatewayNodeQuery query);
    int expireLeases(OffsetDateTime now);
    String mode();
}
