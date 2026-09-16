package com.opensocket.aievent.core.integration.handoff;

import java.util.List;
import java.util.Optional;

/** Read-only boundary exposed to downstream projections. */
public interface HandoffSnapshotReadPort {
    Optional<HandoffContextSnapshot> findSnapshot(String tenantId, String snapshotId);
    Optional<HandoffContextSnapshot> latestApprovedSnapshotForTask(String tenantId, String taskId);
    List<HandoffContextSnapshot> listSnapshotsForTask(String tenantId, String taskId, int limit);
    Optional<ResultContextSnapshot> findResultSnapshot(String tenantId, String resultSnapshotId);
    List<ResultContextSnapshot> listResultSnapshots(String tenantId, String taskId, int limit);
}
