package com.opensocket.aievent.core.iam.rbac.domain;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.Objects;

public final class PrincipalRoleBinding {
    private final String bindingId; private final PrincipalRef principal; private final RoleId roleId; private final ScopeRef scope;
    private final Instant effectiveAt; private final Instant expiresAt; private final BindingStatus status;
    private final Instant createdAt; private final String createdBy; private final Instant revokedAt; private final String revokedBy; private final long version;
    private PrincipalRoleBinding(String bindingId,PrincipalRef principal,RoleId roleId,ScopeRef scope,Instant effectiveAt,Instant expiresAt,
                                 BindingStatus status,Instant createdAt,String createdBy,Instant revokedAt,String revokedBy,long version){
        if(bindingId==null||bindingId.isBlank())throw new IllegalArgumentException("bindingId is required"); this.bindingId=bindingId.trim();
        this.principal=Objects.requireNonNull(principal,"principal"); if(principal.principalType()==PrincipalRef.PrincipalType.INSTANCE_ROOT)throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_PRINCIPAL_FORBIDDEN,"INSTANCE_ROOT cannot receive role bindings");
        this.roleId=Objects.requireNonNull(roleId,"roleId"); this.scope=Objects.requireNonNull(scope,"scope");
        this.effectiveAt=Objects.requireNonNull(effectiveAt,"effectiveAt"); this.expiresAt=expiresAt;
        if(expiresAt!=null&&!expiresAt.isAfter(effectiveAt))throw new IllegalArgumentException("expiresAt must be after effectiveAt");
        this.status=Objects.requireNonNull(status,"status"); this.createdAt=Objects.requireNonNull(createdAt,"createdAt");
        if(createdBy==null||createdBy.isBlank())throw new IllegalArgumentException("createdBy is required");this.createdBy=createdBy.trim();
        this.revokedAt=revokedAt;this.revokedBy=revokedBy==null?"":revokedBy.trim();if(version<1)throw new IllegalArgumentException("version must be positive");this.version=version;
    }
    public static PrincipalRoleBinding create(String id,PrincipalRef principal,RoleId roleId,ScopeRef scope,Instant effectiveAt,Instant expiresAt,String actor,Instant at){return new PrincipalRoleBinding(id,principal,roleId,scope,effectiveAt,expiresAt,BindingStatus.ACTIVE,at,actor,null,"",1);}
    public static PrincipalRoleBinding reconstitute(String id,PrincipalRef principal,RoleId roleId,ScopeRef scope,Instant effectiveAt,Instant expiresAt,BindingStatus status,Instant createdAt,String createdBy,Instant revokedAt,String revokedBy,long version){return new PrincipalRoleBinding(id,principal,roleId,scope,effectiveAt,expiresAt,status,createdAt,createdBy,revokedAt,revokedBy,version);}
    public PrincipalRoleBinding revoke(String actor,Instant at){if(status==BindingStatus.REVOKED)return this;return new PrincipalRoleBinding(bindingId,principal,roleId,scope,effectiveAt,expiresAt,BindingStatus.REVOKED,createdAt,createdBy,at,actor,version+1);}
    public PrincipalRoleBinding changeResponsibility(RoleId nextRoleId,ScopeRef nextScope,String actor,Instant at){if(status!=BindingStatus.ACTIVE)throw new RbacDomainException(RbacReasonCode.ROLE_BINDING_NOT_FOUND,"Only active role bindings may change responsibility");return new PrincipalRoleBinding(bindingId,principal,Objects.requireNonNull(nextRoleId,"roleId"),Objects.requireNonNull(nextScope,"scope"),at,expiresAt,BindingStatus.ACTIVE,createdAt,createdBy,null,"",version+1);}
    public boolean effectiveAt(Instant at){return status==BindingStatus.ACTIVE&&!at.isBefore(effectiveAt)&&(expiresAt==null||at.isBefore(expiresAt));}
    public String bindingId(){return bindingId;}public PrincipalRef principal(){return principal;}public RoleId roleId(){return roleId;}public ScopeRef scope(){return scope;}public Instant effectiveAt(){return effectiveAt;}public Instant expiresAt(){return expiresAt;}public BindingStatus status(){return status;}public Instant createdAt(){return createdAt;}public String createdBy(){return createdBy;}public Instant revokedAt(){return revokedAt;}public String revokedBy(){return revokedBy;}public long version(){return version;}
}
