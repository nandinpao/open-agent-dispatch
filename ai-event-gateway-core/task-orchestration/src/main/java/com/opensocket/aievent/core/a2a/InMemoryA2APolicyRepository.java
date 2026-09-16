package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!prod")
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MEMORY")
public class InMemoryA2APolicyRepository implements A2APolicyRepository {
    private final ConcurrentHashMap<String, A2APolicy> values = new ConcurrentHashMap<>();

    @Override public A2APolicy save(A2APolicy policy) { values.put(key(policy.getTenantId(), policy.getPolicyId()), policy); return policy; }
    @Override public Optional<A2APolicy> findById(String tenantId, String policyId) { return Optional.ofNullable(values.get(key(tenantId, policyId))); }

    @Override
    public List<A2APolicy> search(String tenantId, String sourceDomainId, String targetDomainId, int limit) {
        return values.values().stream()
                .filter(p -> tenantId.equals(p.getTenantId()))
                .filter(p -> blank(sourceDomainId) || sourceDomainId.equalsIgnoreCase(p.getSourceDomainId()))
                .filter(p -> blank(targetDomainId) || targetDomainId.equalsIgnoreCase(p.getTargetDomainId()))
                .sorted((a, b) -> a.getPolicyCode().compareTo(b.getPolicyCode()))
                .limit(bounded(limit)).toList();
    }

    @Override
    public List<A2APolicy> searchScoped(String tenantId, String sourceDomainId, String targetDomainId, int limit, A2APolicyVisibilityScope scope) {
        if (scope == null) return search(tenantId, sourceDomainId, targetDomainId, limit);
        if (scope.denyAll()) return List.of();
        return search(tenantId, sourceDomainId, targetDomainId, Integer.MAX_VALUE).stream()
                .filter(p -> scope.tenantWide() || positive(scope, p))
                .filter(p -> !negative(scope, p))
                .limit(bounded(limit)).toList();
    }

    @Override
    public List<A2APolicy> findDirectional(String tenantId, String sourceDomainId, String targetDomainId, OffsetDateTime at) {
        return values.values().stream()
                .filter(p -> tenantId.equals(p.getTenantId()) && sourceDomainId.equalsIgnoreCase(p.getSourceDomainId())
                        && targetDomainId.equalsIgnoreCase(p.getTargetDomainId()) && p.activeAt(at))
                .sorted((a, b) -> a.getPolicyCode().compareTo(b.getPolicyCode())).toList();
    }

    @Override public String mode() { return "MEMORY"; }

    private boolean positive(A2APolicyVisibilityScope scope, A2APolicy p) {
        return scope.explicitPolicyIds().contains(p.getPolicyId())
                || containsEither(scope.exactDepartmentIds(), p.getSourceDepartmentId(), p.getTargetDepartmentId())
                || containsEither(scope.subtreeDepartmentRootIds(), p.getSourceDepartmentId(), p.getTargetDepartmentId())
                || containsEither(scope.groupIds(), p.getSourceGroupId(), p.getTargetGroupId());
    }

    private boolean negative(A2APolicyVisibilityScope scope, A2APolicy p) {
        return scope.excludedPolicyIds().contains(p.getPolicyId())
                || containsEither(scope.deniedDepartmentIds(), p.getSourceDepartmentId(), p.getTargetDepartmentId())
                || containsEither(scope.deniedSubtreeDepartmentRootIds(), p.getSourceDepartmentId(), p.getTargetDepartmentId())
                || containsEither(scope.deniedGroupIds(), p.getSourceGroupId(), p.getTargetGroupId());
    }

    private boolean containsEither(java.util.Set<String> values, String left, String right) {
        return (left != null && values.contains(left)) || (right != null && values.contains(right));
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private int bounded(int limit) { return Math.max(1, Math.min(limit, 1000)); }
    private String key(String tenantId, String policyId) { return tenantId + ":" + policyId; }
}
