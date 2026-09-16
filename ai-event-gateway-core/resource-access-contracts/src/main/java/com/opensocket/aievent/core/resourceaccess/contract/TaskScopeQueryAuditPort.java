package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Set;

/** Append-only evidence for generated Task list plans and SHADOW result-set mismatches. */
public interface TaskScopeQueryAuditPort {
    void recordPlan(TaskScopeQueryPlan plan, String purpose, Instant createdAt);
    void recordShadowMismatch(TaskScopeQueryPlan plan, String purpose,
            Set<String> legacyOnlyTaskIds, Set<String> scopedOnlyTaskIds, Instant createdAt);
}
