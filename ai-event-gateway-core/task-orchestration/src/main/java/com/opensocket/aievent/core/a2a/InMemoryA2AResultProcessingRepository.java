package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MEMORY", matchIfMissing = true)
public class InMemoryA2AResultProcessingRepository implements A2AResultProcessingRepository {
    private final Map<String, A2AResultProcessing> values = new ConcurrentHashMap<>();

    @Override
    public synchronized A2AResultProcessing save(A2AResultProcessing value) {
        values.putIfAbsent(key(value.getTenantId(), value.getResultId()), copy(value));
        return copy(values.get(key(value.getTenantId(), value.getResultId())));
    }

    @Override
    public synchronized A2AResultProcessing saveExpectedVersion(A2AResultProcessing value, long expectedVersion) {
        String key = key(value.getTenantId(), value.getResultId());
        A2AResultProcessing current = values.get(key);
        if (current == null || current.getRowVersion() != expectedVersion) {
            throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        }
        values.put(key, copy(value));
        return copy(value);
    }

    @Override
    public Optional<A2AResultProcessing> findByResult(String tenantId, String resultId) {
        return Optional.ofNullable(values.get(key(tenantId, resultId))).map(this::copy);
    }

    @Override
    public List<A2AResultProcessing> findDue(OffsetDateTime at, int limit) {
        return values.values().stream()
                .filter(value -> value.getNextReconcileAt() == null || !value.getNextReconcileAt().isAfter(at))
                .limit(limit)
                .map(this::copy)
                .toList();
    }

    @Override
    public List<A2AResultProcessing> findByTask(String tenantId, String taskId, int limit) {
        return values.values().stream()
                .filter(value -> Objects.equals(tenantId, value.getTenantId())
                        && (Objects.equals(taskId, value.getParentTaskId())
                                || Objects.equals(taskId, value.getChildTaskId())))
                .limit(limit)
                .map(this::copy)
                .toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private String key(String tenantId, String resultId) {
        return tenantId + "|" + resultId;
    }

    private A2AResultProcessing copy(A2AResultProcessing source) {
        A2AResultProcessing value = new A2AResultProcessing();
        value.setTenantId(source.getTenantId());
        value.setResultId(source.getResultId());
        value.setRequestId(source.getRequestId());
        value.setParentTaskId(source.getParentTaskId());
        value.setChildTaskId(source.getChildTaskId());
        value.setProcessingStatus(source.getProcessingStatus());
        value.setReconciliationClassification(source.getReconciliationClassification());
        value.setLastErrorCode(source.getLastErrorCode());
        value.setLastError(source.getLastError());
        value.setAttemptCount(source.getAttemptCount());
        value.setNextReconcileAt(source.getNextReconcileAt());
        value.setLastReconciledAt(source.getLastReconciledAt());
        value.setCompletedAt(source.getCompletedAt());
        value.setCreatedAt(source.getCreatedAt());
        value.setUpdatedAt(source.getUpdatedAt());
        value.setRowVersion(source.getRowVersion());
        return value;
    }
}
