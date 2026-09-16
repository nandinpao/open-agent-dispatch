package com.opensocket.aievent.core.iam.persistence.repository;

import com.opensocket.aievent.core.iam.persistence.dao.IamRbacDao;
import com.opensocket.aievent.core.iam.rbac.application.port.out.PrincipalExpansionPort;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;

@DatabaseRepositoryAdapter
public class MybatisPrincipalExpansionAdapter implements PrincipalExpansionPort {
    private final IamRbacDao dao;
    private final Clock clock;
    public MybatisPrincipalExpansionAdapter(IamRbacDao dao,Clock clock){this.dao=dao;this.clock=clock;}

    public Set<PrincipalRef> expand(String tenantId, PrincipalRef principal) {
        Set<PrincipalRef> result=new LinkedHashSet<>();
        result.add(principal);
        if(principal.principalType()!=PrincipalRef.PrincipalType.USER || tenantId==null || tenantId.isBlank()) return Set.copyOf(result);
        var at=clock.instant();
        for(String id:dao.findEffectiveDepartmentIds(tenantId,principal.principalId(),at))
            result.add(new PrincipalRef(PrincipalRef.PrincipalType.DEPARTMENT,id));
        for(String id:dao.findActiveGroupIds(tenantId,principal.principalId(),at))
            result.add(new PrincipalRef(PrincipalRef.PrincipalType.GROUP,id));
        return Set.copyOf(result);
    }
}
