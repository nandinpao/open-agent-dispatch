package com.opensocket.aievent.core.integration.handoff;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "handoff-context", name = "store", havingValue = "MEMORY", matchIfMissing = true)
public class InMemoryHandoffContextRepository implements HandoffContextRepository {
    private final Map<String, HandoffContextPolicy> policies = new ConcurrentHashMap<>();
    private final Map<String, HandoffContextSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, HandoffContextApproval> approvals = new ConcurrentHashMap<>();
    private final Map<String, ResultContextSnapshot> results = new ConcurrentHashMap<>();
    private final Map<String, AgentContextAccessEvent> access = new ConcurrentHashMap<>();
    private final Map<String, HandoffReleaseEvidence> releaseEvidence = new ConcurrentHashMap<>();

    @Override
    public HandoffContextPolicy savePolicy(HandoffContextPolicy value) {
        policies.put(key(value.tenantId(), value.policyId()), value);
        return value;
    }

    @Override
    public Optional<HandoffContextPolicy> findPolicy(String tenantId, String policyId) {
        return Optional.ofNullable(policies.get(key(tenantId, policyId)));
    }

    @Override
    public List<HandoffContextPolicy> listPolicies(String tenantId, int limit) {
        return policies.values().stream()
                .filter(value -> tenantId.equals(value.tenantId()))
                .sorted(Comparator.comparing(HandoffContextPolicy::policyId))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized HandoffContextSnapshot saveSnapshot(HandoffContextSnapshot value) {
        String key = key(value.tenantId(), value.snapshotId());
        HandoffContextSnapshot current = snapshots.get(key);
        if (current != null && value.rowVersion() != current.rowVersion() + 1L
                && value.rowVersion() != current.rowVersion()) {
            throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        }
        snapshots.put(key, value);
        return value;
    }

    @Override
    public Optional<HandoffContextSnapshot> findSnapshot(String tenantId, String snapshotId) {
        return Optional.ofNullable(snapshots.get(key(tenantId, snapshotId)));
    }

    @Override
    public Optional<HandoffContextSnapshot> latestApprovedSnapshotForTask(String tenantId, String taskId) {
        return snapshots.values().stream()
                .filter(value -> tenantId.equals(value.tenantId())
                        && taskId.equals(value.targetTaskId())
                        && value.status() == HandoffSnapshotStatus.APPROVED)
                .max(Comparator.comparingInt(HandoffContextSnapshot::snapshotVersion));
    }

    @Override
    public List<HandoffContextSnapshot> listSnapshotsForTask(String tenantId, String taskId, int limit) {
        return snapshots.values().stream()
                .filter(value -> tenantId.equals(value.tenantId())
                        && (taskId.equals(value.sourceTaskId()) || taskId.equals(value.targetTaskId())))
                .sorted(Comparator.comparingInt(HandoffContextSnapshot::snapshotVersion).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<HandoffContextSnapshot> findReleaseCandidates(OffsetDateTime now, int limit) {
        return snapshots.values().stream()
                .filter(value -> value.releaseStatus() == HandoffSnapshotReleaseStatus.READY
                        || value.releaseStatus() == HandoffSnapshotReleaseStatus.RELEASING
                        || value.releaseStatus() == HandoffSnapshotReleaseStatus.FAILED_RETRYABLE)
                .filter(value -> value.nextReconcileAt() == null || !value.nextReconcileAt().isAfter(now))
                .sorted(Comparator.comparing(value -> Optional.ofNullable(value.nextReconcileAt())
                        .orElse(value.createdAt())))
                .limit(limit)
                .toList();
    }

    @Override
    public HandoffReleaseEvidence saveReleaseEvidence(HandoffReleaseEvidence value) {
        releaseEvidence.putIfAbsent(key(value.tenantId(), value.evidenceId()), value);
        return releaseEvidence.get(key(value.tenantId(), value.evidenceId()));
    }

    @Override
    public List<HandoffReleaseEvidence> listReleaseEvidence(String tenantId, String snapshotId, int limit) {
        return releaseEvidence.values().stream()
                .filter(value -> tenantId.equals(value.tenantId()) && snapshotId.equals(value.snapshotId()))
                .sorted(Comparator.comparing(HandoffReleaseEvidence::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public HandoffContextApproval saveApproval(HandoffContextApproval value) {
        approvals.put(key(value.tenantId(), value.approvalId()), value);
        return value;
    }

    @Override
    public List<HandoffContextApproval> listApprovals(String tenantId, String snapshotId, int limit) {
        return approvals.values().stream()
                .filter(value -> tenantId.equals(value.tenantId()) && snapshotId.equals(value.snapshotId()))
                .sorted(Comparator.comparing(HandoffContextApproval::decidedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public ResultContextSnapshot saveResultSnapshot(ResultContextSnapshot value) {
        results.put(key(value.tenantId(), value.resultSnapshotId()), value);
        return value;
    }

    @Override
    public Optional<ResultContextSnapshot> findResultSnapshot(String tenantId, String snapshotId) {
        return Optional.ofNullable(results.get(key(tenantId, snapshotId)));
    }

    @Override
    public List<ResultContextSnapshot> listResultSnapshots(String tenantId, String taskId, int limit) {
        return results.values().stream()
                .filter(value -> tenantId.equals(value.tenantId())
                        && (taskId.equals(value.sourceTaskId()) || taskId.equals(value.targetTaskId())))
                .sorted(Comparator.comparingInt(ResultContextSnapshot::snapshotVersion).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public AgentContextAccessEvent saveAccessEvent(AgentContextAccessEvent value) {
        access.put(key(value.tenantId(), value.accessEventId()), value);
        return value;
    }

    @Override
    public List<AgentContextAccessEvent> listAccessEvents(String tenantId, String taskId, int limit) {
        return access.values().stream()
                .filter(value -> tenantId.equals(value.tenantId())
                        && (taskId == null || taskId.isBlank() || taskId.equals(value.taskId())))
                .sorted(Comparator.comparing(AgentContextAccessEvent::accessedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private String key(String tenantId, String id) {
        return tenantId + ":" + id;
    }
}
