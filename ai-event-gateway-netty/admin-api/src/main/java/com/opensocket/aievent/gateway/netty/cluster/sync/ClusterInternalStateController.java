package com.opensocket.aievent.gateway.netty.cluster.sync;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cluster state synchronization component for Cluster Internal State Controller. It exposes or
 * consumes lightweight node state snapshots so Admin UI can show a cluster-wide view without
 * moving business ownership across nodes.
 */
@RestController
@RequestMapping("/internal/cluster")
public class ClusterInternalStateController {

    private final ClusterStateSnapshotService clusterStateSnapshotService;

    public ClusterInternalStateController(ClusterStateSnapshotService clusterStateSnapshotService) {
        this.clusterStateSnapshotService = clusterStateSnapshotService;
    }

    @GetMapping("/state")
    public ClusterStateSnapshotResponse state() {
        return clusterStateSnapshotService.localSnapshot();
    }
}
