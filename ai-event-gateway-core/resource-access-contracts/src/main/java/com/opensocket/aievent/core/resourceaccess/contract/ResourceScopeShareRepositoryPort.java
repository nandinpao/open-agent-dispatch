package com.opensocket.aievent.core.resourceaccess.contract;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** RS1 persistence port for permission-neutral cross-scope resource collaboration evidence. */
public interface ResourceScopeShareRepositoryPort {
    Optional<PersistedResourceScopeShare> findById(String tenantId, String shareId);
    PersistedResourceScopeShare save(PersistedResourceScopeShare share, long expectedVersion);
    Set<String> findEffectiveSharedResourceIds(String tenantId, ResourceType resourceType,
            ResourcePermissionScopeDecision permissionScopes, Instant at);
    List<PersistedResourceScopeShare> findEffectiveByResource(ResourceRef resourceRef, Instant at);
}
