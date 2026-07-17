package com.opensocket.aievent.database.persistence.eventprocessing.repository;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.dedup.DedupState;
import com.opensocket.aievent.core.dedup.snapshot.DedupStateSnapshotRepository;
import com.opensocket.aievent.core.event.NormalizedEvent;
import com.opensocket.aievent.database.persistence.eventprocessing.converter.DedupStateSnapshotPersistenceConverter;
import com.opensocket.aievent.database.persistence.eventprocessing.dao.DedupStateSnapshotDao;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "event.dedup", name = "snapshot-store", havingValue = "MYBATIS")
public class MybatisDedupStateSnapshotRepository implements DedupStateSnapshotRepository {
    private final DedupStateSnapshotDao dao;
    private final DedupStateSnapshotPersistenceConverter converter;

    public MybatisDedupStateSnapshotRepository(
            DedupStateSnapshotDao dao,
            DedupStateSnapshotPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public void saveSnapshot(DedupState state, NormalizedEvent event, Duration ttl) {
        dao.upsert(converter.toPo(state, event, ttl));
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }
}
