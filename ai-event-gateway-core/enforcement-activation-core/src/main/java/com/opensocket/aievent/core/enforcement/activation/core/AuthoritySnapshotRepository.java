package com.opensocket.aievent.core.enforcement.activation.core;

import java.util.Optional;

public interface AuthoritySnapshotRepository {
    Optional<AuthoritySnapshotData> findLatestPublished();
    Optional<AuthoritySnapshotData> findPublished(long revision);
}
