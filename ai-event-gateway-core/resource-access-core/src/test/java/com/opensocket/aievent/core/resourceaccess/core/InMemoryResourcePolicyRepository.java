package com.opensocket.aievent.core.resourceaccess.core;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.*;

final class InMemoryResourcePolicyRepository implements ResourcePolicyRepository {
    final Map<String,ScopeGrantRecord> grants=new HashMap<>();final Map<String,ExplicitDenyRecord> denies=new HashMap<>();
    final Map<String,VisibilityPolicyRecord> policies=new HashMap<>();final Map<String,PrincipalClearanceRecord> clearances=new HashMap<>();
    Optional<SecurityStateChangeResult> securityChange=Optional.empty();
    private String key(String tenant,String id){return tenant+":"+id;}
    public Optional<ScopeGrantRecord> findScopeGrant(String t,String id){return Optional.ofNullable(grants.get(key(t,id)));}
    public Optional<ScopeGrantRecord> findScopeGrantByIdempotencyKey(String t,String idem){return grants.values().stream().filter(v->v.tenantId().equals(t)&&v.idempotencyKey().equals(idem)).findFirst();}
    public ScopeGrantRecord insertScopeGrant(ScopeGrantRecord g,String c){grants.put(key(g.tenantId(),g.grantId()),g);return g;}
    public ScopeGrantRecord transitionScopeGrant(ScopeGrantRecord g,ScopeGrantState state,String approved,String actor,String reason,String correlation,String idem,Instant at){ScopeGrantRecord n=new ScopeGrantRecord(g.tenantId(),g.grantId(),g.principalType(),g.principalId(),g.permissionCode(),g.resourceType(),g.scopeType(),g.scopeRefId(),g.visibilityLevel(),g.validFrom(),g.validTo(),g.grantSource(),g.grantReason(),g.createdBy(),approved.isBlank()?g.approvedBy():approved,state,g.idempotencyKey(),g.version()+1,g.createdAt(),at);grants.put(key(n.tenantId(),n.grantId()),n);return n;}
    public Optional<ExplicitDenyRecord> findExplicitDeny(String t,String id){return Optional.ofNullable(denies.get(key(t,id)));}
    public Optional<ExplicitDenyRecord> findExplicitDenyByIdempotencyKey(String t,String idem){return denies.values().stream().filter(v->v.tenantId().equals(t)&&v.idempotencyKey().equals(idem)).findFirst();}
    public ExplicitDenyRecord insertExplicitDeny(ExplicitDenyRecord d,String c){denies.put(key(d.tenantId(),d.denyId()),d);return d;}
    public ExplicitDenyRecord transitionExplicitDeny(ExplicitDenyRecord d,ScopeDenyState state,String approvedBy,String actor,String reason,String correlation,String idem,Instant at){ExplicitDenyRecord n=new ExplicitDenyRecord(d.tenantId(),d.denyId(),d.principalType(),d.principalId(),d.permissionCode(),d.resourceType(),d.scopeType(),d.scopeRefId(),d.denyReason(),d.severity(),d.validFrom(),d.validTo(),d.createdBy(),approvedBy.isBlank()?d.approvedBy():approvedBy,state,d.idempotencyKey(),d.version()+1,d.createdAt(),at);denies.put(key(n.tenantId(),n.denyId()),n);return n;}
    public Optional<VisibilityPolicyRecord> findVisibilityPolicy(String t,String id){return Optional.ofNullable(policies.get(key(t,id)));}
    public Optional<VisibilityPolicyRecord> findVisibilityPolicyByIdempotencyKey(String t,String idem){return Optional.empty();}
    public VisibilityPolicyRecord insertVisibilityPolicy(VisibilityPolicyRecord p,String i,String c){policies.put(key(p.tenantId(),p.policyId()),p);return p;}
    public VisibilityPolicyRecord transitionVisibilityPolicy(VisibilityPolicyRecord p,VisibilityPolicyState state,String actor,String reason,String correlation,String idem,Instant at){VisibilityPolicyRecord n=new VisibilityPolicyRecord(p.tenantId(),p.policyId(),p.resourceType(),p.policyName(),p.maximumVisibility(),p.maximumSensitivity(),state,p.fieldRules(),p.version()+1,p.createdBy(),actor,p.createdAt(),at);policies.put(key(n.tenantId(),n.policyId()),n);return n;}
    public Optional<PrincipalClearanceRecord> findPrincipalClearance(String t,String id){return Optional.ofNullable(clearances.get(key(t,id)));}
    public Optional<PrincipalClearanceRecord> findPrincipalClearanceByIdempotencyKey(String t,String i){return Optional.empty();}
    public Optional<String> findPrincipalClearanceCreator(String t,String id){return Optional.of("creator");}
    public PrincipalClearanceRecord insertPrincipalClearance(PrincipalClearanceRecord c,String createdBy,String i,String x){clearances.put(key(c.tenantId(),c.clearanceId()),c);return c;}
    public PrincipalClearanceRecord transitionPrincipalClearance(PrincipalClearanceRecord c,ClearanceStatus state,String approvedBy,String actor,String reason,String correlation,String idem,Instant at){PrincipalClearanceRecord n=new PrincipalClearanceRecord(c.tenantId(),c.clearanceId(),c.principalType(),c.principalId(),c.clearanceLevel(),c.validFrom(),c.validTo(),state,approvedBy.isBlank()?c.approvedBy():approvedBy,c.reason(),c.version()+1,c.createdAt(),at);clearances.put(key(n.tenantId(),n.clearanceId()),n);return n;}
    public Optional<SecurityStateChangeResult> findSecurityStateChange(ResourceRef r,String i){return securityChange;}
    public SecurityStateChangeResult changeSecurityState(SecurityStateChangeCommand c){return new SecurityStateChangeResult(c.resourceRef(),ResourceSecurityState.NORMAL,c.targetState(),c.expectedResourceVersion()+1,1,c.actorId(),c.reason(),c.requestedAt());}
}
