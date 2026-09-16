package com.opensocket.aievent.core.iam.api.application.service;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.organization.application.command.*;
import com.opensocket.aievent.core.iam.organization.application.port.in.*;
import com.opensocket.aievent.core.iam.organization.application.query.*;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembership;

public final class IamOrganizationAdministrationService {
    private final TenantCommandPort tenants;
    private final DepartmentCommandPort departments;
    private final GroupCommandPort groups;
    private final OrganizationQueryPort queries;
    private final IamIdempotencyExecutor idempotency;

    public IamOrganizationAdministrationService(
            TenantCommandPort tenants,
            DepartmentCommandPort departments,
            GroupCommandPort groups,
            OrganizationQueryPort queries,
            IamIdempotencyExecutor idempotency) {
        this.tenants = tenants;
        this.departments = departments;
        this.groups = groups;
        this.queries = queries;
        this.idempotency = idempotency;
    }

    public TenantResponse createTenant(CreateTenantRequest request, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        String tenantId = request.tenantId() == null || request.tenantId().isBlank()
                ? IamOperationIds.resourceId("tenant", "instance.tenant.create", "INSTANCE", key)
                : request.tenantId().trim();
        return idempotency.execute("INSTANCE", context.actorId(), "instance.tenant.create", key, request, 201,
                TenantResponse.class, () -> TenantResponse.from(tenants.createTenant(new CreateTenantCommand(
                        tenantId, request.tenantCode(), request.tenantName(), request.legalName(),
                        request.timezone(), request.locale(), request.dataRegion(), context.actorId(),
                        context.correlationId(), IamOperationIds.eventId("TENANT_CREATED", "INSTANCE", key)))));
    }

    public TenantResponse changeTenant(String tenantId, String status, long expectedVersion, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        var target = com.opensocket.aievent.core.iam.organization.domain.TenantStatus.valueOf(status);
        return idempotency.execute("INSTANCE", context.actorId(), "instance.tenant.status", key, status, 200,
                TenantResponse.class, () -> TenantResponse.from(tenants.changeTenantStatus(
                        new ChangeTenantStatusCommand(tenantId, target, expectedVersion, context.actorId(),
                                context.correlationId(), IamOperationIds.eventId("TENANT_STATUS_CHANGED", tenantId, key)))));
    }

    public TenantResponse findTenant(String tenantId) {
        return queries.findTenant(new FindTenantQuery(tenantId)).map(TenantResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("TENANT_NOT_FOUND"));
    }

    public DepartmentResponse createDepartment(CreateDepartmentRequest request, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        String tenantId = context.activeTenantId();
        String departmentId = request.departmentId() == null || request.departmentId().isBlank()
                ? IamOperationIds.resourceId("dept", "identity.department.create", tenantId, key)
                : request.departmentId().trim();
        return idempotency.execute(tenantId, context.actorId(), "identity.department.create", key, request, 201,
                DepartmentResponse.class, () -> DepartmentResponse.from(departments.createDepartment(
                        new CreateDepartmentCommand(tenantId, departmentId, request.code(), request.name(),
                                request.parentDepartmentId(), request.managerUserId(), request.displayOrder(),
                                request.reason(), context.actorId(), context.correlationId(),
                                IamOperationIds.eventId("DEPARTMENT_CREATED", tenantId, key)))));
    }

    public DepartmentResponse updateDepartment(
            String departmentId, UpdateDepartmentRequest request, long expectedVersion, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        String tenantId = context.activeTenantId();
        return idempotency.execute(tenantId, context.actorId(), "identity.department.update", key, request, 200,
                DepartmentResponse.class, () -> DepartmentResponse.from(departments.updateDepartment(
                        new UpdateDepartmentCommand(tenantId, departmentId, request.code(), request.name(),
                                request.parentDepartmentId(), request.managerUserId(), request.displayOrder(), expectedVersion,
                                request.reason(), context.actorId(), context.correlationId(),
                                IamOperationIds.eventId("DEPARTMENT_UPDATED", tenantId, key)))));
    }

