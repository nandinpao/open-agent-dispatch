package com.opensocket.aievent.core.enforcement.activation.core;

import java.util.Objects;
import java.util.Optional;

public final class AuthoritySnapshotLoader {
    private final AuthoritySnapshotRepository repository;
    private final RevisionedAuthorityRouter router;
    private final AuthoritySnapshotFactory factory;

    public AuthoritySnapshotLoader(
            AuthoritySnapshotRepository repository,
            RevisionedAuthorityRouter router) {
        this(repository, router, new AuthoritySnapshotFactory());
    }

    public AuthoritySnapshotLoader(
            AuthoritySnapshotRepository repository,
            RevisionedAuthorityRouter router,
            AuthoritySnapshotFactory factory) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.router = Objects.requireNonNull(router, "router");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public AuthoritySnapshot loadLatestPublished() {
        return loadLatestPublishedIfPresent()
                .orElseThrow(() -> new IllegalStateException("no published authority snapshot is available"));
    }

    public Optional<AuthoritySnapshot> loadLatestPublishedIfPresent() {
        return repository.findLatestPublished().map(this::activate);
    }

    public AuthoritySnapshot loadPublished(long revision) {
        AuthoritySnapshotData data = repository.findPublished(revision)
                .orElseThrow(() -> new IllegalStateException(
                        "published authority snapshot is unavailable for revision " + revision));
        if (data.revision() != revision) {
            throw new IllegalStateException("authority snapshot repository returned a different revision");
        }
        return activate(data);
    }

    private AuthoritySnapshot activate(AuthoritySnapshotData data) {
        AuthoritySnapshot current = router.currentSnapshot();
        if (data.revision() == current.revision()) {
            AuthoritySnapshot candidate = factory.create(data);
            if (!candidate.checksum().equals(current.checksum())) {
                throw new IllegalStateException(
                        "published authority revision changed without a revision increment");
            }
            return current;
        }
        return router.publish(data);
    }
}
