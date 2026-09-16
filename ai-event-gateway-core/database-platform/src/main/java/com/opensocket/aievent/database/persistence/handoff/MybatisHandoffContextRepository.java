package com.opensocket.aievent.database.persistence.handoff;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.integration.handoff.AgentContextAccessEvent;
import com.opensocket.aievent.core.integration.handoff.HandoffContextApproval;
import com.opensocket.aievent.core.integration.handoff.HandoffContextPolicy;
import com.opensocket.aievent.core.integration.handoff.HandoffContextRepository;
import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;
import com.opensocket.aievent.core.integration.handoff.HandoffReleaseEvidence;
import com.opensocket.aievent.core.integration.handoff.ResultContextSnapshot;
import com.opensocket.aievent.database.persistence.handoff.dao.HandoffContextDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "handoff-context", name = "store", havingValue = "MYBATIS")
public class MybatisHandoffContextRepository implements HandoffContextRepository {
    private final HandoffContextDao dao;
    private final HandoffContextPersistenceConverter converter;

    public MybatisHandoffContextRepository(
            HandoffContextDao dao,
            HandoffContextPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public HandoffContextPolicy savePolicy(HandoffContextPolicy value) {
        dao.upsertPolicy(converter.toPo(value));
        return findPolicy(value.tenantId(), value.policyId()).orElse(value);
    }

    @Override
    public Optional<HandoffContextPolicy> findPolicy(String tenantId, String policyId) {
        return Optional.ofNullable(dao.findPolicy(tenantId, policyId)).map(converter::policy);
    }

    @Override
    public List<HandoffContextPolicy> listPolicies(String tenantId, int limit) {
        return dao.listPolicies(tenantId, limit).stream().map(converter::policy).toList();
    }

    @Override
    public HandoffContextSnapshot saveSnapshot(HandoffContextSnapshot value) {
        var existing = dao.findSnapshot(value.tenantId(), value.snapshotId());
        if (existing == null) {
            dao.insertSnapshot(converter.toPo(value));
            for (var field : value.fieldDecisions()) {
                dao.insertFieldDecision(converter.toPo(value.tenantId(), value.snapshotId(), field));
            }
        } else if (dao.updateSnapshotState(converter.toPo(value)) != 1) {
            throw new IllegalStateException("RESOURCE_VERSION_CONFLICT: Handoff Snapshot " + value.snapshotId());
        }
        return findSnapshot(value.tenantId(), value.snapshotId()).orElse(value);
    }

    @Override
    public Optional<HandoffContextSnapshot> findSnapshot(String tenantId, String snapshotId) {
        var value = dao.findSnapshot(tenantId, snapshotId);
        return value == null ? Optional.empty()
                : Optional.of(converter.snapshot(value, dao.listFieldDecisions(tenantId, snapshotId)));
    }

    @Override
    public Optional<HandoffContextSnapshot> latestApprovedSnapshotForTask(String tenantId, String taskId) {
        var value = dao.latestApprovedSnapshotForTask(tenantId, taskId);
        return value == null ? Optional.empty()
                : Optional.of(converter.snapshot(value,
                        dao.listFieldDecisions(tenantId, value.getSnapshotId())));
    }

    @Override
    public List<HandoffContextSnapshot> listSnapshotsForTask(String tenantId, String taskId, int limit) {
        return dao.listSnapshotsForTask(tenantId, taskId, limit).stream()
                .map(value -> converter.snapshot(value,
                        dao.listFieldDecisions(tenantId, value.getSnapshotId())))
                .toList();
    }

    @Override
    public List<HandoffContextSnapshot> findReleaseCandidates(OffsetDateTime now, int limit) {
        return dao.findReleaseCandidates(now, limit).stream()
                .map(value -> converter.snapshot(value,
                        dao.listFieldDecisions(value.getTenantId(), value.getSnapshotId())))
                .toList();
    }

    @Override
    public HandoffReleaseEvidence saveReleaseEvidence(HandoffReleaseEvidence value) {
        dao.insertReleaseEvidence(converter.toPo(value));
        return value;
    }

    @Override
    public List<HandoffReleaseEvidence> listReleaseEvidence(
            String tenantId, String snapshotId, int limit) {
        return dao.listReleaseEvidence(tenantId, snapshotId, limit).stream()
                .map(converter::releaseEvidence)
                .toList();
    }

    @Override
    public HandoffContextApproval saveApproval(HandoffContextApproval value) {
        dao.insertApproval(converter.toPo(value));
        return value;
    }

    @Override
    public List<HandoffContextApproval> listApprovals(String tenantId, String snapshotId, int limit) {
        return dao.listApprovals(tenantId, snapshotId, limit).stream()
                .map(converter::approval)
                .toList();
    }

    @Override
    public ResultContextSnapshot saveResultSnapshot(ResultContextSnapshot value) {
        dao.insertResultSnapshot(converter.toPo(value));
        return value;
    }

    @Override
    public Optional<ResultContextSnapshot> findResultSnapshot(String tenantId, String resultSnapshotId) {
        return Optional.ofNullable(dao.findResultSnapshot(tenantId, resultSnapshotId))
                .map(converter::result);
    }

    @Override
    public List<ResultContextSnapshot> listResultSnapshots(String tenantId, String taskId, int limit) {
        return dao.listResultSnapshots(tenantId, taskId, limit).stream()
                .map(converter::result)
                .toList();
    }

    @Override
    public AgentContextAccessEvent saveAccessEvent(AgentContextAccessEvent value) {
        dao.insertAccessEvent(converter.toPo(value));
        return value;
    }

    @Override
    public List<AgentContextAccessEvent> listAccessEvents(String tenantId, String taskId, int limit) {
        return dao.listAccessEvents(tenantId, taskId, limit).stream()
                .map(converter::access)
                .toList();
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }
}
