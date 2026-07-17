package com.opensocket.aievent.core.action.executor.audit;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "adapter-executor.audit", name = "store", havingValue = "MEMORY")
public class InMemoryAdapterExecutorAuditRepository implements AdapterExecutorAuditRepository {
    private final ConcurrentMap<String, AdapterExecutorAuditRecord> records = new ConcurrentHashMap<>();

    @Override
    public AdapterExecutorAuditRecord save(AdapterExecutorAuditRecord record) {
        records.put(record.getAuditId(), record);
        return record;
    }

    @Override
    public List<AdapterExecutorAuditRecord> recent(int limit) {
        return records.values().stream()
                .sorted(Comparator.comparing(AdapterExecutorAuditRecord::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public List<AdapterExecutorAuditRecord> findByActionId(String actionId, int limit) {
        return records.values().stream()
                .filter(r -> actionId != null && actionId.equals(r.getActionId()))
                .sorted(Comparator.comparing(AdapterExecutorAuditRecord::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(limit, 1000)))
                .toList();
    }

    @Override
    public String mode() { return "MEMORY"; }
}
