package com.opensocket.aievent.core.iam.rbac.application.service;

import com.opensocket.aievent.core.iam.rbac.application.port.out.*;
import com.opensocket.aievent.core.iam.security.contract.*;
import java.util.*;

public final class SecurityEpochReconciliationService {
    private final SecurityEpochAuthorityPort authority; private final AuthorizationGrantCachePort cache;
    public SecurityEpochReconciliationService(SecurityEpochAuthorityPort authority,AuthorizationGrantCachePort cache){this.authority=authority;this.cache=cache;}
    public ReconciliationResult reconcile(Collection<Entry> entries){int checked=0,evicted=0;for(Entry entry:entries){checked++;SecurityEpoch current=authority.current(entry.tenant(),entry.principal());if(!entry.cachedEpoch().isAtLeast(current)){String tenantId=entry.tenant().scope()==TenantRef.Scope.TENANT?entry.tenant().tenantId():"";cache.evict(tenantId,entry.principal().principalId());evicted++;}}return new ReconciliationResult(checked,evicted);}
    public record Entry(TenantRef tenant,PrincipalRef principal,SecurityEpoch cachedEpoch){}
    public record ReconciliationResult(int checked,int evicted){}
}
