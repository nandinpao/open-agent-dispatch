package com.opensocket.aievent.core.enforcement.activation.application;

import java.util.Objects;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityActivationTarget;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshState;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshStatus;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshot;
import com.opensocket.aievent.core.enforcement.activation.core.RevisionedAuthorityRouter;

/** Polling fallback that makes every Runtime node converge to the cluster target revision. */
public final class AuthorityRevisionSynchronizationService {
    private final AuthorityActivationTargetRepository targets;
    private final SnapshotRefreshService refresh;
    private final RevisionedAuthorityRouter router;

    public AuthorityRevisionSynchronizationService(
            AuthorityActivationTargetRepository targets,
            SnapshotRefreshService refresh,
            RevisionedAuthorityRouter router) {
        this.targets = Objects.requireNonNull(targets, "targets");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        this.router = Objects.requireNonNull(router, "router");
    }

    public SnapshotRefreshStatus synchronize(String actorId, String correlationId) {
        AuthorityActivationTarget target = targets.current().orElseGet(AuthorityActivationTarget::bootstrap);
        long activeRevision = router.currentRevision();
        if (target.targetRevision() == 0) {
            return refresh.heartbeat(actorId, correlationId);
        }
        if (target.targetRevision() < activeRevision) {
            return refresh.recordFailure(
                    target.targetRevision(),
                    "AUTHORITY_TARGET_REVISION_REGRESSION",
                    "Cluster target revision is lower than the active local revision; Phase 6C-0 does not permit implicit rollback.",
                    actorId,
                    correlationId);
        }
        if (target.targetRevision() == activeRevision
                && target.targetChecksum().equals(router.currentChecksum())) {
            return refresh.heartbeat(actorId, correlationId);
        }
        AuthoritySnapshot previous = router.currentSnapshot();
        SnapshotRefreshStatus result = refresh.refresh(target.targetRevision(), actorId, correlationId);
        if (result.state() == SnapshotRefreshState.APPLIED
                && !target.targetChecksum().equals(result.activeChecksum())) {
            router.restoreAfterFailedActivation(result.activeRevision(), previous);
            return refresh.recordFailure(
                    target.targetRevision(),
                    "AUTHORITY_TARGET_CHECKSUM_MISMATCH",
                    "The activated local checksum does not match the cluster target checksum.",
                    actorId,
                    correlationId);
        }
        return result;
    }
}
