package com.opensocket.aievent.core.enforcement.activation.contract;

import java.time.Instant;
import java.util.List;

/** Operator-facing convergence status for the current cluster target. */
public record ClusterSnapshotStatus(
        AuthorityActivationTarget target,
        SnapshotRefreshStatus local,
        List<SnapshotNodeStatus> nodes,
        boolean converged,
        int healthyNodes,
        int failedNodes,
        int staleNodes,
        Instant evaluatedAt) {

    public ClusterSnapshotStatus {
        if (target == null) throw new IllegalArgumentException("target is required");
        if (local == null) throw new IllegalArgumentException("local is required");
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        if (healthyNodes < 0 || failedNodes < 0 || staleNodes < 0) {
            throw new IllegalArgumentException("node counts must not be negative");
        }
        if (evaluatedAt == null) throw new IllegalArgumentException("evaluatedAt is required");
    }
}
