package com.opensocket.aievent.core.iam.organization.application.service;

import com.opensocket.aievent.core.iam.organization.application.command.*;
import com.opensocket.aievent.core.iam.organization.application.port.in.DepartmentCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.out.*;
import com.opensocket.aievent.core.iam.organization.domain.*;
import com.opensocket.aievent.core.iam.organization.event.OrganizationEvents;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Transaction-neutral organization orchestration. */
public final class DepartmentApplicationService implements DepartmentCommandPort {
    private final DepartmentRepository departments;
    private final DepartmentHierarchyRepository hierarchyRepository;
    private final DepartmentRevisionRepository revisions;
    private final DepartmentMembershipRepository memberships;
    private final TenantMembershipRepository tenantMemberships;
    private final GroupRepository groups;
    private final OrganizationSnapshotRepository snapshots;
    private final OrganizationEventPublisher events;
    private final DepartmentHierarchyPolicy hierarchyPolicy;
    private final OrganizationSnapshotFactory snapshotFactory;
    private final Clock clock;

    public DepartmentApplicationService(
            DepartmentRepository departments,
            DepartmentHierarchyRepository hierarchyRepository,
            DepartmentRevisionRepository revisions,
            DepartmentMembershipRepository memberships,
            TenantMembershipRepository tenantMemberships,
            GroupRepository groups,
            OrganizationSnapshotRepository snapshots,
            OrganizationEventPublisher events,
            DepartmentHierarchyPolicy hierarchyPolicy,
            OrganizationSnapshotFactory snapshotFactory,
            Clock clock) {
        this.departments=Objects.requireNonNull(departments);
        this.hierarchyRepository=Objects.requireNonNull(hierarchyRepository);
        this.revisions=Objects.requireNonNull(revisions);
        this.memberships=Objects.requireNonNull(memberships);
        this.tenantMemberships=Objects.requireNonNull(tenantMemberships);
        this.groups=Objects.requireNonNull(groups);
        this.snapshots=Objects.requireNonNull(snapshots);
        this.events=Objects.requireNonNull(events);
        this.hierarchyPolicy=Objects.requireNonNull(hierarchyPolicy);
        this.snapshotFactory=Objects.requireNonNull(snapshotFactory);
        this.clock=Objects.requireNonNull(clock);
    }

