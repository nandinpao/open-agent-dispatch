package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.integration.identity.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class IntegrationResourceDescriptorResolver implements ResourceDescriptorResolverPort,ResourceParticipantResolverPort {
    private final IntegrationIdentityRepository identities;
    public IntegrationResourceDescriptorResolver(IntegrationIdentityRepository identities){this.identities=identities;}
    public boolean supports(ResourceType type){return type==ResourceType.ISSUE_CONNECTION||type==ResourceType.ISSUE_PRINCIPAL||type==ResourceType.ISSUE_CREDENTIAL_METADATA||type==ResourceType.ISSUE_PROJECT_MAPPING;}
    public boolean supportsParticipants(ResourceType type){return type==ResourceType.ISSUE_CONNECTION||type==ResourceType.ISSUE_PRINCIPAL||type==ResourceType.ISSUE_PROJECT_MAPPING;}
    public Optional<ResourceDescriptor> resolve(ResourceRef ref,DescriptorResolutionContext context){
        if(!supports(ref.resourceType()))return Optional.empty();return switch(ref.resourceType()){
            case ISSUE_CONNECTION->identities.findConnection(ref.tenantId(),ref.resourceId()).map(v->connection(ref,v,context));
            case ISSUE_PRINCIPAL->identities.findPrincipal(ref.tenantId(),ref.resourceId()).map(v->principal(ref,v,context));
            case ISSUE_CREDENTIAL_METADATA->identities.findCredential(ref.tenantId(),ref.resourceId()).map(v->credential(ref,v,context));
            case ISSUE_PROJECT_MAPPING->identities.findMapping(ref.tenantId(),ref.resourceId()).map(v->mapping(ref,v,context));
            default->Optional.empty();};
    }
    public Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef ref,DescriptorResolutionContext context){
        Optional<ResourceDescriptor> descriptor=resolve(ref,context);if(descriptor.isEmpty())return Optional.empty();
        List<ResourceParticipantProjection> values=participants(ref,descriptor.get().ownership(),descriptor.get().visibility().maximumVisibility(),descriptor.get().resourceVersion(),context.requestedAt());
        return Optional.of(new ParticipantProjectionSnapshot(ref,participantVersion(values),values,DescriptorAuthority.ISSUE_TRACKING,"",context.requestedAt()));
    }
    private ResourceDescriptor connection(ResourceRef ref,IntegrationConnection value,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(value.ownerDepartmentId()),BridgeDescriptorSupport.owner(value.ownerGroupId()),"","","","",value.version()); SensitivityLevel sensitivity=SensitivityLevel.RESTRICTED;
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(sensitivity,VisibilityLevel.SENSITIVE,"ISSUE_CONNECTION",value.version());
        String canonical=value.connectionId()+"|"+value.ownerDepartmentId()+"|"+value.ownerGroupId()+"|"+value.providerType()+"|"+value.status()+"|"+value.enabled()+"|"+value.version();
        return descriptor(ref,value.connectionName(),ownership,null,null,visibility,BridgeDescriptorSupport.stateForDisabled(!value.enabled()||value.status()==IntegrationConnectionStatus.DISABLED),0,value.version(),canonical,BridgeDescriptorSupport.instant(value.updatedAt(),context.requestedAt()));
    }
    private ResourceDescriptor principal(ResourceRef ref,IntegrationPrincipal value,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(value.ownerDepartmentId()),BridgeDescriptorSupport.owner(value.ownerGroupId()),"","","","",value.version());
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.RESTRICTED,VisibilityLevel.SENSITIVE,"ISSUE_PRINCIPAL",value.version());
        ResourceRef parent=new ResourceRef(ref.tenantId(),ResourceType.ISSUE_CONNECTION,value.connectionId());
        boolean disabled=Set.of(IntegrationPrincipalStatus.REVOKED,IntegrationPrincipalStatus.EXPIRED,IntegrationPrincipalStatus.DISABLED).contains(value.status());
        String canonical=value.principalId()+"|"+value.connectionId()+"|"+value.status()+"|"+value.riskLevel()+"|"+ownership+"|"+value.version();
        return descriptor(ref,value.principalName(),ownership,parent,parent,visibility,BridgeDescriptorSupport.stateForDisabled(disabled),participantVersion(participants(ref,ownership,VisibilityLevel.SENSITIVE,value.version(),context.requestedAt())),value.version(),canonical,BridgeDescriptorSupport.instant(value.updatedAt(),context.requestedAt()));
    }
    private ResourceDescriptor credential(ResourceRef ref,IntegrationCredentialMetadata value,DescriptorResolutionContext context){
        IntegrationPrincipal principal=identities.findPrincipal(ref.tenantId(),value.principalId()).orElse(null);
        OwnershipDescriptor ownership=principal==null?OwnershipDescriptor.unowned(value.version()):new OwnershipDescriptor(BridgeDescriptorSupport.owner(principal.ownerDepartmentId()),BridgeDescriptorSupport.owner(principal.ownerGroupId()),"","","","",Math.max(value.version(),principal.version()));
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.SECRET,VisibilityLevel.SECRET_METADATA,"ISSUE_CREDENTIAL_METADATA",value.version());
        ResourceRef parent=new ResourceRef(ref.tenantId(),ResourceType.ISSUE_PRINCIPAL,value.principalId());
        boolean disabled=Set.of(IntegrationCredentialStatus.EXPIRED,IntegrationCredentialStatus.REVOKED,IntegrationCredentialStatus.DISABLED).contains(value.status());
        String canonical=value.credentialId()+"|"+value.principalId()+"|"+value.authType()+"|"+value.secretVersion()+"|"+value.status()+"|"+ownership+"|"+value.version();
        return descriptor(ref,"credential-"+value.secretLast4(),ownership,parent,parent,visibility,BridgeDescriptorSupport.stateForDisabled(disabled),0,value.version(),canonical,BridgeDescriptorSupport.instant(value.updatedAt(),context.requestedAt()));
    }
    private ResourceDescriptor mapping(ResourceRef ref,IntegrationProjectMapping value,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(value.departmentId()),BridgeDescriptorSupport.owner(value.groupId()),"","","",BridgeDescriptorSupport.owner(value.departmentId()),value.version());
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.CONFIDENTIAL,VisibilityLevel.STANDARD,"ISSUE_PROJECT_MAPPING",value.mappingVersion());
        ResourceRef parent=new ResourceRef(ref.tenantId(),ResourceType.ISSUE_CONNECTION,value.connectionId());
        boolean disabled=!value.enabled()||value.lifecycleStatus()==ProjectMappingLifecycle.DISABLED||value.lifecycleStatus()==ProjectMappingLifecycle.DEPRECATED;
        String canonical=value.mappingId()+"|"+value.connectionId()+"|"+value.externalProjectId()+"|"+value.lifecycleStatus()+"|"+ownership+"|"+value.mappingVersion()+"|"+value.version();
        return descriptor(ref,value.externalProjectKey(),ownership,parent,parent,visibility,BridgeDescriptorSupport.stateForDisabled(disabled),participantVersion(participants(ref,ownership,VisibilityLevel.STANDARD,value.version(),context.requestedAt())),value.version(),canonical,BridgeDescriptorSupport.instant(value.updatedAt(),context.requestedAt()));
    }
    private ResourceDescriptor descriptor(ResourceRef ref,String key,OwnershipDescriptor ownership,ResourceRef parent,ResourceRef root,VisibilityDescriptor visibility,ResourceSecurityState state,long participantVersion,long resourceVersion,String canonical,Instant at){return new ResourceDescriptor(ref,key,ownership,parent,root,visibility,state,participantVersion,resourceVersion,DescriptorAuthority.ISSUE_TRACKING,BridgeDescriptorSupport.hash(ref,canonical),at);}
    private List<ResourceParticipantProjection> participants(ResourceRef ref,OwnershipDescriptor owner,VisibilityLevel visibility,long version,Instant at){List<ResourceParticipantProjection> values=new ArrayList<>();add(values,ref,ResourceParticipantType.DEPARTMENT,owner.ownerDepartmentId(),ResourceParticipantRole.OWNER,visibility,version,at);add(values,ref,ResourceParticipantType.GROUP,owner.ownerGroupId(),ResourceParticipantRole.OWNER,visibility,version,at);return List.copyOf(values);}
    private long participantVersion(List<ResourceParticipantProjection> values){return values.isEmpty()?0:BridgeDescriptorSupport.revisionToken(values.stream().map(ResourceParticipantProjection::participantId).sorted().reduce("",(a,b)->a+"|"+b));}
    private void add(List<ResourceParticipantProjection> values,ResourceRef ref,ResourceParticipantType type,String id,ResourceParticipantRole role,VisibilityLevel visibility,long version,Instant at){if(id==null||id.isBlank())return;values.add(new ResourceParticipantProjection(BridgeDescriptorSupport.participantId(ref,type,id,role),ref,type,id,role,visibility,List.of("integration.resource.read"),at,null,DescriptorAuthority.ISSUE_TRACKING,version,ResourceParticipantStatus.ACTIVE));}
}
