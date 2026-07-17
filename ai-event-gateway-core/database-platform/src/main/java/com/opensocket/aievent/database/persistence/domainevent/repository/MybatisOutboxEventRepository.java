package com.opensocket.aievent.database.persistence.domainevent.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import com.opensocket.aievent.core.outbox.OutboxEventRecord;
import com.opensocket.aievent.core.outbox.OutboxEventRepository;
import com.opensocket.aievent.database.persistence.domainevent.converter.OutboxEventPersistenceConverter;
import com.opensocket.aievent.database.persistence.domainevent.dao.OutboxEventDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "core.outbox", name = "store", havingValue = "MYBATIS")
public class MybatisOutboxEventRepository implements OutboxEventRepository {
    private final OutboxEventDao dao;
    private final OutboxEventPersistenceConverter converter;

    public MybatisOutboxEventRepository(
            OutboxEventDao dao,
            OutboxEventPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public OutboxEventRecord save(OutboxEventRecord event) {
        dao.insertIgnore(converter.toPo(event));
        return findByEventId(event.getEventId()).orElseThrow();
    }

    @Override
    public Optional<OutboxEventRecord> findById(String id) {
        return Optional.ofNullable(dao.findById(id)).map(converter::toDomain);
    }

    @Override
    public Optional<OutboxEventRecord> findByEventId(String id) {
        return Optional.ofNullable(dao.findByEventId(id)).map(converter::toDomain);
    }

    @Override
    public List<OutboxEventRecord> claimDispatchable(ClaimRequest request) {
        return dao.claimDispatchable(
                        request.now(),
                        request.limit(),
                        request.workerId(),
                        request.claimUntil())
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public PersistenceWriteResult markPublished(
            String id,
            ClaimOwnership ownership,
            OffsetDateTime publishedAt) {
        return result(id, dao.markPublished(id, ownership.workerId(), ownership.claimUntil(), publishedAt));
    }

    @Override
    public PersistenceWriteResult markRetry(
            String id,
            ClaimOwnership ownership,
            int attempts,
            OffsetDateTime nextAttemptAt,
            String error,
            OffsetDateTime updatedAt) {
        return result(id, dao.markRetry(
                id,
                ownership.workerId(),
                ownership.claimUntil(),
                attempts,
                nextAttemptAt,
                error,
                updatedAt));
    }

    @Override
    public PersistenceWriteResult markDeadLetter(
            String id,
            ClaimOwnership ownership,
            int attempts,
            String error,
            OffsetDateTime updatedAt) {
        return result(id, dao.markDeadLetter(
                id,
                ownership.workerId(),
                ownership.claimUntil(),
                attempts,
                error,
                updatedAt));
    }

    @Override
    public List<OutboxEventRecord> recent(int limit) {
        return dao.recent(cap(limit)).stream().map(converter::toDomain).toList();
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
