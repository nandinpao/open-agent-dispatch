package com.opensocket.aievent.core.iam.persistence.repository;

import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.rbac.application.port.out.OrganizationScopePort;
import com.opensocket.aievent.core.iam.rbac.domain.OrganizationScopeSnapshot;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;

@DatabaseRepositoryAdapter
public class MybatisOrganizationScopeAdapter implements OrganizationScopePort {
    private final IamRbacDao dao; private final Clock clock;
    public MybatisOrganizationScopeAdapter(IamRbacDao dao,Clock clock){this.dao=dao;this.clock=clock;}
    public OrganizationScopeSnapshot resolve(String tenantId,PrincipalRef principal){
        if(principal.principalType()!=PrincipalRef.PrincipalType.USER) return new OrganizationScopeSnapshot(tenantId,Set.of(),Set.of());
        return new OrganizationScopeSnapshot(tenantId,
            new LinkedHashSet<>(dao.findEffectiveDepartmentIds(tenantId,principal.principalId(),clock.instant())),
            new LinkedHashSet<>(dao.findActiveGroupIds(tenantId,principal.principalId(),clock.instant())));
    }
    public boolean departmentContains(String tenantId,String ancestorDepartmentId,String candidateDepartmentId){
        return dao.r7ScopeContains(tenantId,"DEPARTMENT",ancestorDepartmentId,"DEPARTMENT",candidateDepartmentId);
    }
}
