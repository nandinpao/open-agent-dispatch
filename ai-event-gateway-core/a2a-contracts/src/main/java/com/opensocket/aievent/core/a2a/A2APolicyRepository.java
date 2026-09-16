package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface A2APolicyRepository {
    /** Legacy persistence mutation retained only for migration/test compatibility. */
    @Deprecated(forRemoval = true)
    A2APolicy save(A2APolicy policy);
    Optional<A2APolicy> findById(String tenantId, String policyId);
    List<A2APolicy> search(String tenantId, String sourceDomainId, String targetDomainId, int limit);
    default List<A2APolicy> searchScoped(String tenantId, String sourceDomainId, String targetDomainId, int limit, A2APolicyVisibilityScope scope) {
        return scope != null && scope.denyAll() ? List.of() : search(tenantId, sourceDomainId, targetDomainId, limit);
    }
    /** Legacy topology lookup retained for historical reconciliation only. */
    @Deprecated(forRemoval = true)
    List<A2APolicy> findDirectional(String tenantId, String sourceDomainId, String targetDomainId, OffsetDateTime at);
    String mode();
}
