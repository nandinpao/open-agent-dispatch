package com.opensocket.aievent.core.resourceaccess.bridge;

import com.opensocket.aievent.core.resourceaccess.contract.*;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentRepository;
import com.opensocket.aievent.core.iam.organization.application.port.out.GroupRepository;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.resourceaccess.core.DescriptorFingerprint;
import com.opensocket.aievent.core.task.*;
import com.opensocket.aievent.core.task.domain.TaskParticipantRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Routes Task ownership mutation to the canonical Task aggregate with optimistic locking. */
@Component
@ConditionalOnProperty(prefix="resource-access",name="enabled",havingValue="true")
public final class TaskOwnershipAuthorityAdapter implements ResourceOwnershipAuthorityPort {
    private final TaskRepository tasks; private final TaskParticipantRepository participants;
    private final DepartmentRepository departments; private final GroupRepository groups;
    public TaskOwnershipAuthorityAdapter(TaskRepository tasks,TaskParticipantRepository participants,DepartmentRepository departments,GroupRepository groups){this.tasks=tasks;this.participants=participants;this.departments=departments;this.groups=groups;}
    public boolean supportsOwnershipTransfer(ResourceType type){return type==ResourceType.TASK;}
    public OwnershipTransferImpact preview(OwnershipTransferCommand command){
        validateTarget(command); TaskRecord task=task(command); OwnershipDescriptor current=ownership(task);
        OwnershipDescriptor proposed=new OwnershipDescriptor(command.newOwnerDepartmentId(),command.newOwnerGroupId(),command.newStewardUserId(),current.custodianServiceId(),current.requesterDepartmentId(),current.executorDepartmentId(),current.ownershipVersion()+1);
        String root=task.getRootTaskId()==null?task.getTaskId():task.getRootTaskId(); long affected=Math.max(1,tasks.findByRootTaskId(task.getTenantId(),root,1000).size());
        List<String> warnings=new ArrayList<>(); if(affected>1)warnings.add("TASK_CHAIN_CHILD_OWNERSHIP_NOT_AUTOMATICALLY_TRANSFERRED");
        return new OwnershipTransferImpact(command.resourceRef(),current,proposed,affected,participants.findByTask(task.getTenantId(),task.getTaskId(),1000).size(),warnings,task.getVersion());
    }
    public OwnershipTransferResult transfer(OwnershipTransferCommand command){
        validateTarget(command); TaskRecord before=task(command); if(before.getVersion()!=command.expectedResourceVersion())throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
        OwnershipDescriptor previous=ownership(before);
        OffsetDateTime changedAt=OffsetDateTime.ofInstant(command.requestedAt(),ZoneOffset.UTC);
        TaskRecord after=tasks.transferOwnership(before.getTenantId(),before.getTaskId(),command.expectedResourceVersion(),command.newOwnerDepartmentId(),command.newOwnerGroupId(),command.actorId(),command.reason(),command.correlationId(),changedAt)
                .orElseThrow(()->new IllegalStateException("RESOURCE_VERSION_CONFLICT"));
        String sourceEventId="task-owner-"+DescriptorFingerprint.sha256(command.idempotencyKey()+":"+after.getVersion()).substring(0,32);
        return new OwnershipTransferResult(command.resourceRef(),previous,ownership(after),after.getVersion(),sourceEventId,command.actorId(),command.reason(),command.requestedAt());
    }
    private void validateTarget(OwnershipTransferCommand command){
        ResourceRef ref=command.resourceRef(); TenantId tenant=new TenantId(ref.tenantId());
        if(!command.newStewardUserId().isBlank())throw new UnsupportedOperationException("TASK_STEWARD_NOT_SUPPORTED_BY_DOMAIN");
        if(!command.newOwnerDepartmentId().isBlank()){
            Department value=departments.findById(tenant,new DepartmentId(command.newOwnerDepartmentId())).orElseThrow(()->new IllegalArgumentException("OWNER_DEPARTMENT_NOT_FOUND"));
            if(value.status()!=DepartmentStatus.ACTIVE)throw new IllegalStateException("OWNER_DEPARTMENT_INACTIVE");
        }
        if(!command.newOwnerGroupId().isBlank()){
            Group value=groups.findById(tenant,new GroupId(command.newOwnerGroupId())).orElseThrow(()->new IllegalArgumentException("OWNER_GROUP_NOT_FOUND"));
            if(value.status()!=GroupStatus.ACTIVE)throw new IllegalStateException("OWNER_GROUP_INACTIVE");
        }
    }
    private TaskRecord task(OwnershipTransferCommand command){ResourceRef ref=command.resourceRef();return tasks.findByTenantAndId(ref.tenantId(),ref.resourceId()).orElseThrow(()->new IllegalArgumentException("RESOURCE_NOT_FOUND"));}
    private OwnershipDescriptor ownership(TaskRecord task){return new OwnershipDescriptor(BridgeDescriptorSupport.owner(task.getOwnerDepartmentId()),BridgeDescriptorSupport.owner(task.getOwnerGroupId()),"","",BridgeDescriptorSupport.owner(task.getRequesterDepartmentId()),BridgeDescriptorSupport.owner(task.getExecutorDepartmentId()),task.getVersion());}
}
