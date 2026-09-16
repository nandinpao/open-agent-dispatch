package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class TaskResourceDescriptorResolver implements ResourceDescriptorResolverPort, ResourceParticipantResolverPort {
    private final TaskRepository tasks; private final TaskParticipantRepository participants;
    public TaskResourceDescriptorResolver(TaskRepository tasks,TaskParticipantRepository participants){this.tasks=tasks;this.participants=participants;}
    public boolean supports(ResourceType type){return type==ResourceType.TASK||type==ResourceType.TASK_CHAIN;}
    public boolean supportsParticipants(ResourceType type){return supports(type);}
    public Optional<ResourceDescriptor> resolve(ResourceRef ref,DescriptorResolutionContext context){
        if(!supports(ref.resourceType()))return Optional.empty();
        TaskRecord task=tasks.findByTenantAndId(ref.tenantId(),ref.resourceId()).orElse(null);if(task==null)return Optional.empty();
        List<TaskParticipant> values=participants.findByTask(ref.tenantId(),task.getTaskId(),1000);
        long participantVersion=participantRevision(values,task.getExecutorGroupId(),task.getVersion());
        OwnershipDescriptor ownership=new OwnershipDescriptor(BridgeDescriptorSupport.owner(task.getOwnerDepartmentId()),BridgeDescriptorSupport.owner(task.getOwnerGroupId()),"","",BridgeDescriptorSupport.owner(task.getRequesterDepartmentId()),BridgeDescriptorSupport.owner(task.getExecutorDepartmentId()),task.getVersion());
        ResourceRef parent=task.getParentTaskId()==null?null:new ResourceRef(ref.tenantId(),ResourceType.TASK,task.getParentTaskId());
        String rootId=task.getRootTaskId()==null?task.getTaskId():task.getRootTaskId();
        ResourceRef root=new ResourceRef(ref.tenantId(),ref.resourceType()==ResourceType.TASK_CHAIN?ResourceType.TASK_CHAIN:ResourceType.TASK,rootId);
        SensitivityLevel sensitivity=BridgeDescriptorSupport.sensitivity(task.getSensitivityLevel(),SensitivityLevel.CONFIDENTIAL);
        VisibilityDescriptor visibility=BridgeDescriptorSupport.visibility(sensitivity,VisibilityLevel.FULL,task.getVisibilityPolicy(),task.getVersion());
        String canonical=String.join("|",task.getTaskId(),String.valueOf(task.getTaskKey()),String.valueOf(task.getStatus()),ownership.toString(),String.valueOf(task.getParentTaskId()),rootId,visibility.toString(),String.valueOf(participantVersion),String.valueOf(task.getVersion()));
        return Optional.of(new ResourceDescriptor(ref,task.getTaskKey(),ownership,parent,root,visibility,ResourceSecurityState.NORMAL,participantVersion,task.getVersion(),DescriptorAuthority.TASK_DOMAIN,BridgeDescriptorSupport.hash(ref,canonical),BridgeDescriptorSupport.instant(task.getUpdatedAt(),context.requestedAt())));
    }
    public Optional<ParticipantProjectionSnapshot> resolveParticipants(ResourceRef ref,DescriptorResolutionContext context){
        if(!supports(ref.resourceType()))return Optional.empty();
        TaskRecord task=tasks.findByTenantAndId(ref.tenantId(),ref.resourceId()).orElse(null);if(task==null)return Optional.empty();
        List<TaskParticipant> source=participants.findByTask(ref.tenantId(),task.getTaskId(),1000);
        List<ResourceParticipantProjection> result=new ArrayList<>();
        for(TaskParticipant value:source){
            ResourceParticipantType type=participantType(value.getParticipantType());if(type==null)continue;
            ResourceParticipantRole role=ResourceParticipantRole.valueOf(value.getParticipantRole().name());
            VisibilityLevel visibility=switch(value.getVisibilityLevel()){case SUMMARY->VisibilityLevel.SUMMARY;case STANDARD->VisibilityLevel.STANDARD;case FULL->VisibilityLevel.FULL;};
            List<String> permissions=permissions(value.getOperationLevel());
            Instant validFrom=BridgeDescriptorSupport.instant(value.getCreatedAt(),context.requestedAt());
            result.add(new ResourceParticipantProjection(value.getParticipantId(),ref,type,value.getParticipantRefId(),role,visibility,permissions,validFrom,null,DescriptorAuthority.TASK_DOMAIN,value.getVersion(),ResourceParticipantStatus.ACTIVE));
        }
        String executorGroup=BridgeDescriptorSupport.owner(task.getExecutorGroupId());
        if(!executorGroup.isEmpty()){
            result.add(new ResourceParticipantProjection(
                    BridgeDescriptorSupport.participantId(ref,ResourceParticipantType.GROUP,executorGroup,ResourceParticipantRole.EXECUTOR),
                    ref,ResourceParticipantType.GROUP,executorGroup,ResourceParticipantRole.EXECUTOR,VisibilityLevel.SENSITIVE,
                    List.of("task.read","task.update","task.execute"),context.requestedAt(),null,DescriptorAuthority.TASK_DOMAIN,
                    task.getVersion(),ResourceParticipantStatus.ACTIVE));
        }
        return Optional.of(new ParticipantProjectionSnapshot(ref,participantRevision(source,task.getExecutorGroupId(),task.getVersion()),result,DescriptorAuthority.TASK_DOMAIN,"",context.requestedAt()));
    }
    private long participantRevision(List<TaskParticipant> values,String executorGroupId,long taskVersion){
        String canonical=values.stream().sorted(Comparator.comparing(TaskParticipant::getParticipantId)).map(v->v.getParticipantId()+":"+v.getVersion()+":"+v.getParticipantType()+":"+v.getParticipantRefId()+":"+v.getParticipantRole()+":"+v.getVisibilityLevel()+":"+v.getOperationLevel()).reduce("",(a,b)->a+"|"+b);
        String executorGroup=BridgeDescriptorSupport.owner(executorGroupId);
        if(!executorGroup.isEmpty())canonical+="|RS4_EXECUTOR_GROUP:"+executorGroup+":"+taskVersion;
        return canonical.isEmpty()?0:BridgeDescriptorSupport.revisionToken(canonical);
    }
    private ResourceParticipantType participantType(TaskParticipantType type){return switch(type){case USER->ResourceParticipantType.USER;case DEPARTMENT->ResourceParticipantType.DEPARTMENT;case GROUP->ResourceParticipantType.GROUP;case AGENT->ResourceParticipantType.AGENT_ASSIGNMENT;case SERVICE_DOMAIN->null;};}
    private List<String> permissions(TaskParticipantOperationLevel level){return switch(level){case READ_ONLY->List.of("task.read");case COMMENT->List.of("task.read","task.comment");case OPERATE->List.of("task.read","task.update","task.execute");case APPROVE->List.of("task.read","task.approve");case ADMIN->List.of("task.read","task.update","task.participant.manage","resource.ownership.transfer");};}
}