    @Override public Department createDepartment(CreateDepartmentCommand command){
        Objects.requireNonNull(command);
        TenantId tenantId=new TenantId(command.tenantId());
        DepartmentId id=new DepartmentId(command.departmentId());
        if(departments.findById(tenantId,id).isPresent()) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_ID_CONFLICT,"Department internal ID already exists");
        if(departments.existsByCode(tenantId,command.code())) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_CODE_CONFLICT,"An active Department with this code already exists");
        Optional<DepartmentId> parentId=optionalId(command.parentDepartmentId());
        Optional<Department> parent=parentId.flatMap(value->departments.findById(tenantId,value));
        if(parentId.isPresent()&&parent.isEmpty()) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_PARENT_NOT_FOUND,"Parent department not found");
        hierarchyPolicy.validateCreate(tenantId,parent,hierarchyRepository.load(tenantId));
        Optional<PrincipalRef> manager=manager(command.managerUserId());
        Instant now=clock.instant();
        manager.ifPresent(value->requireActiveTenantManager(tenantId,value,now));
        Department saved=departments.save(Department.create(tenantId,id,command.code(),command.name(),parent.map(Department::departmentId),manager,command.displayOrder(),command.actorId(),now),0);
        manager.ifPresent(value->ensureOfficialManagerMembership(saved,value,command.actorId(),command.correlationId(),command.eventId(),now));
        createRevision(saved,1,pathFor(tenantId,parent,saved),now,DepartmentRevisionChangeType.CREATED,command.actorId(),command.reason(),command.eventId());
        events.publish(new OrganizationEvents.DepartmentCreated(command.eventId(),tenantId.value(),id.value(),command.actorId(),command.correlationId(),now));
        return saved;
    }

    @Override public Department updateDepartment(UpdateDepartmentCommand command){
        Objects.requireNonNull(command);
        TenantId tenantId=new TenantId(command.tenantId());
        Department current=requireDepartment(tenantId,command.departmentId());
        requireMutable(current);
        requireVersion(current.version(),command.expectedVersion());
        if(!current.code().equalsIgnoreCase(command.code())&&departments.existsByCode(tenantId,command.code())) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_CODE_CONFLICT,"An active Department with this code already exists");
        Optional<DepartmentId> parentId=optionalId(command.parentDepartmentId());
        Optional<Department> parent=parentId.flatMap(value->departments.findById(tenantId,value));
        if(parentId.isPresent()&&parent.isEmpty()) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_PARENT_NOT_FOUND,"Parent department not found");
        if(!current.parentDepartmentId().equals(parent.map(Department::departmentId))) hierarchyPolicy.validateMove(current,parent,hierarchyRepository.load(tenantId));
        Optional<PrincipalRef> manager=manager(command.managerUserId());
        Instant now=clock.instant();
        manager.ifPresent(value->requireActiveTenantManager(tenantId,value,now));
        Department saved=departments.save(current.update(command.code(),command.name(),parent.map(Department::departmentId),manager,command.displayOrder(),command.actorId(),now),command.expectedVersion());
        manager.ifPresent(value->ensureOfficialManagerMembership(saved,value,command.actorId(),command.correlationId(),command.eventId(),now));
        revise(saved,tenantId,parent,now,DepartmentRevisionChangeType.UPDATED,command.actorId(),command.reason(),command.eventId());
        events.publish(new OrganizationEvents.DepartmentUpdated(command.eventId(),tenantId.value(),saved.departmentId().value(),saved.manager().map(PrincipalRef::principalId).orElse(""),command.actorId(),command.correlationId(),now));
        return saved;
    }

    @Override public Department changeDepartmentStatus(ChangeDepartmentStatusCommand command){
        TenantId tenantId=new TenantId(command.tenantId());
        Department current=requireDepartment(tenantId,command.departmentId());
        requireVersion(current.version(),command.expectedVersion());
        if(current.status()==DepartmentStatus.DELETED && command.status()!=DepartmentStatus.DELETED) throw new OrganizationDomainException(OrganizationReasonCode.INVALID_STATUS_TRANSITION,"Deleted Departments cannot be reactivated or edited");
        Instant now=clock.instant();
        Department saved=departments.save(current.changeStatus(command.status(),command.actorId(),now),command.expectedVersion());
        DepartmentRevisionChangeType type=command.status()==DepartmentStatus.ACTIVE?DepartmentRevisionChangeType.REACTIVATED:command.status()==DepartmentStatus.DELETED?DepartmentRevisionChangeType.DELETED:DepartmentRevisionChangeType.DISABLED;
        Optional<Department> parent=saved.parentDepartmentId().flatMap(value->departments.findById(tenantId,value));
        revise(saved,tenantId,parent,now,type,command.actorId(),command.reason(),command.eventId());
        events.publish(new OrganizationEvents.DepartmentStatusChanged(command.eventId(),tenantId.value(),saved.departmentId().value(),current.status().name(),saved.status().name(),command.actorId(),command.correlationId(),now));
        return saved;
    }

    @Override public Department moveDepartment(MoveDepartmentCommand command){
        TenantId tenantId=new TenantId(command.tenantId());
        Department current=requireDepartment(tenantId,command.departmentId());
        requireMutable(current);
        requireVersion(current.version(),command.expectedVersion());
        Optional<DepartmentId> requested=optionalId(command.newParentDepartmentId());
        Optional<Department> parent=requested.flatMap(value->departments.findById(tenantId,value));
        if(requested.isPresent()&&parent.isEmpty()) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_PARENT_NOT_FOUND,"Parent department not found");
        hierarchyPolicy.validateMove(current,parent,hierarchyRepository.load(tenantId));
        Instant now=clock.instant();
        String oldParent=current.parentDepartmentId().map(DepartmentId::value).orElse("");
        Department moved=departments.save(current.moveTo(parent.map(Department::departmentId),command.actorId(),now),command.expectedVersion());
        long revision=revise(moved,tenantId,parent,now,DepartmentRevisionChangeType.MOVED,command.actorId(),command.reason(),command.eventId());
        events.publish(new OrganizationEvents.DepartmentMoved(command.eventId(),tenantId.value(),moved.departmentId().value(),oldParent,moved.parentDepartmentId().map(DepartmentId::value).orElse(""),revision,command.actorId(),command.correlationId(),now));
        return moved;
    }

    @Override public DepartmentMembership addDepartmentMembership(AddDepartmentMembershipCommand command){
        TenantId tenantId=new TenantId(command.tenantId());
        PrincipalRef user=new PrincipalRef(PrincipalRef.PrincipalType.USER,command.userId());
        DepartmentId departmentId=new DepartmentId(command.departmentId());
        Department department=departments.findById(tenantId,departmentId).orElseThrow(()->new IllegalArgumentException("department not found"));
        if(department.status()!=DepartmentStatus.ACTIVE) throw new OrganizationDomainException(OrganizationReasonCode.DEPARTMENT_PARENT_DISABLED,"Department must be active");
        Instant now=clock.instant();
        requireAssignableTenantMembership(tenantId,user,now,"Department member");
        requireAssignableMembershipType(command.membershipType());

        // Re-admission must reconcile stale persistence rows before deciding whether a new
        // Primary Department can be created. PostgreSQL protects one status=ACTIVE primary
        // row even when its expires_at is in the past, while the domain's active query
        // correctly excludes expired rows. Demote/expire that stale row first so the DB
        // invariant and the domain invariant cannot disagree.
        memberships.findStoredPrimaryByUser(tenantId,user).ifPresent(storedPrimary -> {
            boolean effectiveNow=!storedPrimary.effectiveAt().isAfter(now);
            boolean unexpired=storedPrimary.expiresAt().isEmpty()||storedPrimary.expiresAt().orElseThrow().isAfter(now);
            if(!effectiveNow||!unexpired){
                MembershipStatus reconciledStatus=unexpired?MembershipStatus.ACTIVE:MembershipStatus.EXPIRED;
                DepartmentMembership reconciled=storedPrimary.update(storedPrimary.membershipType(),false,storedPrimary.expiresAt(),reconciledStatus);
                memberships.save(reconciled,storedPrimary.version());
            }
        });

        Optional<DepartmentMembership> existing=memberships.findByUserAndDepartment(tenantId,user,departmentId);
        List<DepartmentMembership> activeMemberships=memberships.findActiveByUser(tenantId,user);
        boolean otherPrimary=activeMemberships.stream().anyMatch(value->value.primary()&&(existing.isEmpty()||!value.membershipId().equals(existing.orElseThrow().membershipId())));
        if(command.primary()&&otherPrimary) throw new OrganizationDomainException(OrganizationReasonCode.PRIMARY_DEPARTMENT_CONFLICT,"User already has a primary department");
        boolean primary=command.primary()||activeMemberships.stream().noneMatch(DepartmentMembership::primary);

        DepartmentMembership saved;
        if(existing.isPresent()){
            DepartmentMembership current=existing.orElseThrow();
            // Reuse the governed relationship on Tenant re-admission instead of INSERTing
            // another row that violates user/department/type uniqueness.
            DepartmentMembership reactivated=current.update(command.membershipType(),primary,Optional.ofNullable(command.expiresAt()),MembershipStatus.ACTIVE);
            saved=memberships.save(reactivated,current.version());
        }else{
            saved=memberships.save(new DepartmentMembership(new MembershipId(command.membershipId()),tenantId,user,departmentId,command.membershipType(),primary,now,Optional.ofNullable(command.expiresAt()),MembershipStatus.ACTIVE,1),0);
        }
        publishMembership(saved,command.actorId(),command.correlationId(),command.eventId(),now);
        return saved;
    }

    @Override public DepartmentMembership updateDepartmentMembership(UpdateDepartmentMembershipCommand command){
        TenantId tenantId=new TenantId(command.tenantId());
        DepartmentMembership current=memberships.findById(tenantId,new MembershipId(command.membershipId())).orElseThrow(()->new IllegalArgumentException("department membership not found"));
        requireVersion(current.version(),command.expectedVersion());
        requireAssignableMembershipType(command.membershipType());
        boolean removingPrimary=current.primary()&&(command.remove()||!command.primary());
        DepartmentMembership replacement=removingPrimary&&hasReplacement(command)?requireReplacement(tenantId,current,command):null;
        if(!command.remove()&&command.primary()&&memberships.findActiveByUser(tenantId,current.userPrincipal()).stream().anyMatch(value->value.primary()&&!value.membershipId().equals(current.membershipId()))) throw new OrganizationDomainException(OrganizationReasonCode.PRIMARY_DEPARTMENT_CONFLICT,"User already has a primary department");
        DepartmentMembership changed=command.remove()?current.remove():current.update(command.membershipType(),command.primary(),Optional.ofNullable(command.expiresAt()),MembershipStatus.ACTIVE);
        DepartmentMembership saved=memberships.save(changed,command.expectedVersion());
        publishMembership(saved,command.actorId(),command.correlationId(),command.eventId(),clock.instant());
        if(replacement!=null) promoteReplacement(replacement,command);
        return saved;
    }

    @Override public OrganizationSnapshot captureSnapshot(CaptureOrganizationSnapshotCommand command){
        TenantId tenantId=new TenantId(command.tenantId());
        DepartmentRevision revision=revisions.findCurrent(tenantId,new DepartmentId(command.departmentId())).orElseThrow(()->new IllegalArgumentException("current department revision is missing"));
        Set<GroupId> groupIds=command.groupIds()==null?Set.of():command.groupIds().stream().map(GroupId::new).collect(Collectors.toUnmodifiableSet());
        Instant now=clock.instant();
        OrganizationSnapshot saved=snapshots.saveIfAbsentByContentHash(snapshotFactory.capture(new SnapshotId(command.snapshotId()),revision,groupIds,now));
        events.publish(new OrganizationEvents.OrganizationSnapshotCaptured(command.eventId(),tenantId.value(),saved.snapshotId().value(),saved.contentHash(),command.actorId(),command.correlationId(),now));
        return saved;
    }

    private void requireAssignableTenantMembership(TenantId tenantId,PrincipalRef user,Instant now,String subject){
        TenantMembership membership=tenantMemberships.find(tenantId,user).orElseThrow(()->new OrganizationDomainException(OrganizationReasonCode.TENANT_MEMBERSHIP_NOT_ASSIGNABLE,subject+" must have a Tenant Membership"));
        if(!membership.organizationAssignableAt(now)) throw new OrganizationDomainException(OrganizationReasonCode.TENANT_MEMBERSHIP_NOT_ASSIGNABLE,subject+" requires an INVITED or ACTIVE Tenant Membership with a future expiry");
    }

    private void requireActiveTenantManager(TenantId tenantId,PrincipalRef user,Instant now){
        TenantMembership membership=tenantMemberships.find(tenantId,user).orElseThrow(()->new OrganizationDomainException(OrganizationReasonCode.OFFICIAL_MANAGER_TENANT_MEMBERSHIP_REQUIRED,"Official Department Manager must have a Tenant Membership"));
        if(!membership.activeAt(now)) throw new OrganizationDomainException(OrganizationReasonCode.OFFICIAL_MANAGER_TENANT_MEMBERSHIP_REQUIRED,"Official Department Manager must have an active Tenant Membership");
    }

    private void ensureOfficialManagerMembership(Department department,PrincipalRef manager,String actor,String correlation,String eventId,Instant now){
        Optional<DepartmentMembership> existing=memberships.findByUserAndDepartment(department.tenantId(),manager,department.departmentId());
        DepartmentMembership saved;
        if(existing.isEmpty()){
            String id=UUID.nameUUIDFromBytes(("official-manager:"+department.tenantId().value()+":"+department.departmentId().value()+":"+manager.principalId()).getBytes(StandardCharsets.UTF_8)).toString();
            boolean primary=memberships.findActiveByUser(department.tenantId(),manager).stream().noneMatch(DepartmentMembership::primary);
            saved=memberships.save(new DepartmentMembership(new MembershipId(id),department.tenantId(),manager,department.departmentId(),DepartmentMembershipType.MEMBER,primary,now,Optional.empty(),MembershipStatus.ACTIVE,1),0);
        }else{
            DepartmentMembership current=existing.orElseThrow();
            boolean valid=current.status()==MembershipStatus.ACTIVE&&(current.expiresAt().isEmpty()||current.expiresAt().orElseThrow().isAfter(now))&&current.membershipType()!=DepartmentMembershipType.MANAGER;
            if(valid) return;
            boolean primary=current.primary()||memberships.findActiveByUser(department.tenantId(),manager).stream().noneMatch(DepartmentMembership::primary);
            saved=memberships.save(current.update(DepartmentMembershipType.MEMBER,primary,Optional.empty(),MembershipStatus.ACTIVE),current.version());
        }
        publishMembership(saved,actor,correlation,eventId+":official-manager-membership",now);
    }

    private static boolean hasReplacement(UpdateDepartmentMembershipCommand command){
        return command.replacementMembershipId()!=null&&!command.replacementMembershipId().isBlank()&&command.replacementExpectedVersion()!=null;
    }

    private DepartmentMembership requireReplacement(TenantId tenantId,DepartmentMembership current,UpdateDepartmentMembershipCommand command){
        if(command.replacementMembershipId()==null||command.replacementMembershipId().isBlank()||command.replacementExpectedVersion()==null){
            throw new OrganizationDomainException(OrganizationReasonCode.PRIMARY_DEPARTMENT_REPLACEMENT_REQUIRED,"Select a replacement Primary Department before removing the current primary membership");
        }
        DepartmentMembership replacement=memberships.findById(tenantId,new MembershipId(command.replacementMembershipId())).orElseThrow(()->new OrganizationDomainException(OrganizationReasonCode.PRIMARY_DEPARTMENT_REPLACEMENT_INVALID,"Replacement Department Membership was not found"));
        requireVersion(replacement.version(),command.replacementExpectedVersion());
        if(replacement.membershipId().equals(current.membershipId())||!replacement.userPrincipal().equals(current.userPrincipal())||replacement.status()!=MembershipStatus.ACTIVE||replacement.primary()){
            throw new OrganizationDomainException(OrganizationReasonCode.PRIMARY_DEPARTMENT_REPLACEMENT_INVALID,"Replacement must be another active, non-primary Department Membership for the same user");
        }
        return replacement;
    }

    private void promoteReplacement(DepartmentMembership replacement,UpdateDepartmentMembershipCommand command){
        DepartmentMembership promoted=replacement.update(replacement.membershipType(),true,replacement.expiresAt(),MembershipStatus.ACTIVE);
        DepartmentMembership saved=memberships.save(promoted,replacement.version());
        publishMembership(saved,command.actorId(),command.correlationId(),command.eventId()+":replacement",clock.instant());
    }

    private static void requireMutable(Department department){
        if(department.status()==DepartmentStatus.DELETED) throw new OrganizationDomainException(OrganizationReasonCode.INVALID_STATUS_TRANSITION,"Deleted Departments are read-only tombstones retained for audit");
    }

    private Department requireDepartment(TenantId tenantId,String id){return departments.findById(tenantId,new DepartmentId(id)).orElseThrow(()->new IllegalArgumentException("department not found"));}
    private long revise(Department saved,TenantId tenantId,Optional<Department> parent,Instant now,DepartmentRevisionChangeType type,String actor,String reason,String eventId){DepartmentRevision previous=revisions.findCurrent(tenantId,saved.departmentId()).orElseThrow(()->new IllegalStateException("current department revision is missing"));revisions.save(previous.closeAt(now));long next=previous.revision()+1;createRevision(saved,next,pathFor(tenantId,parent,saved),now,type,actor,reason,eventId);return next;}
    private void createRevision(Department saved,long number,DepartmentPath path,Instant now,DepartmentRevisionChangeType type,String actor,String reason,String eventId){DepartmentRevision revision=revisionFrom(saved,number,path,now,type,actor,reason);revisions.save(revision);events.publish(new OrganizationEvents.DepartmentRevisionCreated(eventId+":revision",saved.tenantId().value(),saved.departmentId().value(),number,type.name(),actor,"",now));}
    private DepartmentPath pathFor(TenantId tenantId,Optional<Department> parent,Department saved){return parent.map(value->hierarchyRepository.loadPath(tenantId,value.departmentId())).orElseGet(DepartmentPath::empty).append(saved);}
    private void publishMembership(DepartmentMembership saved,String actor,String correlation,String eventId,Instant now){events.publish(new OrganizationEvents.DepartmentMembershipChanged(eventId,saved.tenantId().value(),saved.membershipId().value(),saved.userPrincipal().principalId(),saved.primary(),actor,correlation,now));}
    private static DepartmentRevision revisionFrom(Department department,long revision,DepartmentPath path,Instant at,DepartmentRevisionChangeType type,String actor,String reason){return new DepartmentRevision(department.tenantId(),department.departmentId(),revision,department.code(),department.name(),department.parentDepartmentId(),path.departmentIds(),path.departmentCodes(),path.departmentNames(),at,Optional.empty(),type,actor,reason,at);}
    private static Optional<DepartmentId> optionalId(String value){return optionalText(value).map(DepartmentId::new);}
    private static Optional<String> optionalText(String value){return value==null||value.isBlank()?Optional.empty():Optional.of(value.trim());}
    private static Optional<PrincipalRef> manager(String value){return optionalText(value).map(item->new PrincipalRef(PrincipalRef.PrincipalType.USER,item));}
    private static void requireAssignableMembershipType(DepartmentMembershipType type){if(type==DepartmentMembershipType.MANAGER) throw new IllegalArgumentException("Use Official Department Manager instead of MANAGER membership type");}
    private static void requireVersion(long actual,long expected){if(expected<1||actual!=expected) throw new OrganizationDomainException(OrganizationReasonCode.VERSION_CONFLICT,"Expected version "+expected+" but was "+actual);}
}
