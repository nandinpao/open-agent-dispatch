package com.opensocket.aievent.core.gateway;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "gateway-nodes", name = "store", havingValue = "MEMORY")
public class InMemoryGatewayNodeRepository implements GatewayNodeRepository {
    private final ConcurrentHashMap<String, GatewayNode> nodes = new ConcurrentHashMap<>();

    @Override
    public GatewayNode upsert(GatewayNode node) {
        nodes.put(node.getGatewayNodeId(), node);
        return node;
    }

    @Override
    public Optional<GatewayNode> findById(String gatewayNodeId) {
        return Optional.ofNullable(nodes.get(gatewayNodeId));
    }

    @Override
    public List<GatewayNode> search(GatewayNodeQuery query) {
        return nodes.values().stream()
                .filter(node -> query.getStatus() == null || query.getStatus() == node.getStatus())
                .filter(node -> matches(query.getSiteId(), node.getSiteId()))
                .filter(node -> matches(query.getRegion(), node.getRegion()))
                .filter(node -> matches(query.getZone(), node.getZone()))
                .sorted(Comparator.comparing(GatewayNode::getLastHeartbeatAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(query.getLimit())
                .toList();
    }

    @Override
    public int expireLeases(OffsetDateTime now) {
        int[] count = {0};
        nodes.computeIfPresent("__never__", (id, node) -> node);
        nodes.forEach((id, node) -> {
            if (node.getLeaseExpiresAt() != null && node.getLeaseExpiresAt().isBefore(now)
                    && node.getStatus() != GatewayNodeStatus.EXPIRED && node.getStatus() != GatewayNodeStatus.OFFLINE) {
                node.setStatus(GatewayNodeStatus.EXPIRED);
                node.setUpdatedAt(now);
                count[0]++;
            }
        });
        return count[0];
    }

    @Override
    public String mode() { return "MEMORY"; }

    private boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }
}
