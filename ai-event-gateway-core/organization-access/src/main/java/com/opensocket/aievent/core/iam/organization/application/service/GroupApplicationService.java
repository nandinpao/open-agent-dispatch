package com.opensocket.aievent.core.iam.organization.application.service;

import com.opensocket.aievent.core.iam.organization.application.command.*;
import com.opensocket.aievent.core.iam.organization.application.port.in.GroupCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.out.*;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.iam.organization.event.OrganizationEvents;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.*;
import java.util.*;

public final class GroupApplicationService implements GroupCommandPort {
    private final GroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final DepartmentRepository departments;
    private final TenantMembershipRepository tenantMemberships;
    private final OrganizationEventPublisher events;
    private final Clock clock;

    public GroupApplicationService(GroupRepository groups,GroupMembershipRepository memberships,DepartmentRepository departments,TenantMembershipRepository tenantMemberships,OrganizationEventPublisher events,Clock clock){
        this.groups=Objects.requireNonNull(groups);this.memberships=Objects.requireNonNull(memberships);this.departments=Objects.requireNonNull(departments);this.tenantMemberships=Objects.requireNonNull(tenantMemberships);this.events=Objects.requireNonNull(events);this.clock=Objects.requireNonNull(clock);
    }

    @Override public Group createGroup(CreateGroupCommand command){
        TenantId tenantId=new TenantId(command.tenantId());GroupId id=new GroupId(command.groupId());
        if(groups.findById(tenantId,id).isPresent()) throw new OrganizationDomainException(OrganizationReasonCode.GROUP_ID_CONFLICT,"Group internal ID already exists");
        if(groups.existsByCode(tenantId,command.code())) throw new OrganizationDomainException(OrganizationReasonCode.GROUP_CODE_CONFLICT,"An active Group with this code already exists");
        Optional<GroupId> parent=groupId(command.parentGroupId());validateParentChain(tenantId,id,parent);
        Optional<DepartmentId> owner=departmentId(command.ownerDepartmentId());validateOwner(tenantId,owner);
        Instant now=clock.instant();Group saved=groups.save(Group.create(tenantId,id,command.code(),command.name(),command.type(),parent,owner,command.description(),command.actorId(),now),0);
        events.publish(new OrganizationEvents.GroupCreated(command.eventId(),tenantId.value(),id.value(),command.actorId(),command.correlationId(),now));return saved;
    }

    @Override public Group updateGroup(UpdateGroupCommand command){
        TenantId tenantId=new TenantId(command.tenantId());Group current=requireGroup(tenantId,command.groupId());requireMutable(current);requireVersion(current.version(),command.expectedVersion());
        if(!current.code().equalsIgnoreCase(command.code())&&groups.existsByCode(tenantId,command.code())) throw new OrganizationDomainException(OrganizationReasonCode.GROUP_CODE_CONFLICT,"An active Group with this code already exists");
        Optional<GroupId> parent=groupId(command.parentGroupId());validateParentChain(tenantId,current.groupId(),parent);
        Optional<DepartmentId> owner=departmentId(command.ownerDepartmentId());validateOwner(tenantId,owner);
        Instant now=clock.instant();Group saved=groups.save(current.update(command.code(),command.name(),command.type(),parent,owner,command.description(),command.actorId(),now),command.expectedVersion());
        events.publish(new OrganizationEvents.GroupUpdated(command.eventId(),tenantId.value(),saved.groupId().value(),command.actorId(),command.correlationId(),now));return saved;
    }

    @Override public Group changeGroupStatus(ChangeGroupStatusCommand command){
        TenantId tenantId=new TenantId(command.tenantId());Group current=requireGroup(tenantId,command.groupId());requireVersion(current.version(),command.expectedVersion());
        if(current.status()==GroupStatus.DELETED && command.status()!=GroupStatus.DELETED) throw new OrganizationDomainException(OrganizationReasonCode.INVALID_STATUS_TRANSITION,"Deleted Groups cannot be reactivated or edited");
        Instant now=clock.instant();Group saved=groups.save(current.changeStatus(command.status(),command.actorId(),now),command.expectedVersion());
        events.publish(new OrganizationEvents.GroupStatusChanged(command.eventId(),tenantId.value(),saved.groupId().value(),current.status().name(),saved.status().name(),command.actorId(),command.correlationId(),now));return saved;
    }

