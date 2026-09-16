package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityActivationTarget;
import com.opensocket.aievent.core.enforcement.activation.contract.ClusterSnapshotStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotNodeStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshState;

public final class ClusterSnapshotStatusService {
    private final AuthorityActivationTargetRepository targets;
    private final SnapshotRefreshStatusRepository statuses;
    private final SnapshotRefreshService refresh;
    private final Clock clock;
    private final Duration staleAfter;

    public ClusterSnapshotStatusService(
            AuthorityActivationTargetRepository targets,
            SnapshotRefreshStatusRepository statuses,
            SnapshotRefreshService refresh,
            Clock clock,
            Duration staleAfter) {
        this.targets = Objects.requireNonNull(targets, "targets");
        this.statuses = Objects.requireNonNull(statuses, "statuses");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.staleAfter = Objects.requireNonNull(staleAfter, "staleAfter");
        if (staleAfter.isZero() || staleAfter.isNegative()) {
            throw new IllegalArgumentException("staleAfter must be positive");
        }
    }

    public ClusterSnapshotStatus current() {
        Instant now = clock.instant();
        AuthorityActivationTarget target = targets.current().orElseGet(AuthorityActivationTarget::bootstrap);
        List<SnapshotNodeStatus> nodes = statuses.nodes(now.minus(staleAfter));
        int stale = (int) nodes.stream().filter(SnapshotNodeStatus::stale).count();
        int failed = (int) nodes.stream().filter(node -> node.state() == SnapshotRefreshState.FAILED).count();
        int healthy = (int) nodes.stream().filter(node -> !node.stale()
                && node.state() == SnapshotRefreshState.APPLIED
                && node.activeRevision() == target.targetRevision()
                && node.activeChecksum().equals(target.targetChecksum())).count();
        boolean converged = target.targetRevision() > 0
                && !nodes.isEmpty()
                && healthy == nodes.size()
                && failed == 0
                && stale == 0;
        return new ClusterSnapshotStatus(
                target,
                refresh.current(),
                nodes,
                converged,
                healthy,
                failed,
                stale,
                now);
    }
}
