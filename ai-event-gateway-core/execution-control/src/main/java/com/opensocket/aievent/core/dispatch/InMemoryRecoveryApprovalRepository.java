package com.opensocket.aievent.core.dispatch;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "core.recovery.approval", name = "store", havingValue = "MEMORY", matchIfMissing = true)
public class InMemoryRecoveryApprovalRepository implements RecoveryApprovalRepository {
    private final ConcurrentMap<String, RecoveryApprovalRequest> records = new ConcurrentHashMap<>();

    @Override
    public RecoveryApprovalRequest save(RecoveryApprovalRequest request) {
        records.put(request.getApprovalId(), request);
        return request;
    }

    @Override
    public Optional<RecoveryApprovalRequest> findById(String approvalId) {
        return Optional.ofNullable(records.get(approvalId));
    }

    @Override
    public List<RecoveryApprovalRequest> findByStatus(RecoveryApprovalStatus status, int limit) {
        return records.values().stream()
                .filter(record -> status == null || status == record.getStatus())
                .sorted(Comparator.comparing(RecoveryApprovalRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap(limit))
                .toList();
    }

    @Override
    public List<RecoveryApprovalRequest> recent(int limit) {
        return records.values().stream()
                .sorted(Comparator.comparing(RecoveryApprovalRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(cap(limit))
                .toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private int cap(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 100 : limit, 1000));
    }
}
