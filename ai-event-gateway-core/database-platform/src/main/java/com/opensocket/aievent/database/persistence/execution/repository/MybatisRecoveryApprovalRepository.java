package com.opensocket.aievent.database.persistence.execution.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.opensocket.aievent.core.dispatch.RecoveryApprovalRepository;
import com.opensocket.aievent.core.dispatch.RecoveryApprovalRequest;
import com.opensocket.aievent.core.dispatch.RecoveryApprovalStatus;
import com.opensocket.aievent.database.persistence.execution.converter.RecoveryApprovalRequestPersistenceConverter;
import com.opensocket.aievent.database.persistence.execution.dao.RecoveryApprovalRequestDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;

@DatabaseRepositoryAdapter
@ConditionalOnProperty(prefix = "core.recovery.approval", name = "store", havingValue = "MYBATIS")
public class MybatisRecoveryApprovalRepository implements RecoveryApprovalRepository {
    private final RecoveryApprovalRequestDao dao;
    private final RecoveryApprovalRequestPersistenceConverter converter;

    public MybatisRecoveryApprovalRepository(RecoveryApprovalRequestDao dao,
                                             RecoveryApprovalRequestPersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public RecoveryApprovalRequest save(RecoveryApprovalRequest request) {
        dao.upsert(converter.toPo(request));
        return request;
    }

    @Override
    public Optional<RecoveryApprovalRequest> findById(String approvalId) {
        return Optional.ofNullable(dao.findById(approvalId)).map(converter::toRecord);
    }

    @Override
    public List<RecoveryApprovalRequest> findByStatus(RecoveryApprovalStatus status, int limit) {
        return dao.findByStatus(status == null ? null : status.name(), cap(limit)).stream().map(converter::toRecord).toList();
    }

    @Override
    public List<RecoveryApprovalRequest> recent(int limit) {
        return dao.recent(cap(limit)).stream().map(converter::toRecord).toList();
    }

    @Override
    public String mode() {
        return "MYBATIS";
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 1000));
    }
}
