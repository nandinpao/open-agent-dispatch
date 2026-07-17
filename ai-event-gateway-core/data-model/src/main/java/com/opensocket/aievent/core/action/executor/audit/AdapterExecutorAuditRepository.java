package com.opensocket.aievent.core.action.executor.audit;

import java.util.List;

public interface AdapterExecutorAuditRepository {
    AdapterExecutorAuditRecord save(AdapterExecutorAuditRecord record);
    List<AdapterExecutorAuditRecord> recent(int limit);
    List<AdapterExecutorAuditRecord> findByActionId(String actionId, int limit);
    String mode();
}
