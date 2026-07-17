package com.opensocket.aievent.gateway.netty.cluster.sync;

import com.opensocket.aievent.gateway.netty.api.GatewayApiErrorCode;
import com.opensocket.aievent.gateway.netty.api.GatewayApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Cluster state synchronization component for Cluster Sync Controller. It exposes or consumes
 * lightweight node state snapshots so Admin UI can show a cluster-wide transport view without
 * moving business ownership across nodes.
 */
@RestController
@RequestMapping("/api/cluster")
public class ClusterSyncController {

    private final ClusterStateSnapshotService clusterStateSnapshotService;
    private final ClusterRemoteStateRegistry clusterRemoteStateRegistry;
    private final ClusterOverviewService clusterOverviewService;
    private final ClusterStateSyncScheduler clusterStateSyncScheduler;

    public ClusterSyncController(
            ClusterStateSnapshotService clusterStateSnapshotService,
            ClusterRemoteStateRegistry clusterRemoteStateRegistry,
            ClusterOverviewService clusterOverviewService,
            ClusterStateSyncScheduler clusterStateSyncScheduler
    ) {
        this.clusterStateSnapshotService = clusterStateSnapshotService;
        this.clusterRemoteStateRegistry = clusterRemoteStateRegistry;
        this.clusterOverviewService = clusterOverviewService;
        this.clusterStateSyncScheduler = clusterStateSyncScheduler;
    }

    @GetMapping("/state/local")
    public ClusterStateSnapshotResponse localState() {
        return clusterStateSnapshotService.localSnapshot();
    }

    @GetMapping("/state/remote")
    public List<RemoteClusterStateSnapshot> remoteStates() {
        return clusterRemoteStateRegistry.list();
    }

    @GetMapping("/state/remote/{nodeId}")
    public RemoteClusterStateSnapshot remoteState(@PathVariable String nodeId) {
        return clusterRemoteStateRegistry.findByNodeId(nodeId)
                .orElseThrow(() -> new GatewayApiException(GatewayApiErrorCode.GATEWAY_CLUSTER_NODE_NOT_FOUND, "Remote cluster state not found: " + nodeId));
    }

    @GetMapping("/runtime-metrics")
    public Map<String, ClusterNodeRuntimeMetricsResponse> runtimeMetrics() {
        return clusterOverviewService.overview().runtimeMetricsByNode();
    }

    @GetMapping("/sync/status")
    public ClusterSyncStatusResponse syncStatus() {
        return clusterOverviewService.syncStatus();
    }

    @PostMapping("/sync/run-once")
    public ClusterSyncStatusResponse runSyncOnce() {
        clusterStateSyncScheduler.pullRemoteStates();
        return clusterOverviewService.syncStatus();
    }
}
