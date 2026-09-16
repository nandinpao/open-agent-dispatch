package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.Optional;

/** Persistence boundary for prepared/active materialized scope plans. */
public interface MaterializedScopeSnapshotPort {
    Optional<MaterializedScopeSnapshot> findActive(
            String tenantId, ScopeSnapshotKind kind, String principalType, String principalId,
            String permissionCode, ResourceType resourceType, PolicyVersion policyVersion,
            SecurityEpoch securityEpoch, Instant at);
    MaterializedScopeSnapshot savePrepared(MaterializedScopeSnapshot snapshot, String correlationId);
    long countPrepared(String tenantId, long departmentRevision);
    int retireBeforeDepartmentRevision(String tenantId, long departmentRevision, String reasonCode, String correlationId, Instant at);
}
