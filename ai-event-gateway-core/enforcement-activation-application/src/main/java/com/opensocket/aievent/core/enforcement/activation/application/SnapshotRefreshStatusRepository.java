package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotNodeStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshStatus;

public interface SnapshotRefreshStatusRepository {
    Optional<SnapshotRefreshStatus> current();
    void save(SnapshotRefreshStatus status, String actorId, String correlationId);
    default List<SnapshotNodeStatus> nodes(Instant staleBefore) { return List.of(); }
    default String nodeId() { return "local"; }
}