    /**
     * Changes only the Official Department Manager organizational relationship.
     * This operation never creates an RBAC Role or Role Binding.
     */
    public DepartmentResponse assignOfficialDepartmentManager(
            String departmentId,
            AssignOfficialDepartmentManagerRequest request,
            long expectedVersion,
            IamApiRequestContext context) {
        DepartmentResponse current = findDepartment(departmentId, context);
        UpdateDepartmentRequest update = new UpdateDepartmentRequest(
                current.code(), current.name(), current.parentDepartmentId(),
                request.normalizedManagerUserId(), current.displayOrder(), context.requireAuditReason());
        return updateDepartment(departmentId, update, expectedVersion, context);
    }

    public DepartmentResponse changeDepartmentStatus(
            String departmentId, ChangeDepartmentStatusRequest request, long expectedVersion, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        String tenantId = context.activeTenantId();
        return idempotency.execute(tenantId, context.actorId(), "identity.department.status", key, request, 200,
                DepartmentResponse.class, () -> DepartmentResponse.from(departments.changeDepartmentStatus(
                        new ChangeDepartmentStatusCommand(tenantId, departmentId, request.status(), expectedVersion,
                                request.reason(), context.actorId(), context.correlationId(),
                                IamOperationIds.eventId("DEPARTMENT_STATUS_CHANGED", tenantId, key)))));
    }

    public DepartmentResponse moveDepartment(
            String departmentId, MoveDepartmentRequest request, long expectedVersion, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        String tenantId = context.activeTenantId();
        return idempotency.execute(tenantId, context.actorId(), "identity.department.move", key, request, 200,
                DepartmentResponse.class, () -> DepartmentResponse.from(departments.moveDepartment(
                        new MoveDepartmentCommand(tenantId, departmentId, request.parentDepartmentId(),
                                expectedVersion, request.reason(), context.actorId(), context.correlationId(),
                                IamOperationIds.eventId("DEPARTMENT_MOVED", tenantId, key)))));
    }

    public DepartmentResponse findDepartment(String departmentId, IamApiRequestContext context) {
        return queries.findDepartment(new FindDepartmentQuery(context.activeTenantId(), departmentId))
                .map(DepartmentResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("DEPARTMENT_NOT_FOUND"));
    }

    public GroupResponse findGroup(String groupId, IamApiRequestContext context) {
        return queries.findGroup(new FindGroupQuery(context.activeTenantId(), groupId)).map(GroupResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("GROUP_NOT_FOUND"));
    }

    public GroupResponse createGroup(CreateGroupRequest request, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        String tenantId = context.activeTenantId();
        String groupId = request.groupId() == null || request.groupId().isBlank()
                ? IamOperationIds.resourceId("group", "identity.group.create", tenantId, key)
                : request.groupId().trim();
        return idempotency.execute(tenantId, context.actorId(), "identity.group.create", key, request, 201,
                GroupResponse.class, () -> GroupResponse.from(groups.createGroup(new CreateGroupCommand(
                        tenantId, groupId, request.code(), request.name(), request.type(),
                        request.parentGroupId(), request.ownerDepartmentId(), request.description(),
                        context.actorId(), context.correlationId(),
                        IamOperationIds.eventId("GROUP_CREATED", tenantId, key)))));
    }

    public GroupResponse updateGroup(String groupId, UpdateGroupRequest request, long expectedVersion, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey(); String tenantId = context.activeTenantId();
        return idempotency.execute(tenantId, context.actorId(), "identity.group.update", key, request, 200,
                GroupResponse.class, () -> GroupResponse.from(groups.updateGroup(new UpdateGroupCommand(
                        tenantId, groupId, request.code(), request.name(), request.type(), request.parentGroupId(),
                        request.ownerDepartmentId(), request.description(), expectedVersion, request.reason(),
                        context.actorId(), context.correlationId(), IamOperationIds.eventId("GROUP_UPDATED", tenantId, key)))));
    }

    public GroupResponse changeGroupStatus(String groupId, ChangeGroupStatusRequest request, long expectedVersion, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey(); String tenantId = context.activeTenantId();
        return idempotency.execute(tenantId, context.actorId(), "identity.group.status", key, request, 200,
                GroupResponse.class, () -> GroupResponse.from(groups.changeGroupStatus(new ChangeGroupStatusCommand(
                        tenantId, groupId, request.status(), expectedVersion, request.reason(), context.actorId(),
                        context.correlationId(), IamOperationIds.eventId("GROUP_STATUS_CHANGED", tenantId, key)))));
    }