    @Override public GroupMembership addGroupMembership(AddGroupMembershipCommand command){
        TenantId tenantId=new TenantId(command.tenantId());Group group=requireGroup(tenantId,command.groupId());if(group.status()!=GroupStatus.ACTIVE) throw new IllegalArgumentException("group must be active");
        PrincipalRef user=new PrincipalRef(PrincipalRef.PrincipalType.USER,command.userId());requireAssignableTenantMembership(tenantId,user);
        Instant now=clock.instant();GroupMembership saved=memberships.save(new GroupMembership(new MembershipId(command.membershipId()),tenantId,user,group.groupId(),command.membershipRole(),now,Optional.ofNullable(command.expiresAt()),MembershipStatus.ACTIVE,1),0);
        publish(saved,command.actorId(),command.correlationId(),command.eventId(),now);return saved;
    }

    @Override public GroupMembership updateGroupMembership(UpdateGroupMembershipCommand command){
        TenantId tenantId=new TenantId(command.tenantId());GroupMembership current=memberships.findById(tenantId,new MembershipId(command.membershipId())).orElseThrow(()->new IllegalArgumentException("group membership not found"));requireVersion(current.version(),command.expectedVersion());
        if(!command.remove()){
            Group group=requireGroup(tenantId,current.groupId().value());if(group.status()!=GroupStatus.ACTIVE) throw new IllegalArgumentException("group must be active");requireAssignableTenantMembership(tenantId,current.userPrincipal());
        }
        GroupMembership changed=command.remove()?current.remove():current.update(command.membershipRole(),Optional.ofNullable(command.expiresAt()),MembershipStatus.ACTIVE);
        GroupMembership saved=memberships.save(changed,command.expectedVersion());publish(saved,command.actorId(),command.correlationId(),command.eventId(),clock.instant());return saved;
    }

    private void validateParentChain(TenantId tenantId,GroupId moving,Optional<GroupId> requested){
        if(requested.filter(moving::equals).isPresent()) throw new OrganizationDomainException(OrganizationReasonCode.GROUP_CYCLE_DETECTED,"Group cannot be its own parent");
        Set<GroupId> visited=new HashSet<>();Optional<GroupId> cursor=requested;int depth=0;
        while(cursor.isPresent()){
            GroupId id=cursor.orElseThrow();if(!visited.add(id)||id.equals(moving)) throw new OrganizationDomainException(OrganizationReasonCode.GROUP_CYCLE_DETECTED,"Group parent hierarchy contains a cycle");
            Group parent=groups.findById(tenantId,id).orElseThrow(()->new IllegalArgumentException("parent group not found"));if(parent.status()!=GroupStatus.ACTIVE) throw new IllegalArgumentException("parent group must be active");cursor=parent.parentGroupId();
            if(++depth>25) throw new OrganizationDomainException(OrganizationReasonCode.GROUP_CYCLE_DETECTED,"Group hierarchy exceeds the supported depth");
        }
    }

    private void validateOwner(TenantId tenantId,Optional<DepartmentId> owner){owner.ifPresent(id->{Department department=departments.findById(tenantId,id).orElseThrow(()->new IllegalArgumentException("owner department not found"));if(department.status()!=DepartmentStatus.ACTIVE) throw new OrganizationDomainException(OrganizationReasonCode.GROUP_OWNER_DEPARTMENT_DISABLED,"Owner Department must be active");});}
    private void requireAssignableTenantMembership(TenantId tenantId,PrincipalRef user){if(tenantMemberships.find(tenantId,user).filter(value->value.organizationAssignableAt(clock.instant())).isEmpty()) throw new OrganizationDomainException(OrganizationReasonCode.TENANT_MEMBERSHIP_NOT_ASSIGNABLE,"Group member requires an INVITED or ACTIVE Tenant Membership with a future expiry");}
    private static void requireMutable(Group group){if(group.status()==GroupStatus.DELETED) throw new OrganizationDomainException(OrganizationReasonCode.INVALID_STATUS_TRANSITION,"Deleted Groups are read-only tombstones retained for audit");}
    private Group requireGroup(TenantId tenantId,String id){return groups.findById(tenantId,new GroupId(id)).orElseThrow(()->new IllegalArgumentException("group not found"));}
    private void publish(GroupMembership membership,String actor,String correlation,String eventId,Instant now){events.publish(new OrganizationEvents.GroupMembershipChanged(eventId,membership.tenantId().value(),membership.membershipId().value(),membership.userPrincipal().principalId(),actor,correlation,now));}
    private static Optional<GroupId> groupId(String value){return optional(value).map(GroupId::new);}private static Optional<DepartmentId> departmentId(String value){return optional(value).map(DepartmentId::new);}private static Optional<String> optional(String value){return value==null||value.isBlank()?Optional.empty():Optional.of(value.trim());}
    private static void requireVersion(long actual,long expected){if(expected<1||actual!=expected) throw new OrganizationDomainException(OrganizationReasonCode.VERSION_CONFLICT,"Expected version "+expected+" but was "+actual);}
}
