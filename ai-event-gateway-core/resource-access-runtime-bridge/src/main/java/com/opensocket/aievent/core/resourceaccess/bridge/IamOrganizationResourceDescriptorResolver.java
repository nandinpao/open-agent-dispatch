package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.iam.identity.application.port.out.HumanUserRepository;
import com.opensocket.aievent.core.iam.identity.domain.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.*;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.iam.rbac.application.port.out.RoleRepository;
import com.opensocket.aievent.core.iam.rbac.domain.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.token.application.port.out.*;
import com.opensocket.aievent.core.iam.token.domain.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Trusted projection bridge for IAM and Organization authorities. */
@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class IamOrganizationResourceDescriptorResolver implements ResourceDescriptorResolverPort,ResourceParticipantResolverPort {
    private final TenantRepository tenants; private final DepartmentRepository departments; private final GroupRepository groups;
    private final HumanUserRepository users; private final TenantMembershipRepository memberships; private final RoleRepository roles;
    private final ServiceAccountRepository serviceAccounts; private final AccessTokenRepository accessTokens;
    public IamOrganizationResourceDescriptorResolver(TenantRepository tenants,DepartmentRepository departments,GroupRepository groups,
            HumanUserRepository users,TenantMembershipRepository memberships,RoleRepository roles,
            ServiceAccountRepository serviceAccounts,AccessTokenRepository accessTokens){
        this.tenants=tenants;this.departments=departments;this.groups=groups;this.users=users;this.memberships=memberships;
        this.roles=roles;this.serviceAccounts=serviceAccounts;this.accessTokens=accessTokens;
    }
    public boolean supports(ResourceType type){return switch(type){case TENANT,DEPARTMENT,GROUP,USER,ROLE,SERVICE_ACCOUNT,ACCESS_TOKEN_METADATA->true;default->false;};}
    public boolean supportsParticipants(ResourceType type){return type==ResourceType.DEPARTMENT||type==ResourceType.GROUP;}
    public Optional<ResourceDescriptor> resolve(ResourceRef ref,DescriptorResolutionContext context){
        if(!supports(ref.resourceType()))return Optional.empty();
        TenantId tenantId=new TenantId(ref.tenantId());
        return switch(ref.resourceType()){
            case TENANT->tenants.findById(tenantId).filter(v->v.tenantId().value().equals(ref.resourceId())).map(v->tenant(ref,v,context));
            case DEPARTMENT->departments.findById(tenantId,new DepartmentId(ref.resourceId())).map(v->department(ref,v,context));
            case GROUP->groups.findById(tenantId,new GroupId(ref.resourceId())).map(v->group(ref,v,context));
            case USER->{
                Optional<HumanUser> user=users.findById(new UserId(ref.resourceId()));
                Optional<TenantMembership> membership=memberships.find(tenantId,new PrincipalRef(PrincipalRef.PrincipalType.USER,ref.resourceId()));
                yield user.isEmpty()||membership.isEmpty()?Optional.empty():Optional.of(user(ref,user.get(),membership.get(),context));
            }
            case ROLE->roles.findById(ref.tenantId(),new RoleId(ref.resourceId())).map(v->role(ref,v,context));
            case SERVICE_ACCOUNT->serviceAccounts.find(ref.tenantId(),new ServiceAccountId(ref.resourceId())).map(v->serviceAccount(ref,v,context));
            case ACCESS_TOKEN_METADATA->accessTokens.findById(ref.tenantId(),new TokenId(ref.resourceId())).map(v->token(ref,v,context));
            default->Optional.empty();
        };
    }
    public Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef ref,DescriptorResolutionContext context){
        Optional<ResourceDescriptor> descriptor=resolve(ref,context); if(descriptor.isEmpty())return Optional.empty();
        List<ResourceParticipantProjection> values=new ArrayList<>(); ResourceDescriptor value=descriptor.get();
        if(ref.resourceType()==ResourceType.DEPARTMENT){add(values,ref,ResourceParticipantType.USER,value.ownership().stewardUserId(),ResourceParticipantRole.STEWARD,VisibilityLevel.SENSITIVE,value.resourceVersion(),DescriptorAuthority.ORGANIZATION_ACCESS,context.requestedAt());}
        if(ref.resourceType()==ResourceType.GROUP){add(values,ref,ResourceParticipantType.DEPARTMENT,value.ownership().ownerDepartmentId(),ResourceParticipantRole.OWNER,VisibilityLevel.STANDARD,value.resourceVersion(),DescriptorAuthority.ORGANIZATION_ACCESS,context.requestedAt());}
        long revision=participantVersion(values); return Optional.of(new ParticipantProjectionSnapshot(ref,revision,values,DescriptorAuthority.ORGANIZATION_ACCESS,"",context.requestedAt()));
    }
    private ResourceDescriptor tenant(ResourceRef ref,Tenant value,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=OwnershipDescriptor.unowned(value.version()); VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.RESTRICTED,VisibilityLevel.SENSITIVE,"TENANT",value.version());
        boolean disabled=value.status()!=TenantStatus.ACTIVE&&value.status()!=TenantStatus.PROVISIONING; String canonical=value.tenantId().value()+"|"+value.tenantCode()+"|"+value.status()+"|"+value.dataRegion()+"|"+value.version();
        return descriptor(ref,value.tenantName(),ownership,null,null,visibility,BridgeDescriptorSupport.stateForDisabled(disabled),0,value.version(),DescriptorAuthority.IAM,canonical,value.updatedAt());
    }
    private ResourceDescriptor department(ResourceRef ref,Department value,DescriptorResolutionContext context){
        String manager=value.manager().map(PrincipalRef::principalId).orElse(""); OwnershipDescriptor ownership=new OwnershipDescriptor(value.departmentId().value(),"",manager,"","","",value.version());
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.INTERNAL,VisibilityLevel.STANDARD,"DEPARTMENT",value.version());
        ResourceRef parent=value.parentDepartmentId().map(v->new ResourceRef(ref.tenantId(),ResourceType.DEPARTMENT,v.value())).orElse(null);
        List<ResourceParticipantProjection> participants=new ArrayList<>();add(participants,ref,ResourceParticipantType.USER,manager,ResourceParticipantRole.STEWARD,VisibilityLevel.SENSITIVE,value.version(),DescriptorAuthority.ORGANIZATION_ACCESS,context.requestedAt());
        String canonical=value.departmentId().value()+"|"+value.code()+"|"+value.status()+"|"+parent+"|"+manager+"|"+value.version();
        return descriptor(ref,value.name(),ownership,parent,null,visibility,BridgeDescriptorSupport.stateForDisabled(value.status()!=DepartmentStatus.ACTIVE),participantVersion(participants),value.version(),DescriptorAuthority.ORGANIZATION_ACCESS,canonical,value.updatedAt());
    }
    private ResourceDescriptor group(ResourceRef ref,Group value,DescriptorResolutionContext context){
        String owner=value.ownerDepartmentId().map(DepartmentId::value).orElse(""); OwnershipDescriptor ownership=new OwnershipDescriptor(owner,value.groupId().value(),"","","","",value.version());
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.INTERNAL,VisibilityLevel.STANDARD,"GROUP",value.version());
        ResourceRef parent=value.parentGroupId().map(v->new ResourceRef(ref.tenantId(),ResourceType.GROUP,v.value())).orElse(null);
        List<ResourceParticipantProjection> participants=new ArrayList<>();add(participants,ref,ResourceParticipantType.DEPARTMENT,owner,ResourceParticipantRole.OWNER,VisibilityLevel.STANDARD,value.version(),DescriptorAuthority.ORGANIZATION_ACCESS,context.requestedAt());
        String canonical=value.groupId().value()+"|"+value.code()+"|"+value.type()+"|"+value.status()+"|"+owner+"|"+parent+"|"+value.version();
        return descriptor(ref,value.name(),ownership,parent,null,visibility,BridgeDescriptorSupport.stateForDisabled(value.status()!=GroupStatus.ACTIVE),participantVersion(participants),value.version(),DescriptorAuthority.ORGANIZATION_ACCESS,canonical,value.updatedAt());
    }
    private ResourceDescriptor user(ResourceRef ref,HumanUser value,TenantMembership membership,DescriptorResolutionContext context){
        long version=Math.max(value.version(),membership.version()); OwnershipDescriptor ownership=new OwnershipDescriptor("","",value.userId().value(),"","","",version); VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.RESTRICTED,VisibilityLevel.SENSITIVE,"USER",version);
        boolean disabled=!value.active()||membership.status()!=MembershipStatus.ACTIVE; String canonical=value.userId().value()+"|"+value.username()+"|"+value.status()+"|"+membership.status()+"|"+membership.version()+"|"+value.version(); return descriptor(ref,value.displayName(),ownership,null,null,visibility,BridgeDescriptorSupport.stateForDisabled(disabled),0,version,DescriptorAuthority.IAM,canonical,value.updatedAt());
    }
    private ResourceDescriptor role(ResourceRef ref,Role value,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=new OwnershipDescriptor("","",value.updatedBy(),"","","",value.version()); VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.RESTRICTED,VisibilityLevel.SENSITIVE,"ROLE",value.version());
        String canonical=value.roleId()+"|"+value.roleCode()+"|"+value.roleType()+"|"+value.status()+"|"+value.version(); return descriptor(ref,value.roleName(),ownership,null,null,visibility,BridgeDescriptorSupport.stateForDisabled(!value.active()),0,value.version(),DescriptorAuthority.IAM,canonical,value.updatedAt());
    }
    private ResourceDescriptor serviceAccount(ResourceRef ref,ServiceAccount value,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(value.ownerDepartmentId()),"",BridgeDescriptorSupport.owner(value.ownerUserId()),value.serviceAccountId().value(),"","",value.version());
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.SECRET,VisibilityLevel.SECRET_METADATA,"SERVICE_ACCOUNT",value.version());
        boolean disabled=value.status()!=ServiceAccountStatus.ACTIVE&&value.status()!=ServiceAccountStatus.OWNERSHIP_REVIEW; String canonical=value.serviceAccountId().value()+"|"+value.status()+"|"+value.riskLevel()+"|"+ownership+"|"+value.version();
        return descriptor(ref,value.name(),ownership,null,null,visibility,BridgeDescriptorSupport.stateForDisabled(disabled),0,value.version(),DescriptorAuthority.IAM,canonical,value.updatedAt());
    }
    private ResourceDescriptor token(ResourceRef ref,AccessToken value,DescriptorResolutionContext context){
        String steward=value.principal().principalType()==PrincipalRef.PrincipalType.USER?value.principal().principalId():""; String custodian=value.principal().principalType()==PrincipalRef.PrincipalType.SERVICE_ACCOUNT?value.principal().principalId():""; String ownerDepartment="";
        if(!custodian.isBlank()){
            Optional<ServiceAccount> account=serviceAccounts.find(ref.tenantId(),new ServiceAccountId(custodian));
            if(account.isPresent()){ownerDepartment=BridgeDescriptorSupport.owner(account.get().ownerDepartmentId());if(steward.isBlank())steward=BridgeDescriptorSupport.owner(account.get().ownerUserId());}
        }
        OwnershipDescriptor ownership=new OwnershipDescriptor(ownerDepartment,"",steward,custodian,"","",value.version()); VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.SECRET,VisibilityLevel.SECRET_METADATA,"ACCESS_TOKEN_METADATA",value.version());
        boolean disabled=value.status()!=AccessTokenStatus.ACTIVE&&value.status()!=AccessTokenStatus.ROTATING; String canonical=value.tokenId().value()+"|"+value.type()+"|"+value.principal()+"|"+value.prefix()+"|"+value.last4()+"|"+value.status()+"|"+value.securityEpoch()+"|"+value.version();
        return descriptor(ref,value.name(),ownership,null,null,visibility,BridgeDescriptorSupport.stateForDisabled(disabled),0,value.version(),DescriptorAuthority.IAM,canonical,value.issuedAt());
    }
    private ResourceDescriptor descriptor(ResourceRef ref,String key,OwnershipDescriptor ownership,ResourceRef parent,ResourceRef root,VisibilityDescriptor visibility,ResourceSecurityState state,long participantVersion,long version,DescriptorAuthority authority,String canonical,Instant at){return new ResourceDescriptor(ref,key,ownership,parent,root,visibility,state,participantVersion,version,authority,BridgeDescriptorSupport.hash(ref,canonical),at);}
    private long participantVersion(List<ResourceParticipantProjection> values){return values.isEmpty()?0:BridgeDescriptorSupport.revisionToken(values.stream().map(ResourceParticipantProjection::participantId).sorted().reduce("",(a,b)->a+"|"+b));}
    private void add(List<ResourceParticipantProjection> values,ResourceRef ref,ResourceParticipantType type,String id,ResourceParticipantRole role,VisibilityLevel visibility,long version,DescriptorAuthority authority,Instant at){if(id==null||id.isBlank())return;values.add(new ResourceParticipantProjection(BridgeDescriptorSupport.participantId(ref,type,id,role),ref,type,id,role,visibility,List.of("resource.metadata.read"),at,null,authority,version,ResourceParticipantStatus.ACTIVE));}
}
