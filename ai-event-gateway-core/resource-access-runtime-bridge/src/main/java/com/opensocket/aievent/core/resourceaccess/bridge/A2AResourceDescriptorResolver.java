package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.resourceaccess.contract.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class A2AResourceDescriptorResolver implements ResourceDescriptorResolverPort,ResourceParticipantResolverPort {
    private final A2ARequestRepository requests;
    private final A2APolicyRepository policies;

    public A2AResourceDescriptorResolver(A2ARequestRepository requests,A2APolicyRepository policies){
        this.requests=Objects.requireNonNull(requests);this.policies=Objects.requireNonNull(policies);
    }
    public boolean supports(ResourceType type){return type==ResourceType.A2A_REQUEST||type==ResourceType.A2A_APPROVAL||type==ResourceType.A2A_POLICY;}
    public boolean supportsParticipants(ResourceType type){return supports(type);}

    public Optional<ResourceDescriptor> resolve(ResourceRef ref,DescriptorResolutionContext context){
        if(!supports(ref.resourceType()))return Optional.empty();
        if(ref.resourceType()==ResourceType.A2A_POLICY)return policies.findById(ref.tenantId(),ref.resourceId()).map(p->policyDescriptor(ref,p,context));
        A2ARequest request=requests.findById(ref.tenantId(),ref.resourceId()).orElse(null);if(request==null)return Optional.empty();
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(request.getSourceDepartmentId()),BridgeDescriptorSupport.owner(request.getSourceGroupId()),"","",BridgeDescriptorSupport.owner(request.getSourceDepartmentId()),BridgeDescriptorSupport.owner(request.getTargetDepartmentId()),request.getVersion());
        ResourceRef parent=request.getSourceTaskId()==null?null:new ResourceRef(ref.tenantId(),ResourceType.TASK,request.getSourceTaskId());
        ResourceRef root=request.getRootTaskId()==null?null:new ResourceRef(ref.tenantId(),ResourceType.TASK,request.getRootTaskId());
        SensitivityLevel sensitivity=BridgeDescriptorSupport.sensitivity(request.getSensitivityLevel(),ref.resourceType()==ResourceType.A2A_APPROVAL?SensitivityLevel.RESTRICTED:SensitivityLevel.CONFIDENTIAL);
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(sensitivity,ref.resourceType()==ResourceType.A2A_APPROVAL?VisibilityLevel.SENSITIVE:VisibilityLevel.STANDARD,"A2A_"+ref.resourceType(),request.getPolicyVersion());
        List<ResourceParticipantProjection> projected=requestParticipants(ref,request,context.requestedAt()); long participantVersion=participantRevision(projected);
        ResourceSecurityState state=requestState(request);
        String canonical=request.getRequestId()+"|"+request.getRequestStatus()+"|"+request.getApprovalStatus()+"|"+ownership+"|"+request.getChildTaskId()+"|"+request.getVersion()+"|"+participantVersion;
        return Optional.of(new ResourceDescriptor(ref,request.getRequestId(),ownership,parent,root,visibility,state,participantVersion,request.getVersion(),DescriptorAuthority.A2A_DOMAIN,BridgeDescriptorSupport.hash(ref,canonical),BridgeDescriptorSupport.instant(request.getUpdatedAt(),context.requestedAt())));
    }

    public Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef ref,DescriptorResolutionContext context){
        if(!supports(ref.resourceType()))return Optional.empty();
        List<ResourceParticipantProjection> values;
        long version;
        if(ref.resourceType()==ResourceType.A2A_POLICY){
            A2APolicy policy=policies.findById(ref.tenantId(),ref.resourceId()).orElse(null);if(policy==null)return Optional.empty();
            values=policyParticipants(ref,policy,context.requestedAt());version=policy.getVersion();
        }else{
            A2ARequest request=requests.findById(ref.tenantId(),ref.resourceId()).orElse(null);if(request==null)return Optional.empty();
            values=requestParticipants(ref,request,context.requestedAt());version=request.getVersion();
        }
        return Optional.of(new ParticipantProjectionSnapshot(ref,participantRevision(values),values,DescriptorAuthority.A2A_DOMAIN,"",context.requestedAt()));
    }

    private ResourceDescriptor policyDescriptor(ResourceRef ref,A2APolicy policy,DescriptorResolutionContext context){
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(policy.getGovernanceOwnerDepartmentId()),BridgeDescriptorSupport.owner(policy.getGovernanceOwnerGroupId()),"","",BridgeDescriptorSupport.owner(policy.getSourceDepartmentId()),BridgeDescriptorSupport.owner(policy.getTargetDepartmentId()),policy.getVersion());
        SensitivityLevel sensitivity=BridgeDescriptorSupport.sensitivity(policy.getMaxSensitivityLevel(),SensitivityLevel.CONFIDENTIAL);
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(sensitivity,VisibilityLevel.SENSITIVE,"A2A_POLICY",policy.getVersion());
        List<ResourceParticipantProjection> projected=policyParticipants(ref,policy,context.requestedAt());
        ResourceSecurityState state=policy.isEnabled()?ResourceSecurityState.NORMAL:ResourceSecurityState.ARCHIVED;
        String canonical=policy.getPolicyId()+"|"+policy.getPolicyCode()+"|"+policy.getSourceDepartmentId()+"|"+policy.getSourceGroupId()+"|"+policy.getTargetDepartmentId()+"|"+policy.getTargetGroupId()+"|"+policy.isEnabled()+"|"+policy.getVersion();
        return new ResourceDescriptor(ref,policy.getPolicyCode(),ownership,null,null,visibility,state,participantRevision(projected),policy.getVersion(),DescriptorAuthority.A2A_DOMAIN,BridgeDescriptorSupport.hash(ref,canonical),BridgeDescriptorSupport.instant(policy.getUpdatedAt(),context.requestedAt()));
    }

    private List<ResourceParticipantProjection> requestParticipants(ResourceRef ref,A2ARequest request,Instant at){
        List<ResourceParticipantProjection> values=new ArrayList<>();
        add(values,ref,ResourceParticipantType.DEPARTMENT,request.getSourceDepartmentId(),ResourceParticipantRole.REQUESTER,VisibilityLevel.SUMMARY,List.of("a2a.request.read"),request.getVersion(),at);
        add(values,ref,ResourceParticipantType.GROUP,request.getSourceGroupId(),ResourceParticipantRole.REQUESTER,VisibilityLevel.SUMMARY,List.of("a2a.request.read"),request.getVersion(),at);
        add(values,ref,ResourceParticipantType.DEPARTMENT,request.getTargetDepartmentId(),ResourceParticipantRole.EXECUTOR,VisibilityLevel.STANDARD,List.of("a2a.request.read","a2a.request.execute"),request.getVersion(),at);
        add(values,ref,ResourceParticipantType.GROUP,request.getTargetGroupId(),ResourceParticipantRole.EXECUTOR,VisibilityLevel.STANDARD,List.of("a2a.request.read","a2a.request.execute"),request.getVersion(),at);
        add(values,ref,ResourceParticipantType.USER,request.getRequestedById(),ResourceParticipantRole.CREATOR,VisibilityLevel.SUMMARY,List.of("a2a.request.read"),request.getVersion(),at);
        for(String actor:request.getApprovalActorIds()==null?List.<String>of():request.getApprovalActorIds())add(values,ref,ResourceParticipantType.USER,actor,ResourceParticipantRole.APPROVER,VisibilityLevel.SENSITIVE,List.of("a2a.approval.read","a2a.approval.approve"),request.getVersion(),at);
        return List.copyOf(values);
    }
    private List<ResourceParticipantProjection> policyParticipants(ResourceRef ref,A2APolicy policy,Instant at){
        List<ResourceParticipantProjection> values=new ArrayList<>();
        add(values,ref,ResourceParticipantType.DEPARTMENT,policy.getSourceDepartmentId(),ResourceParticipantRole.REQUESTER,VisibilityLevel.STANDARD,List.of("a2a.policy.read","a2a.policy.manage"),policy.getVersion(),at);
        add(values,ref,ResourceParticipantType.GROUP,policy.getSourceGroupId(),ResourceParticipantRole.REQUESTER,VisibilityLevel.STANDARD,List.of("a2a.policy.read","a2a.policy.manage"),policy.getVersion(),at);
        add(values,ref,ResourceParticipantType.DEPARTMENT,policy.getTargetDepartmentId(),ResourceParticipantRole.OWNER,VisibilityLevel.SENSITIVE,List.of("a2a.policy.read","a2a.policy.manage"),policy.getVersion(),at);
        add(values,ref,ResourceParticipantType.GROUP,policy.getTargetGroupId(),ResourceParticipantRole.OWNER,VisibilityLevel.SENSITIVE,List.of("a2a.policy.read","a2a.policy.manage"),policy.getVersion(),at);
        return List.copyOf(values);
    }
    private void add(List<ResourceParticipantProjection> values,ResourceRef ref,ResourceParticipantType type,String id,ResourceParticipantRole role,VisibilityLevel visibility,List<String> permissions,long version,Instant at){id=BridgeDescriptorSupport.owner(id);if(id.isEmpty())return;values.add(new ResourceParticipantProjection(BridgeDescriptorSupport.participantId(ref,type,id,role),ref,type,id,role,visibility,permissions,at,null,DescriptorAuthority.A2A_DOMAIN,version,ResourceParticipantStatus.ACTIVE));}
    private long participantRevision(List<ResourceParticipantProjection> values){if(values.isEmpty())return 0;return BridgeDescriptorSupport.revisionToken(values.stream().map(v->v.participantId()+":"+v.sourceVersion()).sorted().reduce("",(a,b)->a+"|"+b));}
    private ResourceSecurityState requestState(A2ARequest request){String status=request.getRequestStatus()==null?"":request.getRequestStatus().name();if(status.contains("CANCEL")||status.contains("EXPIRE")||status.contains("COMPLET"))return ResourceSecurityState.ARCHIVED;return ResourceSecurityState.NORMAL;}
}
