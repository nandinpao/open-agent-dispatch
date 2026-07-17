package com.opensocket.aievent.database.persistence.integrationevent.repository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.integration.IntegrationEventRecord;
import com.opensocket.aievent.core.integration.IntegrationEventRepository;
import com.opensocket.aievent.core.kernel.persistence.ClaimOwnership;
import com.opensocket.aievent.core.kernel.persistence.ClaimRequest;
import com.opensocket.aievent.core.kernel.persistence.PersistenceWriteResult;
import com.opensocket.aievent.database.persistence.integrationevent.converter.IntegrationEventPersistenceConverter;
import com.opensocket.aievent.database.persistence.integrationevent.dao.IntegrationEventDao;
import com.opensocket.aievent.database.persistence.integrationevent.po.IntegrationEventPo;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "core.integration-events", name = "store", havingValue = "MYBATIS")
public class MybatisIntegrationEventRepository implements IntegrationEventRepository {
    private final IntegrationEventDao dao;
    private final IntegrationEventPersistenceConverter converter;

    public MybatisIntegrationEventRepository(
            IntegrationEventDao dao,
            IntegrationEventPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public IntegrationEventRecord save(IntegrationEventRecord event) {
        dao.insertIgnore(converter.toPo(event));
        IntegrationEventPo po = dao.findByEventId(event.getEventId());
        if (po == null) {
            throw new IllegalStateException("Integration event was not persisted: " + event.getEventId());
        }
        return converter.toDomain(po);
    }

    @Override
    public List<IntegrationEventRecord> claimDispatchable(ClaimRequest request) {
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
    public PersistenceWriteResult markDelivered(
            String id,
            ClaimOwnership ownership,
            OffsetDateTime deliveredAt) {
        return result(id, dao.markDelivered(id, ownership.workerId(), ownership.claimUntil(), deliveredAt));
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
    public Map<String, Integer> statusCounts(int limit) {
        Map<String, Integer> result = new LinkedHashMap<>();
        dao.statusCounts().forEach(po -> result.put(po.getStatus(), po.getTotal()));
        return result;
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
}
