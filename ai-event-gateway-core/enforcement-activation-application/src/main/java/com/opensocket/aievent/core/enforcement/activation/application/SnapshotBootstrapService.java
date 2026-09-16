package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshState;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshStatus;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshot;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotLoader;
import com.opensocket.aievent.core.enforcement.activation.core.RevisionedAuthorityRouter;

/** Restores the persisted Last-known-good authority revision after a process restart. */
public final class SnapshotBootstrapService {
    private final AuthoritySnapshotLoader loader;
    private final RevisionedAuthorityRouter router;
    private final SnapshotRefreshStatusRepository repository;
    private final Clock clock;

    public SnapshotBootstrapService(
            AuthoritySnapshotLoader loader,
            RevisionedAuthorityRouter router,
            SnapshotRefreshStatusRepository repository,
            Clock clock) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.router = Objects.requireNonNull(router, "router");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SnapshotRefreshStatus bootstrap(String actorId, String correlationId) {
        Optional<SnapshotRefreshStatus> persisted = repository.current();
        long desiredRevision = persisted
                .map(SnapshotRefreshStatus::lastKnownGoodRevision)
                .filter(revision -> revision > 0)
                .orElse(0L);
        try {
            Optional<AuthoritySnapshot> candidate = desiredRevision > 0
                    ? Optional.of(loader.loadPublished(desiredRevision))
                    : loader.loadLatestPublishedIfPresent();
            if (candidate.isEmpty()) {
                AuthoritySnapshot current = router.currentSnapshot();
                return new SnapshotRefreshStatus(
                        SnapshotRefreshState.BOOTSTRAP,
                        current.revision(),
                        current.revision(),
                        current.revision(),
                        current.checksum(),
                        "",
                        "",
                        clock.instant());
            }
            AuthoritySnapshot applied = candidate.orElseThrow();
            SnapshotRefreshStatus status = new SnapshotRefreshStatus(
                    SnapshotRefreshState.APPLIED,
                    applied.revision(),
                    applied.revision(),
                    applied.revision(),
                    applied.checksum(),
                    "",
                    "",
                    clock.instant());
            repository.save(status, actorId, correlationId);
            return status;
        } catch (RuntimeException exception) {
            AuthoritySnapshot retained = router.currentSnapshot();
            long retainedLastKnownGood = desiredRevision > 0
                    ? desiredRevision
                    : retained.revision();
            SnapshotRefreshStatus status = new SnapshotRefreshStatus(
                    SnapshotRefreshState.FAILED,
                    retained.revision(),
                    desiredRevision,
                    retainedLastKnownGood,
                    retained.checksum(),
                    "AUTHORITY_SNAPSHOT_BOOTSTRAP_FAILED",
                    safe(exception.getMessage()),
                    clock.instant());
            repository.save(status, actorId, correlationId);
            return status;
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "Unknown snapshot bootstrap failure";
        }
        return value.substring(0, Math.min(value.length(), 1_000));
    }
}
