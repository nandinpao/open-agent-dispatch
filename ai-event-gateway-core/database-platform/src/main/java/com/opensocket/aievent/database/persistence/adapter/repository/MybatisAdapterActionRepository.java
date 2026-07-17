package com.opensocket.aievent.database.persistence.adapter.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionRepository;
import com.opensocket.aievent.core.action.AdapterActionStatus;
import com.opensocket.aievent.core.action.AdapterType;
import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.LeaseRenewalRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import com.opensocket.aievent.database.persistence.adapter.converter.AdapterActionPersistenceConverter;
import com.opensocket.aievent.database.persistence.adapter.dao.AdapterActionDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "adapter-actions", name = "store", havingValue = "MYBATIS")
public class MybatisAdapterActionRepository implements AdapterActionRepository {
    private final AdapterActionDao dao;
    private final AdapterActionPersistenceConverter converter;

    public MybatisAdapterActionRepository(
            AdapterActionDao dao,
            AdapterActionPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public AdapterAction save(AdapterAction action) {
        dao.upsert(converter.toPo(action));
        return action;
    }

    @Override
    public AdapterAction saveNewOrGetByIdempotencyKey(AdapterAction action) {
        try {
            dao.insert(converter.toPo(action));
            return action;
        } catch (DuplicateKeyException exception) {
            return findByIdempotencyKey(action.getIdempotencyKey()).orElseThrow(() -> exception);
        }
    }

    @Override
    public Optional<AdapterAction> findById(String actionId) {
        return Optional.ofNullable(dao.findById(actionId)).map(converter::toAction);
    }

    @Override
    public Optional<AdapterAction> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(dao.findByIdempotencyKey(idempotencyKey)).map(converter::toAction);
    }

    @Override
    public List<AdapterAction> findByIncidentId(String incidentId, int limit) {
        return dao.findByIncidentId(incidentId, cap(limit)).stream().map(converter::toAction).toList();
    }

    @Override
    public List<AdapterAction> findByTaskId(String taskId, int limit) {
        return dao.findByTaskId(taskId, cap(limit)).stream().map(converter::toAction).toList();
    }

    @Override
    public List<AdapterAction> findByStatus(AdapterActionStatus status, int limit) {
        return dao.findByStatus(status.name(), cap(limit)).stream().map(converter::toAction).toList();
    }

    @Override
    public List<AdapterAction> findExecutablePending(OffsetDateTime now, int limit) {
        return dao.findExecutablePending(now, cap(limit)).stream().map(converter::toAction).toList();
    }

    @Override
    public Optional<AdapterAction> claimNext(AdapterType adapterType, ClaimRequest request) {
        return Optional.ofNullable(dao.claimNext(
                        adapterType.name(),
                        request.workerId(),
                        request.now(),
                        request.claimUntil()))
                .map(converter::toAction);
    }

    @Override
    public Optional<AdapterAction> extendLease(LeaseRenewalRequest request) {
        return Optional.ofNullable(dao.extendLease(
                        request.resourceId(),
                        request.ownership().workerId(),
                        request.ownership().claimUntil(),
                        request.now(),
                        request.newClaimUntil()))
                .map(converter::toAction);
    }

    @Override
    public PersistenceWriteResult saveClaimed(
            AdapterAction action,
            ClaimOwnership ownership,
            OffsetDateTime now) {
        int rows = dao.saveClaimed(
                converter.toPo(action),
                ownership.workerId(),
                ownership.claimUntil(),
                now);
        return result(action.getActionId(), rows);
    }

    @Override
    public PersistenceWriteResult recoverExpiredClaim(
            AdapterAction action,
            ClaimOwnership ownership,
            OffsetDateTime observedAt) {
        int rows = dao.recoverExpiredClaim(
                converter.toPo(action),
                ownership.workerId(),
                ownership.claimUntil(),
                observedAt);
        return result(action.getActionId(), rows);
    }

    @Override
    public List<AdapterAction> recent(int limit) {
        return dao.recent(cap(limit)).stream().map(converter::toAction).toList();
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }

    private PersistenceWriteResult result(String id, int rows) {
        return rows > 0
                ? PersistenceWriteResult.applied(id, rows)
                : PersistenceWriteResult.ownershipLost(id);
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit, 1000));
    }
}