    public MembershipResponse addTenantMembership(
            String userId, AddTenantMembershipRequest request, IamApiRequestContext context) {
        String tenantId = context.activeTenantId();
        if (!tenantId.equals(request.tenantId())) throw new IllegalArgumentException("AUTH_SCOPE_MISMATCH");
        CreateTenantMembershipRequest expanded = new CreateTenantMembershipRequest(
                request.membershipId(), userId,
                com.opensocket.aievent.core.iam.organization.domain.MembershipStatus.ACTIVE,
                request.employeeId(), request.expiresAt(), false,
                com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource.ADMIN_CREATED,
                context.requireAuditReason());
        return createTenantMembership(tenantId, expanded, context);
    }

    public MembershipResponse createTenantMembership(
            String tenantId, CreateTenantMembershipRequest request, IamApiRequestContext context) {
        requireTenant(tenantId, context);
        String key = context.requireIdempotencyKey();
        TenantMembership membership = idempotency.execute(tenantId, context.actorId(),
                "identity.tenant_membership.create", key, request, 201, TenantMembership.class,
                () -> tenants.addTenantMembership(new AddTenantMembershipCommand(
                        request.membershipId() == null || request.membershipId().isBlank()
                                ? IamOperationIds.resourceId("tenantmem", "identity.tenant_membership.create", tenantId + "|" + request.userId(), key)
                                : request.membershipId(),
                        tenantId, request.userId(), request.employeeId(), request.expiresAt(),
                        request.initialStatus(), request.defaultTenant(), request.membershipSource(), request.reason(),
                        context.actorId(), context.correlationId(),
                        IamOperationIds.eventId("TENANT_MEMBERSHIP_CHANGED", tenantId, key))));
        return tenantMembershipResponse(membership);
    }

    public MembershipResponse updateTenantMembership(
            String tenantId, String membershipId, UpdateTenantMembershipRequest request,
            long expectedVersion, IamApiRequestContext context) {
        requireTenant(tenantId, context);
        String key = context.requireIdempotencyKey();
        TenantMembership membership = idempotency.execute(tenantId, context.actorId(),
                "identity.tenant_membership.update", key, request, 200, TenantMembership.class,
                () -> tenants.updateTenantMembership(new UpdateTenantMembershipCommand(
                        tenantId, membershipId, request.employeeId(), request.expiresAt(), request.defaultTenant(),
                        expectedVersion, request.reason(), context.actorId(), context.correlationId(),
                        IamOperationIds.eventId("TENANT_MEMBERSHIP_CHANGED", tenantId, key))));
        return tenantMembershipResponse(membership);
    }

    public MembershipResponse changeTenantMembershipStatus(
            String tenantId, String membershipId, ChangeTenantMembershipStatusRequest request,
            long expectedVersion, IamApiRequestContext context) {
        requireTenant(tenantId, context);
        String key = context.requireIdempotencyKey();
        TenantMembership membership = idempotency.execute(tenantId, context.actorId(),
                "identity.tenant_membership.status", key, request, 200, TenantMembership.class,
                () -> tenants.changeTenantMembershipStatus(new ChangeTenantMembershipStatusCommand(
                        tenantId, membershipId, request.status(), expectedVersion, request.reason(),
                        context.actorId(), context.correlationId(),
                        IamOperationIds.eventId("TENANT_MEMBERSHIP_CHANGED", tenantId, key))));
        return tenantMembershipResponse(membership);
    }

    public MembershipResponse addDepartmentMembership(
            String userId, AddDepartmentMembershipRequest request, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        String tenantId = context.activeTenantId();
        var membership = idempotency.execute(tenantId, context.actorId(), "identity.membership.department.add",
                key, request, 201,
                com.opensocket.aievent.core.iam.organization.domain.DepartmentMembership.class,
                () -> departments.addDepartmentMembership(new AddDepartmentMembershipCommand(
                        request.membershipId() == null || request.membershipId().isBlank()
                                ? IamOperationIds.resourceId("deptmem", "identity.membership.department.add", tenantId + "|" + userId, key)
                                : request.membershipId(), tenantId, userId, request.departmentId(), request.membershipType(),
                        request.primary(), request.expiresAt(), context.actorId(), context.correlationId(),
                        IamOperationIds.eventId("DEPARTMENT_MEMBERSHIP_CHANGED", tenantId, key))));
        return new MembershipResponse(membership.membershipId().value(), "DEPARTMENT", tenantId, userId,
                membership.departmentId().value(), membership.membershipType().name(), membership.status().name(),
                membership.primary(), membership.effectiveAt(), membership.expiresAt().orElse(null), membership.version());
    }

