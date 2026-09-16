package com.opensocket.aievent.database.persistence.a2a.repository;

import com.opensocket.aievent.core.a2a.A2APolicy;
import com.opensocket.aievent.core.a2a.A2APolicyRepository;
import com.opensocket.aievent.core.a2a.A2APolicyVisibilityScope;
import com.opensocket.aievent.database.persistence.a2a.A2APersistenceConverter;
import com.opensocket.aievent.database.persistence.a2a.dao.A2APolicyDao;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "task", name = "store", havingValue = "MYBATIS")
public class MybatisA2APolicyRepository implements A2APolicyRepository {
    private final A2APolicyDao dao;
    private final A2APersistenceConverter converter;

    public MybatisA2APolicyRepository(A2APolicyDao dao, A2APersistenceConverter converter) {
        this.dao = dao;
        this.converter = converter;
    }

    @Override
    public A2APolicy save(A2APolicy value) {
        dao.upsert(converter.toPo(value));
        return findById(value.getTenantId(), value.getPolicyId()).orElse(value);
    }

    @Override
    public Optional<A2APolicy> findById(String tenantId, String policyId) {
        return Optional.ofNullable(dao.findById(tenantId, policyId)).map(converter::toDomain);
    }

    @Override
    public List<A2APolicy> search(String tenantId, String sourceDomainId, String targetDomainId, int limit) {
        return dao.search(tenantId, sourceDomainId, targetDomainId, bounded(limit)).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<A2APolicy> searchScoped(String tenantId, String sourceDomainId, String targetDomainId, int limit, A2APolicyVisibilityScope scope) {
        if (scope == null) return search(tenantId, sourceDomainId, targetDomainId, limit);
        if (scope.denyAll()) return List.of();
        return dao.searchScoped(tenantId, sourceDomainId, targetDomainId, bounded(limit), scope).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<A2APolicy> findDirectional(String tenantId, String sourceDomainId, String targetDomainId, OffsetDateTime at) {
        return dao.findDirectional(tenantId, sourceDomainId, targetDomainId, at).stream().map(converter::toDomain).toList();
    }

    @Override
    public String mode() { return "MYBATIS"; }

    private int bounded(int limit) { return Math.max(1, Math.min(limit, 1000)); }
}
