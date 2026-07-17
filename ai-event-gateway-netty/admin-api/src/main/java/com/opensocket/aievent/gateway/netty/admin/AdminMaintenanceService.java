package com.opensocket.aievent.gateway.netty.admin;

import com.opensocket.aievent.gateway.netty.admin.dto.AdminActionResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains operational markers requested by the Admin UI.
 *
 * <p>Drain/resume is a local transport-advisory marker only. It does not own business assignment
 * or durable scheduling state. Production assignment and cross-node routing policy remain owned by
 * ai-event-gateway-core.</p>
 */
@Service
public class AdminMaintenanceService {

    private final Set<String> drainedNodes = ConcurrentHashMap.newKeySet();

    public AdminActionResponse drainNode(String nodeId) {
        drainedNodes.add(nodeId);
        return AdminActionResponse.completed(
                "CLUSTER_NODE_DRAIN",
                "CLUSTER_NODE",
                nodeId,
                "Node marked as locally drained for transport operations. Business assignment remains owned by ai-event-gateway-core.",
                Map.of("drained", true, "nodeId", nodeId, "updatedAt", OffsetDateTime.now().toString())
        );
    }

    public AdminActionResponse resumeNode(String nodeId) {
        drainedNodes.remove(nodeId);
        return AdminActionResponse.completed(
                "CLUSTER_NODE_RESUME",
                "CLUSTER_NODE",
                nodeId,
                "Node drain marker removed.",
                Map.of("drained", false, "nodeId", nodeId, "updatedAt", OffsetDateTime.now().toString())
        );
    }

    public boolean isDrained(String nodeId) {
        return drainedNodes.contains(nodeId);
    }

    public Set<String> drainedNodes() {
        return Set.copyOf(drainedNodes);
    }
}
