package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Clock;
import java.util.Objects;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshState;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshStatus;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshot;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotLoader;
import com.opensocket.aievent.core.enforcement.activation.core.RevisionedAuthorityRouter;

public final class SnapshotRefreshService {
    private final AuthoritySnapshotLoader loader;
    private final RevisionedAuthorityRouter router;
    private final SnapshotRefreshStatusRepository repository;
    private final Clock clock;

    public SnapshotRefreshService(
            AuthoritySnapshotLoader loader,
            RevisionedAuthorityRouter router,
            SnapshotRefreshStatusRepository repository,
            Clock clock) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.router = Objects.requireNonNull(router, "router");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SnapshotRefreshStatus refresh(long attemptedRevision, String actorId, String correlationId) {
        AuthoritySnapshot previous = router.currentSnapshot();
        try {
            AuthoritySnapshot applied = loader.loadPublished(attemptedRevision);
            SnapshotRefreshStatus status = applied(applied, attemptedRevision);
            repository.save(status, actorId, correlationId);
            return status;
        } catch (RuntimeException exception) {
            AuthoritySnapshot active = router.currentSnapshot();
            if (active.revision() != previous.revision()) {
                router.restoreAfterFailedActivation(active.revision(), previous);
            }
            AuthoritySnapshot retained = router.currentSnapshot();
            SnapshotRefreshStatus status = failed(
                    retained,
                    attemptedRevision,
                    "AUTHORITY_SNAPSHOT_REFRESH_FAILED",
                    safe(exception.getMessage()));
            try {
                repository.save(status, actorId, correlationId);
            } catch (RuntimeException persistenceFailure) {
                persistenceFailure.addSuppressed(exception);
                throw persistenceFailure;
            }
            return status;
        }
    }

    public SnapshotRefreshStatus heartbeat(String actorId, String correlationId) {
        AuthoritySnapshot active = router.currentSnapshot();
        SnapshotRefreshStatus status = new SnapshotRefreshStatus(
                active.revision() == 0 ? SnapshotRefreshState.BOOTSTRAP : SnapshotRefreshState.APPLIED,
                active.revision(),
                active.revision(),
                active.revision(),
                active.checksum(),
                "",
                "",
                clock.instant());
        repository.save(status, actorId, correlationId);
        return status;
    }

    public SnapshotRefreshStatus recordFailure(
            long attemptedRevision,
            String failureCode,
            String failureMessage,
            String actorId,
            String correlationId) {
        AuthoritySnapshot retained = router.currentSnapshot();
        SnapshotRefreshStatus status = failed(
                retained,
                attemptedRevision,
                failureCode,
                safe(failureMessage));
        repository.save(status, actorId, correlationId);
        return status;
    }

    public SnapshotRefreshStatus current() {
        return repository.current().orElseGet(() -> {
            AuthoritySnapshot snapshot = router.currentSnapshot();
            return new SnapshotRefreshStatus(
                    SnapshotRefreshState.BOOTSTRAP,
                    snapshot.revision(),
                    snapshot.revision(),
                    snapshot.revision(),
                    snapshot.checksum(),
                    "",
                    "",
                    clock.instant());
        });
    }

    private SnapshotRefreshStatus applied(AuthoritySnapshot applied, long attemptedRevision) {
        return new SnapshotRefreshStatus(
                SnapshotRefreshState.APPLIED,
                applied.revision(),
                attemptedRevision,
                applied.revision(),
                applied.checksum(),
                "",
                "",
                clock.instant());
    }

    private SnapshotRefreshStatus failed(
            AuthoritySnapshot retained,
            long attemptedRevision,
            String code,
            String message) {
        return new SnapshotRefreshStatus(
                SnapshotRefreshState.FAILED,
                retained.revision(),
                attemptedRevision,
                retained.revision(),
                retained.checksum(),
                code,
                message,
                clock.instant());
    }

    private static String safe(String value) {
        if (value == null) return "Unknown snapshot refresh failure";
        return value.substring(0, Math.min(value.length(), 1_000));
    }
}
