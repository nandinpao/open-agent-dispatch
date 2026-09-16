package com.opensocket.aievent.core.iam.persistence.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opensocket.aievent.core.iam.rbac.application.port.out.AuthorizationGrantCachePort;
import com.opensocket.aievent.core.iam.rbac.domain.ResolvedRoleGrant;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Duration;
import java.util.*;

public final class CaffeineAuthorizationGrantCache implements AuthorizationGrantCachePort {
    private final Cache<Key,List<ResolvedRoleGrant>> cache;
    public CaffeineAuthorizationGrantCache(long maximumSize,Duration ttl){cache=Caffeine.newBuilder().maximumSize(Math.max(100,maximumSize)).expireAfterWrite(ttl).build();}
    public Optional<List<ResolvedRoleGrant>> get(String tenantId,PrincipalRef principal,long policyVersion){return Optional.ofNullable(cache.getIfPresent(new Key(norm(tenantId),principal.principalType().name(),principal.principalId(),policyVersion)));}
    public void put(String tenantId,PrincipalRef principal,long policyVersion,List<ResolvedRoleGrant> grants){cache.put(new Key(norm(tenantId),principal.principalType().name(),principal.principalId(),policyVersion),List.copyOf(grants));}
    public void evict(String tenantId,String principalId){String t=norm(tenantId);cache.asMap().keySet().removeIf(k->k.tenantId.equals(t)&&k.principalId.equals(principalId));}
    public void evictTenant(String tenantId){String t=norm(tenantId);cache.asMap().keySet().removeIf(k->k.tenantId.equals(t));}
    public void evictAll(){cache.invalidateAll();}
    public long estimatedSize(){return cache.estimatedSize();}
    private String norm(String v){return v==null?"":v.trim();}
    private record Key(String tenantId,String principalType,String principalId,long policyVersion){}
}
