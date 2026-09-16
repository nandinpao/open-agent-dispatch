package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.agent.governance.*;
import com.opensocket.aievent.core.dispatch.flow.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class AgentResourceDescriptorResolver implements ResourceDescriptorResolverPort,ResourceParticipantResolverPort {
    private final AgentGovernanceRepository agents;private final AgentPoolRoutingRepository pools;
    public AgentResourceDescriptorResolver(AgentGovernanceRepository agents,AgentPoolRoutingRepository pools){this.agents=agents;this.pools=pools;}
    public boolean supports(ResourceType type){return type==ResourceType.AGENT||type==ResourceType.AGENT_POOL||type==ResourceType.AGENT_SERVICE_SCOPE||type==ResourceType.AGENT_CREDENTIAL_METADATA;}
    public boolean supportsParticipants(ResourceType type){return type==ResourceType.AGENT_POOL;}
    public Optional<ResourceDescriptor> resolve(ResourceRef ref,DescriptorResolutionContext context){if(!supports(ref.resourceType()))return Optional.empty();return switch(ref.resourceType()){
        case AGENT,AGENT_SERVICE_SCOPE,AGENT_CREDENTIAL_METADATA->agents.findProfile(ref.resourceId()).filter(v->ref.tenantId().equals(v.getTenantId())).map(v->agent(ref,v,context));
        case AGENT_POOL->pools.findActivePool(ref.tenantId(),ref.resourceId()).map(v->pool(ref,v,context));default->Optional.empty();};}
    public Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef ref,DescriptorResolutionContext context){Optional<ResourceDescriptor> descriptor=resolve(ref,context);if(descriptor.isEmpty())return Optional.empty();List<ResourceParticipantProjection> values=participants(ref,descriptor.get().ownership(),descriptor.get().resourceVersion(),context.requestedAt());return Optional.of(new ParticipantProjectionSnapshot(ref,participantVersion(values),values,DescriptorAuthority.AGENT_CONTROL,"",context.requestedAt()));}
    private ResourceDescriptor agent(ResourceRef ref,AgentProfile value,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(value.getOwnerDepartmentId()),BridgeDescriptorSupport.owner(value.getOwnerGroupId()),"","","","",value.getPolicyVersion());
        SensitivityLevel sensitivity=ref.resourceType()==ResourceType.AGENT_CREDENTIAL_METADATA?SensitivityLevel.SECRET:SensitivityLevel.RESTRICTED;VisibilityLevel maximum=ref.resourceType()==ResourceType.AGENT_CREDENTIAL_METADATA?VisibilityLevel.SECRET_METADATA:VisibilityLevel.SENSITIVE;
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(sensitivity,maximum,ref.resourceType().name(),value.getPolicyVersion());
        ResourceSecurityState state=switch(value.getRiskStatus()){case QUARANTINED,COMPROMISED->ResourceSecurityState.QUARANTINED;case SUSPENDED,REVOKED->ResourceSecurityState.RESTRICTED;case NORMAL->ResourceSecurityState.NORMAL;};
        if(value.getApprovalStatus()==AgentApprovalStatus.REVOKED||value.getApprovalStatus()==AgentApprovalStatus.REJECTED)state=ResourceSecurityState.RESTRICTED;
        long participantVersion=0;String canonical=value.getAgentId()+"|"+value.getApprovalStatus()+"|"+value.getRiskStatus()+"|"+value.isEnabled()+"|"+ownership+"|"+value.getPolicyVersion();
        return new ResourceDescriptor(ref,value.getAgentName(),ownership,null,null,visibility,state,participantVersion,value.getPolicyVersion(),DescriptorAuthority.AGENT_CONTROL,BridgeDescriptorSupport.hash(ref,canonical),BridgeDescriptorSupport.instant(value.getUpdatedAt(),context.requestedAt()));
    }
    private ResourceDescriptor pool(ResourceRef ref,AgentPoolRoutingSnapshot value,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(value.getOwnerDepartmentId()),BridgeDescriptorSupport.owner(value.getOwnerGroupId()),"","","","",1);VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(SensitivityLevel.INTERNAL,VisibilityLevel.STANDARD,"AGENT_POOL",1);String canonical=value.getPoolId()+"|"+value.getPoolCode()+"|"+value.getStatus()+"|"+value.getMembers().size();
        return new ResourceDescriptor(ref,value.getPoolCode(),ownership,null,null,visibility,ResourceSecurityState.NORMAL,0,1,DescriptorAuthority.AGENT_CONTROL,BridgeDescriptorSupport.hash(ref,canonical),context.requestedAt());
    }
    private List<ResourceParticipantProjection> participants(ResourceRef ref,OwnershipDescriptor owner,long version,Instant at){List<ResourceParticipantProjection> values=new ArrayList<>();add(values,ref,ResourceParticipantType.DEPARTMENT,owner.ownerDepartmentId(),ResourceParticipantRole.OWNER,version,at);add(values,ref,ResourceParticipantType.GROUP,owner.ownerGroupId(),ResourceParticipantRole.OWNER,version,at);return List.copyOf(values);}
    private long participantVersion(List<ResourceParticipantProjection> values){return values.isEmpty()?0:BridgeDescriptorSupport.revisionToken(values.stream().map(ResourceParticipantProjection::participantId).sorted().reduce("",(a,b)->a+"|"+b));}
    private void add(List<ResourceParticipantProjection> values,ResourceRef ref,ResourceParticipantType type,String id,ResourceParticipantRole role,long version,Instant at){if(id==null||id.isBlank())return;values.add(new ResourceParticipantProjection(BridgeDescriptorSupport.participantId(ref,type,id,role),ref,type,id,role,VisibilityLevel.SENSITIVE,List.of("agent.read","agent.manage"),at,null,DescriptorAuthority.AGENT_CONTROL,version,ResourceParticipantStatus.ACTIVE));}
}
