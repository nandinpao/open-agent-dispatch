package com.opensocket.aievent.core.iam.persistence.repository;

import java.time.Instant;

import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PolicyVersionRepository;
import com.opensocket.aievent.core.iam.rbac.domain.PolicyVersion;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;

@DatabaseRepositoryAdapter
public class MybatisPolicyVersionRepository implements PolicyVersionRepository {
    private final IamRbacDao dao;

    public MybatisPolicyVersionRepository(IamRbacDao dao) {
        this.dao = dao;
    }

    @Override
    public PolicyVersion current(String tenantId) {
        String normalizedTenantId = tenantId == null ? "" : tenantId.trim();
        boolean instanceScope = normalizedTenantId.isBlank();
        long value = dao.findPolicyVersion(instanceScope ? null : normalizedTenantId);
        return new PolicyVersion(
                instanceScope ? ScopeType.INSTANCE : ScopeType.TENANT,
                instanceScope ? "INSTANCE" : normalizedTenantId,
                instanceScope ? "" : normalizedTenantId,
                value,
                Instant.EPOCH,
                "database");
    }
}