    public MembershipResponse updateDepartmentMembership(
            String membershipId, UpdateDepartmentMembershipRequest request, long expectedVersion, boolean remove,
            IamApiRequestContext context) {
        String key = context.requireIdempotencyKey(); String tenantId = context.activeTenantId();
        var membership = idempotency.execute(tenantId, context.actorId(), remove ? "identity.membership.department.remove" : "identity.membership.department.update",
                key, request, 200, com.opensocket.aievent.core.iam.organization.domain.DepartmentMembership.class,
                () -> departments.updateDepartmentMembership(new UpdateDepartmentMembershipCommand(
                        tenantId, membershipId, request.membershipType(), request.primary(), request.expiresAt(), remove,
                        request.replacementMembershipId(), request.replacementExpectedVersion(), expectedVersion, context.actorId(), context.correlationId(),
                        IamOperationIds.eventId("DEPARTMENT_MEMBERSHIP_CHANGED", tenantId, key))));
        return new MembershipResponse(membership.membershipId().value(), "DEPARTMENT", tenantId,
                membership.userPrincipal().principalId(), membership.departmentId().value(), membership.membershipType().name(),
                membership.status().name(), membership.primary(), membership.effectiveAt(), membership.expiresAt().orElse(null), membership.version());
    }

    public MembershipResponse addGroupMembership(
            String userId, AddGroupMembershipRequest request, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        String tenantId = context.activeTenantId();
        var membership = idempotency.execute(tenantId, context.actorId(), "identity.membership.group.add",
                key, request, 201, com.opensocket.aievent.core.iam.organization.domain.GroupMembership.class,
                () -> groups.addGroupMembership(new AddGroupMembershipCommand(
                        request.membershipId() == null || request.membershipId().isBlank()
                                ? IamOperationIds.resourceId("groupmem", "identity.membership.group.add", tenantId + "|" + userId, key)
                                : request.membershipId(), tenantId, userId, request.groupId(), request.membershipRole(),
                        request.expiresAt(), context.actorId(), context.correlationId(),
                        IamOperationIds.eventId("GROUP_MEMBERSHIP_CHANGED", tenantId, key))));
        return new MembershipResponse(membership.membershipId().value(), "GROUP", tenantId, userId,
                membership.groupId().value(), membership.membershipRole().name(), membership.status().name(), false,
                membership.effectiveAt(), membership.expiresAt().orElse(null), membership.version());
    }

    public MembershipResponse updateGroupMembership(
            String membershipId, UpdateGroupMembershipRequest request, long expectedVersion, boolean remove,
            IamApiRequestContext context) {
        String key = context.requireIdempotencyKey(); String tenantId = context.activeTenantId();
        var membership = idempotency.execute(tenantId, context.actorId(), remove ? "identity.membership.group.remove" : "identity.membership.group.update",
                key, request, 200, com.opensocket.aievent.core.iam.organization.domain.GroupMembership.class,
                () -> groups.updateGroupMembership(new UpdateGroupMembershipCommand(
                        tenantId, membershipId, request.membershipRole(), request.expiresAt(), remove, expectedVersion,
                        context.actorId(), context.correlationId(), IamOperationIds.eventId("GROUP_MEMBERSHIP_CHANGED", tenantId, key))));
        return new MembershipResponse(membership.membershipId().value(), "GROUP", tenantId,
                membership.userPrincipal().principalId(), membership.groupId().value(), membership.membershipRole().name(),
                membership.status().name(), false, membership.effectiveAt(), membership.expiresAt().orElse(null), membership.version());
    }

    private static MembershipResponse tenantMembershipResponse(TenantMembership membership) {
        return new MembershipResponse(
                membership.membershipId().value(), "TENANT", membership.tenantId().value(),
                membership.userPrincipal().principalId(), membership.tenantId().value(),
                membership.membershipSource().name(), membership.status().name(), membership.defaultTenant(),
                membership.joinedAt(), membership.expiresAt().orElse(null), membership.version(),
                membership.employeeId().orElse(null));
    }

    private static void requireTenant(String tenantId, IamApiRequestContext context) {
        if (!context.activeTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("AUTH_SCOPE_MISMATCH");
        }
    }
}
