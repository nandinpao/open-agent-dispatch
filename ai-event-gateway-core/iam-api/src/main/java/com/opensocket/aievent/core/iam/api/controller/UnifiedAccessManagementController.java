package com.opensocket.aievent.core.iam.api.controller;

import com.opensocket.aievent.core.iam.api.application.port.*;
import com.opensocket.aievent.core.iam.api.application.service.*;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContextFactory;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.pagination.*;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.api.security.*;
import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembershipType;
import com.opensocket.aievent.core.iam.organization.domain.GroupMembershipRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Set;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * R4 canonical administration facade.
 *
 * <p>This is the only new administration surface for Human Users, Tenant membership,
 * organization, RBAC, Effective Access and security actions. It delegates to the existing
 * transactional application services; it does not create a second domain model.</p>
 */
@RestController
@RequestMapping("/api/admin/access")
@ConditionalOnBean({
        IamPlatformUserAdministrationApiPort.class,
        IamUserProvisioningApiPort.class,
        IamUserAdministrationApiPort.class,
        IamOrganizationAdministrationService.class,
        IamPeopleBulkAdministrationService.class,
        IamRbacAdministrationService.class,
        IamEffectiveAccessQueryService.class,
        IamAdministrationProjectionPort.class,
        IamSessionAdministrationApiPort.class,
        IamCredentialAdministrationApiPort.class,
        IamPermissionGuard.class,
        IamRbacHardeningService.class,
        IamMachineOwnershipAdministrationPort.class,
        R7SensitiveOperationGuard.class})
@ConditionalOnProperty(prefix = "aeg.iam.api", name = "enabled", havingValue = "true")
public class UnifiedAccessManagementController {
    private final IamPlatformUserAdministrationApiPort platformUsers;
    private final IamUserProvisioningApiPort provisioning;
    private final IamUserAdministrationApiPort tenantUsers;
    private final IamOrganizationAdministrationService organization;
    private final IamPeopleBulkAdministrationService peopleBulk;
    private final IamRbacAdministrationService rbac;
    private final IamEffectiveAccessQueryService effectiveAccess;
    private final IamAdministrationProjectionPort projections;
    private final IamMachineOwnershipAdministrationPort machineOwnership;
    private final IamSessionAdministrationApiPort sessions;
    private final IamCredentialAdministrationApiPort credentials;
    private final IamPermissionGuard guard;
    private final IamRbacHardeningService hardening;
    private final R7SensitiveOperationGuard sensitiveWrites;
    private final IamApiRequestContextFactory contexts;
    private final IamPaginationPolicy pagination;
    private final UiFeatureEntitlementService uiFeatures = new UiFeatureEntitlementService();

    public UnifiedAccessManagementController(
            IamPlatformUserAdministrationApiPort platformUsers,
            IamUserProvisioningApiPort provisioning,
            IamUserAdministrationApiPort tenantUsers,
            IamOrganizationAdministrationService organization,
            IamPeopleBulkAdministrationService peopleBulk,
            IamRbacAdministrationService rbac,
            IamEffectiveAccessQueryService effectiveAccess,
            IamAdministrationProjectionPort projections,
            IamMachineOwnershipAdministrationPort machineOwnership,
            IamSessionAdministrationApiPort sessions,
            IamCredentialAdministrationApiPort credentials,
            IamPermissionGuard guard,
            IamRbacHardeningService hardening,
            R7SensitiveOperationGuard sensitiveWrites,
            IamApiRequestContextFactory contexts,
            IamPaginationPolicy pagination) {
        this.platformUsers = platformUsers;
        this.provisioning = provisioning;
        this.tenantUsers = tenantUsers;
        this.organization = organization;
        this.peopleBulk = peopleBulk;
        this.rbac = rbac;
        this.effectiveAccess = effectiveAccess;
        this.projections = projections;
        this.machineOwnership = machineOwnership;
        this.sessions = sessions;
        this.credentials = credentials;
        this.guard = guard;
        this.hardening = hardening;
        this.sensitiveWrites = sensitiveWrites;
        this.contexts = contexts;
        this.pagination = pagination;
    }

    // ---------- Instance users and Tenants ----------

    @GetMapping("/users")
    public CursorPage<UserResponse> users(
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String tenantId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(context, IamPermissions.PLATFORM_USER_READ, "USER", "");
        return projections.platformUsers(text, status, tenantId, pagination.limit(limit), cursor);
    }

    @GetMapping("/users/{userId}")
    public UserResponse user(@PathVariable String userId, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(context, IamPermissions.PLATFORM_USER_READ, "USER", userId);
        return requirePlatformUser(userId);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createPlatformUser(
            @Valid @RequestBody CreateUserRequest body, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requireSensitiveWrite(context);
        guard.requireInstance(context, IamPermissions.PLATFORM_USER_CREATE, "USER", body.authorizationTarget());
        return ResponseEntity.status(201).body(platformUsers.createUser(body, context));
    }

    @PutMapping("/users/{userId}")
    public UserResponse updatePlatformUser(
            @PathVariable String userId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateUserRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requireSensitiveWrite(context);
        guard.requireInstance(context, IamPermissions.PLATFORM_USER_UPDATE, "USER", userId);
        requirePlatformUser(userId);
        return platformUsers.updateUser(userId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @PostMapping("/users/{userId}/status")
    public UserResponse changePlatformUserStatus(
            @PathVariable String userId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ChangeUserStatusRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requireSensitiveWrite(context);
        guard.requireInstance(context, IamPermissions.PLATFORM_USER_UPDATE, "USER", userId);
        requirePlatformUser(userId);
        return platformUsers.changeUserStatus(userId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @GetMapping("/users/{userId}/tenant-memberships")
    public CursorPage<PlatformTenantMembershipResponse> platformMemberships(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(context, IamPermissions.PLATFORM_USER_READ, "USER", userId);
        requirePlatformUser(userId);
        return projections.platformMemberships(userId, pagination.limit(limit), cursor);
    }

    @PostMapping("/users/{userId}/require-password-change")
    public ResponseEntity<Void> requirePasswordChange(@PathVariable String userId, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requireSensitiveWrite(context);
        guard.requireInstance(context, IamPermissions.PLATFORM_USER_SECURITY, "USER", userId);
        requirePlatformUser(userId);
        platformUsers.requirePasswordChange(userId, context);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/revoke-all-sessions")
    public ResponseEntity<Void> revokeAllPlatformSessions(@PathVariable String userId, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requireSensitiveWrite(context);
        guard.requireInstance(context, IamPermissions.PLATFORM_USER_SECURITY, "USER", userId);
        requirePlatformUser(userId);
        platformUsers.revokeAllSessions(userId, context);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants")
    public OffsetPage<TenantResponse> tenants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(context, IamPermissions.TENANT_READ, "TENANT", "");
        return projections.tenants(pagination.page(page), pagination.sizeCapped(size), text, status);
    }

    @GetMapping("/tenants/{tenantId}")
    public TenantResponse tenant(@PathVariable String tenantId, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        guard.requireInstance(context, IamPermissions.TENANT_READ, "TENANT", tenantId);
        return organization.findTenant(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/workspace-summary")
    public TenantWorkspaceSummaryResponse tenantWorkspaceSummary(
            @PathVariable String tenantId, HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_READ, "TENANT", tenantId);
        return projections.tenantWorkspaceSummary(tenantId);
    }

    @PostMapping("/tenants")
    public ResponseEntity<TenantResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest body, HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requireSensitiveWrite(context);
        guard.requireInstance(context, IamPermissions.TENANT_MANAGE, "TENANT", body.tenantId());
        return ResponseEntity.status(201).body(organization.createTenant(body, context));
    }

    @PostMapping("/tenants/{tenantId}/status/{status}")
    public TenantResponse changeTenantStatus(
            @PathVariable String tenantId,
            @PathVariable String status,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = contexts.from(request);
        requireSensitiveWrite(context);
        guard.requireInstance(context, IamPermissions.TENANT_MANAGE, "TENANT", tenantId);
        return organization.changeTenant(tenantId, status.toUpperCase(Locale.ROOT), context.requireExpectedVersion(ifMatch), context);
    }

    // ---------- Tenant users and membership ----------

    @GetMapping("/tenants/{tenantId}/users")
    public CursorPage<UserResponse> tenantUsers(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String membershipStatus,
            @RequestParam(defaultValue = "") String roleId,
            @RequestParam(defaultValue = "") String departmentId,
            @RequestParam(defaultValue = "") String groupId,
            @RequestParam(defaultValue = "") String signInState,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        int resolvedLimit = pagination.limit(limit);
        if (isInstanceRoot(context)) {
            guard.requireTenant(context, IamPermissions.USER_READ, "USER", "");
            return projections.users(tenantId, text, status, membershipStatus, roleId, departmentId, groupId, signInState, resolvedLimit, cursor);
        }
        IamEffectiveAuthorityResponse authority = effectiveAccess.effectiveAuthority(tenantId, context.actorId());
        Set<String> scopes = authority.permissionScopes().getOrDefault(IamPermissions.USER_READ, Set.of());
        if (scopes.contains("TENANT:" + tenantId)) {
            guard.requireTenant(context, IamPermissions.USER_READ, "USER", "");
            return projections.users(tenantId, text, status, membershipStatus, roleId, departmentId, groupId, signInState, resolvedLimit, cursor);
        }
        Set<String> departmentIds = effectiveDepartmentScopeIds(tenantId, scopes);
        Set<String> groupIds = scopeIds(scopes, "GROUP");
        if (departmentIds.isEmpty() && groupIds.isEmpty()) {
            guard.requireTenant(context, IamPermissions.USER_READ, "USER", "");
        }
        departmentIds.forEach(id -> guard.requireDepartment(context, IamPermissions.USER_READ, "USER", id));
        groupIds.forEach(id -> guard.requireGroup(context, IamPermissions.USER_READ, "USER", id));
        return projections.usersWithin(tenantId, departmentIds, groupIds, text, status, membershipStatus, roleId,
                departmentId, groupId, signInState, resolvedLimit, cursor);
    }

    @GetMapping("/tenants/{tenantId}/available-users")
    public CursorPage<UserResponse> availableTenantUsers(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_MANAGE, "USER", "");
        String query = text == null ? "" : text.trim();
        if (query.length() < 2) {
            throw IamApiException.badRequest(
                    "IDENTITY_AVAILABLE_SEARCH_TEXT_REQUIRED",
                    "Enter at least two characters before searching the global identity directory");
        }
        return projections.availableUsers(tenantId, query, pagination.limit(limit), cursor);
    }

    @PostMapping("/tenants/{tenantId}/people/bulk-actions")
    public PeopleBulkActionResponse peopleBulkAction(
            @PathVariable String tenantId,
            @Valid @RequestBody PeopleBulkActionRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.USER_READ, "BULK_PEOPLE", tenantId);
        return peopleBulk.execute(body, context);
    }

    @GetMapping("/tenants/{tenantId}/users/{userId}")
    public UserResponse tenantUser(@PathVariable String tenantId, @PathVariable String userId, HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireUserReadScope(context, tenantId, userId);
        return requireTenantUser(tenantId, userId);
    }

    @PostMapping("/tenants/{tenantId}/users")
    public ResponseEntity<UserResponse> createTenantUser(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateUserRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.USER_CREATE, "USER", body.authorizationTarget());
        return ResponseEntity.status(201).body(provisioning.createUser(body, context));
    }

    @PutMapping("/tenants/{tenantId}/users/{userId}")
    public UserResponse updateTenantUser(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateUserRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.USER_UPDATE, "USER", userId);
        requireTenantUser(tenantId, userId);
        return tenantUsers.updateUser(userId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @GetMapping("/tenants/{tenantId}/users/{userId}/overview")
    public UserAccessOverviewResponse userOverview(
            @PathVariable String tenantId,
            @PathVariable String userId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.ROLE_BINDING_READ, "USER", userId);
        UserResponse user = requireTenantUser(tenantId, userId);
        return new UserAccessOverviewResponse(
                tenantId,
                user,
                projections.memberships(tenantId, userId, pagination.limit(100), null).items(),
                effectiveAccess.effectiveAccess(tenantId, userId),
                projections.userAuthenticationReadiness(tenantId, userId),
                projections.sessions(tenantId, userId, pagination.limit(100), null).items(),
                projections.machineOwnershipImpact(tenantId, userId));
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/machine-ownership/transfer")
    public MachineOwnershipTransferResponse transferMachineOwnership(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody MachineOwnershipTransferRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.USER_UPDATE, "USER", userId);
        guard.requireTenant(context, IamPermissions.TOKEN_MANAGE, "MACHINE_OWNERSHIP", userId);
        requireTenantUser(tenantId, userId);
        requireTenantUser(tenantId, body.toUserId());
        if (userId.equals(body.toUserId())) {
            throw IamApiException.badRequest(
                    "MACHINE_OWNERSHIP_TRANSFER_TARGET_INVALID",
                    "Choose a different active Person to receive machine and Agent ownership");
        }
        String reason = body.reason().trim();
        return machineOwnership.transfer(tenantId, userId, body.toUserId(), context.actorId(), reason);
    }

    @GetMapping("/tenants/{tenantId}/users/{userId}/memberships")
    public CursorPage<MembershipResponse> memberships(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_READ, "USER", userId);
        return projections.memberships(tenantId, userId, pagination.limit(limit), cursor);
    }

    @GetMapping("/tenants/{tenantId}/members")
    public CursorPage<MembershipResponse> tenantMembers(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_READ, "TENANT", tenantId);
        return projections.tenantMemberships(tenantId, pagination.limit(limit), cursor);
    }

    @PostMapping("/tenants/{tenantId}/members")
    public ResponseEntity<MembershipResponse> addTenantMember(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateTenantMembershipRequest body,
            HttpServletRequest request) {
        return createTenantMembership(tenantId, body, request);
    }

    @PostMapping("/tenants/{tenantId}/memberships")
    public ResponseEntity<MembershipResponse> createTenantMembership(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateTenantMembershipRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_MANAGE, "USER", body.userId());
        return ResponseEntity.status(201).body(organization.createTenantMembership(tenantId, body, context));
    }

    @PutMapping("/tenants/{tenantId}/memberships/{membershipId}")
    public MembershipResponse updateTenantMembership(
            @PathVariable String tenantId,
            @PathVariable String membershipId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateTenantMembershipRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_MANAGE, "MEMBERSHIP", membershipId);
        return organization.updateTenantMembership(tenantId, membershipId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @PostMapping("/tenants/{tenantId}/memberships/{membershipId}/status")
    public MembershipResponse changeTenantMembershipStatus(
            @PathVariable String tenantId,
            @PathVariable String membershipId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ChangeTenantMembershipStatusRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.TENANT_MEMBERSHIP_MANAGE, "MEMBERSHIP", membershipId);
        return organization.changeTenantMembershipStatus(tenantId, membershipId, body, context.requireExpectedVersion(ifMatch), context);
    }

    // ---------- Departments, Manager and Group organization ----------

    @GetMapping("/tenants/{tenantId}/departments")
    public OffsetPage<DepartmentResponse> departments(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        int resolvedPage = pagination.page(page);
        int resolvedSize = pagination.size(size);
        if (isInstanceRoot(context)) {
            guard.requireTenant(context, IamPermissions.DEPARTMENT_READ, "DEPARTMENT", tenantId);
            return projections.departments(tenantId, resolvedPage, resolvedSize, text, status);
        }
        IamEffectiveAuthorityResponse authority = effectiveAccess.effectiveAuthority(tenantId, context.actorId());
        Set<String> scopes = authority.permissionScopes().getOrDefault(IamPermissions.DEPARTMENT_READ, Set.of());
        if (scopes.contains("TENANT:" + tenantId)) {
            guard.requireTenant(context, IamPermissions.DEPARTMENT_READ, "DEPARTMENT", tenantId);
            return projections.departments(tenantId, resolvedPage, resolvedSize, text, status);
        }
        Set<String> departmentIds = effectiveDepartmentScopeIds(tenantId, scopes);
        if (departmentIds.isEmpty()) {
            guard.requireTenant(context, IamPermissions.DEPARTMENT_READ, "DEPARTMENT", tenantId);
        }
        departmentIds.forEach(id -> guard.requireDepartment(context, IamPermissions.DEPARTMENT_READ, id));
        return projections.departmentsWithin(tenantId, departmentIds, resolvedPage, resolvedSize, text, status);
    }

    @GetMapping("/tenants/{tenantId}/departments/{departmentId}")
    public DepartmentResponse department(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_READ, departmentId);
        return organization.findDepartment(departmentId, context);
    }

    @GetMapping("/tenants/{tenantId}/departments/{departmentId}/retirement-preview")
    public OrganizationRetirementPreviewResponse departmentRetirementPreview(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_MANAGE, departmentId);
        return projections.departmentRetirementPreview(tenantId, departmentId);
    }

    @PostMapping("/tenants/{tenantId}/departments")
    public ResponseEntity<DepartmentResponse> createDepartment(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateDepartmentRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        if (body.parentDepartmentId() == null || body.parentDepartmentId().isBlank()) {
            guard.requireTenant(context, IamPermissions.DEPARTMENT_MANAGE, "DEPARTMENT", tenantId);
        } else {
            guard.requireDepartment(context, IamPermissions.DEPARTMENT_MANAGE, body.parentDepartmentId());
        }
        return ResponseEntity.status(201).body(organization.createDepartment(body, context));
    }

    @PutMapping("/tenants/{tenantId}/departments/{departmentId}")
    public DepartmentResponse updateDepartment(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateDepartmentRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_MANAGE, departmentId);
        DepartmentResponse current = organization.findDepartment(departmentId, context);
        if (!sameOptionalId(current.parentDepartmentId(), body.parentDepartmentId())) {
            requireDepartmentDestinationManage(context, body.parentDepartmentId());
        }
        return organization.updateDepartment(departmentId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @PostMapping("/tenants/{tenantId}/departments/{departmentId}/status")
    public DepartmentResponse changeDepartmentStatus(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ChangeDepartmentStatusRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_MANAGE, departmentId);
        return organization.changeDepartmentStatus(departmentId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @PostMapping("/tenants/{tenantId}/departments/{departmentId}/move")
    public DepartmentResponse moveDepartment(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody MoveDepartmentRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_MANAGE, departmentId);
        requireDepartmentDestinationManage(context, body.parentDepartmentId());
        return organization.moveDepartment(departmentId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @PutMapping("/tenants/{tenantId}/departments/{departmentId}/official-manager")
    public DepartmentResponse assignOfficialDepartmentManager(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody AssignOfficialDepartmentManagerRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_MANAGE, departmentId);
        return organization.assignOfficialDepartmentManager(departmentId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @GetMapping("/tenants/{tenantId}/departments/{departmentId}/members")
    public CursorPage<MembershipResponse> departmentMembers(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_READ, departmentId);
        return projections.departmentMembers(tenantId, departmentId, pagination.limit(limit), cursor);
    }

    @GetMapping("/tenants/{tenantId}/departments/{departmentId}/eligible-managers")
    public CursorPage<UserResponse> eligibleDepartmentManagers(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_READ, departmentId);
        return projections.eligibleDepartmentManagers(tenantId, departmentId, text, pagination.limit(limit), cursor);
    }

    @PostMapping("/tenants/{tenantId}/departments/{departmentId}/members")
    public ResponseEntity<MembershipResponse> addDepartmentMember(
            @PathVariable String tenantId,
            @PathVariable String departmentId,
            @Valid @RequestBody AddDepartmentMemberRequest body,
            HttpServletRequest request) {
        return addDepartmentMembership(tenantId, body.userId(), body.toMembershipRequest(departmentId), request);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/department-memberships")
    public ResponseEntity<MembershipResponse> addDepartmentMembership(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody AddDepartmentMembershipRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireDepartment(context, IamPermissions.MEMBERSHIP_MANAGE, "USER", body.departmentId());
        return ResponseEntity.status(201).body(organization.addDepartmentMembership(userId, body, context));
    }

    @PutMapping("/tenants/{tenantId}/department-memberships/{membershipId}")
    public MembershipResponse updateDepartmentMembership(
            @PathVariable String tenantId,
            @PathVariable String membershipId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateDepartmentMembershipRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        requireMembershipScope(context, tenantId, membershipId, "DEPARTMENT");
        return organization.updateDepartmentMembership(membershipId, body, context.requireExpectedVersion(ifMatch), false, context);
    }

    @DeleteMapping("/tenants/{tenantId}/department-memberships/{membershipId}")
    public MembershipResponse removeDepartmentMembership(
            @PathVariable String tenantId,
            @PathVariable String membershipId,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        requireMembershipScope(context, tenantId, membershipId, "DEPARTMENT");
        UpdateDepartmentMembershipRequest command = new UpdateDepartmentMembershipRequest(
                DepartmentMembershipType.MEMBER, false, null,
                request.getHeader("X-Replacement-Membership-Id"),
                optionalLong(request.getHeader("X-Replacement-Membership-Version")),
                context.requireAuditReason());
        return organization.updateDepartmentMembership(membershipId, command, context.requireExpectedVersion(ifMatch), true, context);
    }

    @GetMapping("/tenants/{tenantId}/groups")
    public OffsetPage<GroupResponse> groups(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        int resolvedPage = pagination.page(page);
        int resolvedSize = pagination.size(size);
        if (isInstanceRoot(context)) {
            guard.requireTenant(context, IamPermissions.GROUP_READ, "GROUP", tenantId);
            return projections.groups(tenantId, resolvedPage, resolvedSize, text, type, status);
        }
        IamEffectiveAuthorityResponse authority = effectiveAccess.effectiveAuthority(tenantId, context.actorId());
        Set<String> scopes = authority.permissionScopes().getOrDefault(IamPermissions.GROUP_READ, Set.of());
        if (scopes.contains("TENANT:" + tenantId)) {
            guard.requireTenant(context, IamPermissions.GROUP_READ, "GROUP", tenantId);
            return projections.groups(tenantId, resolvedPage, resolvedSize, text, type, status);
        }
        Set<String> groupIds = scopeIds(scopes, "GROUP");
        if (groupIds.isEmpty()) {
            guard.requireTenant(context, IamPermissions.GROUP_READ, "GROUP", tenantId);
        }
        groupIds.forEach(id -> guard.requireGroup(context, IamPermissions.GROUP_READ, id));
        return projections.groupsWithin(tenantId, groupIds, resolvedPage, resolvedSize, text, type, status);
    }

    @GetMapping("/tenants/{tenantId}/groups/{groupId}")
    public GroupResponse group(@PathVariable String tenantId, @PathVariable String groupId, HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireGroup(context, IamPermissions.GROUP_READ, groupId);
        return organization.findGroup(groupId, context);
    }

    @GetMapping("/tenants/{tenantId}/groups/{groupId}/retirement-preview")
    public OrganizationRetirementPreviewResponse groupRetirementPreview(
            @PathVariable String tenantId,
            @PathVariable String groupId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireGroup(context, IamPermissions.GROUP_MANAGE, groupId);
        return projections.groupRetirementPreview(tenantId, groupId);
    }

    @PostMapping("/tenants/{tenantId}/groups")
    public ResponseEntity<GroupResponse> createGroup(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateGroupRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        if (body.parentGroupId() == null || body.parentGroupId().isBlank()) {
            guard.requireTenant(context, IamPermissions.GROUP_MANAGE, "GROUP", tenantId);
        } else {
            guard.requireGroup(context, IamPermissions.GROUP_MANAGE, body.parentGroupId());
        }
        return ResponseEntity.status(201).body(organization.createGroup(body, context));
    }

    @PutMapping("/tenants/{tenantId}/groups/{groupId}")
    public GroupResponse updateGroup(
            @PathVariable String tenantId,
            @PathVariable String groupId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateGroupRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireGroup(context, IamPermissions.GROUP_MANAGE, groupId);
        GroupResponse current = organization.findGroup(groupId, context);
        if (!sameOptionalId(current.parentGroupId(), body.parentGroupId())) {
            requireGroupDestinationManage(context, body.parentGroupId());
        }
        return organization.updateGroup(groupId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @PostMapping("/tenants/{tenantId}/groups/{groupId}/status")
    public GroupResponse changeGroupStatus(
            @PathVariable String tenantId,
            @PathVariable String groupId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ChangeGroupStatusRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireGroup(context, IamPermissions.GROUP_MANAGE, groupId);
        return organization.changeGroupStatus(groupId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @GetMapping("/tenants/{tenantId}/groups/{groupId}/members")
    public CursorPage<MembershipResponse> groupMembers(
            @PathVariable String tenantId,
            @PathVariable String groupId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireGroup(context, IamPermissions.GROUP_READ, groupId);
        return projections.groupMembers(tenantId, groupId, pagination.limit(limit), cursor);
    }

    @PostMapping("/tenants/{tenantId}/groups/{groupId}/members")
    public ResponseEntity<MembershipResponse> addGroupMember(
            @PathVariable String tenantId,
            @PathVariable String groupId,
            @Valid @RequestBody AddGroupMemberRequest body,
            HttpServletRequest request) {
        return addGroupMembership(tenantId, body.userId(), body.toMembershipRequest(groupId), request);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/group-memberships")
    public ResponseEntity<MembershipResponse> addGroupMembership(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody AddGroupMembershipRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireGroup(context, IamPermissions.MEMBERSHIP_MANAGE, "USER", body.groupId());
        return ResponseEntity.status(201).body(organization.addGroupMembership(userId, body, context));
    }

    @PutMapping("/tenants/{tenantId}/group-memberships/{membershipId}")
    public MembershipResponse updateGroupMembership(
            @PathVariable String tenantId,
            @PathVariable String membershipId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateGroupMembershipRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        requireMembershipScope(context, tenantId, membershipId, "GROUP");
        return organization.updateGroupMembership(membershipId, body, context.requireExpectedVersion(ifMatch), false, context);
    }

    @DeleteMapping("/tenants/{tenantId}/group-memberships/{membershipId}")
    public MembershipResponse removeGroupMembership(
            @PathVariable String tenantId,
            @PathVariable String membershipId,
            @RequestHeader("If-Match") String ifMatch,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        requireMembershipScope(context, tenantId, membershipId, "GROUP");
        UpdateGroupMembershipRequest command = new UpdateGroupMembershipRequest(
                GroupMembershipRole.MEMBER, null, context.requireAuditReason());
        return organization.updateGroupMembership(membershipId, command, context.requireExpectedVersion(ifMatch), true, context);
    }

    // ---------- Roles, permissions, binding and Effective Access ----------

    @GetMapping("/tenants/{tenantId}/roles")
    public OffsetPage<RoleResponse> roles(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String status,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_ROLE_READ, "ROLE", "");
        return projections.roles(tenantId, pagination.page(page), pagination.size(size), text, type, status);
    }

    @PostMapping("/tenants/{tenantId}/roles")
    public ResponseEntity<RoleResponse> createRole(
            @PathVariable String tenantId,
            @Valid @RequestBody CreateRoleRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.TENANT_ROLE_MANAGE, "ROLE", body.authorizationTarget());
        return ResponseEntity.status(201).body(rbac.createTenant(body, context));
    }

    @PutMapping("/tenants/{tenantId}/roles/{roleId}")
    public RoleResponse updateRole(
            @PathVariable String tenantId,
            @PathVariable String roleId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateRoleRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.TENANT_ROLE_MANAGE, "ROLE", roleId);
        return rbac.updateTenant(roleId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @PostMapping("/tenants/{tenantId}/roles/{roleId}/status")
    public RoleResponse changeRoleStatus(
            @PathVariable String tenantId,
            @PathVariable String roleId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ChangeRoleStatusRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.TENANT_ROLE_MANAGE, "ROLE", roleId);
        return rbac.changeTenantStatus(roleId, body, context.requireExpectedVersion(ifMatch), context);
    }

    @GetMapping("/tenants/{tenantId}/roles/{roleId}/permissions")
    public RolePermissionMatrixResponse rolePermissions(
            @PathVariable String tenantId,
            @PathVariable String roleId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_ROLE_READ, "ROLE", roleId);
        return new RolePermissionMatrixResponse(roleId, projections.rolePermissions(tenantId, roleId));
    }

    @GetMapping("/tenants/{tenantId}/roles/{roleId}/ui-access-preview")
    public ResponsibilityUiAccessPreviewResponse responsibilityUiAccessPreview(
            @PathVariable String tenantId,
            @PathVariable String roleId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_ROLE_READ, "ROLE", roleId);
        var matrix = projections.rolePermissions(tenantId, roleId);
        Set<String> codes = matrix.stream().map(PermissionResponse::permissionCode)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        Set<String> scopes = matrix.stream().flatMap(permission -> permission.allowedScopeTypes().stream())
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        return new ResponsibilityUiAccessPreviewResponse(
                tenantId, roleId, java.time.Instant.now(), codes, scopes,
                uiFeatures.previewTenant(tenantId, codes, java.util.Map.of()));
    }

    @PostMapping("/tenants/{tenantId}/roles/{roleId}/ui-access-preview")
    public ResponsibilityUiAccessPreviewResponse previewResponsibilityUiAccessDraft(
            @PathVariable String tenantId,
            @PathVariable String roleId,
            @Valid @RequestBody PreviewResponsibilityUiAccessRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_ROLE_READ, "ROLE", roleId);
        Set<String> codes = new java.util.TreeSet<>(body.permissionCodes());
        return new ResponsibilityUiAccessPreviewResponse(
                tenantId, roleId, java.time.Instant.now(), codes, Set.of(),
                uiFeatures.previewTenant(tenantId, codes, java.util.Map.of()));
    }

    @PutMapping("/tenants/{tenantId}/roles/{roleId}/permissions")
    public ResponseEntity<Void> replaceRolePermissions(
            @PathVariable String tenantId,
            @PathVariable String roleId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ReplaceRolePermissionsRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.ROLE_PERMISSION_MANAGE, "ROLE", roleId);
        rbac.replaceTenantPermissions(roleId, body, context.requireExpectedVersion(ifMatch), context);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants/{tenantId}/permissions")
    public OffsetPage<PermissionResponse> permissions(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "") String scopeType,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.TENANT_ROLE_READ, "PERMISSION", "");
        return projections.permissions(pagination.page(page), pagination.size(size), text, scopeType);
    }

    @GetMapping("/tenants/{tenantId}/responsibility-templates")
    public OffsetPage<ResponsibilityTemplateResponse> responsibilityTemplates(
            @PathVariable String tenantId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(defaultValue="") String text,@RequestParam(defaultValue="ACTIVE") String status,
            @RequestParam(defaultValue="") String riskLevel,@RequestParam(defaultValue="") String scopeType,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.TENANT_ROLE_READ,"ROLE","");
        return projections.responsibilityTemplates(tenantId,pagination.page(page),pagination.size(size),text,status,riskLevel,scopeType);
    }

    @GetMapping("/tenants/{tenantId}/access-assignments")
    public CursorPage<AccessAssignmentResponse> accessAssignments(
            @PathVariable String tenantId,@RequestParam(defaultValue="") String text,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="") String principalType,@RequestParam(defaultValue="") String scopeType,
            @RequestParam(defaultValue="") String lifecycle,@RequestParam(defaultValue="20") int limit,
            @RequestParam(required=false) String cursor,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.ROLE_BINDING_READ,"ROLE_BINDING","");
        return projections.accessAssignments(tenantId,text,status,principalType,scopeType,lifecycle,pagination.limit(limit),cursor);
    }

    @GetMapping("/tenants/{tenantId}/access-lifecycle-summary")
    public AccessLifecycleSummaryResponse accessLifecycleSummary(@PathVariable String tenantId,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.ROLE_BINDING_READ,"ROLE_BINDING","");
        return projections.accessLifecycleSummary(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/access-review-candidates")
    public CursorPage<AccessReviewCandidateResponse> accessReviewCandidates(
            @PathVariable String tenantId,@RequestParam(defaultValue="") String reason,@RequestParam(defaultValue="") String riskLevel,
            @RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.ROLE_BINDING_READ,"ROLE_BINDING","");
        return projections.accessReviewCandidates(tenantId,reason,riskLevel,pagination.limit(limit),cursor);
    }

    @GetMapping("/tenants/{tenantId}/security-summary")
    public SecurityWorkspaceSummaryResponse securityWorkspaceSummary(@PathVariable String tenantId,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.AUDIT_READ,"SECURITY_SUMMARY",tenantId);
        return projections.securityWorkspaceSummary(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/credential-governance-catalog")
    public CredentialGovernanceCatalogResponse credentialGovernanceCatalog(@PathVariable String tenantId,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.TOKEN_READ,"CREDENTIAL_CATALOG",tenantId);
        return projections.credentialGovernanceCatalog(tenantId);
    }

    @GetMapping("/tenants/{tenantId}/audit-feed")
    public CursorPage<HumanReadableAuditResponse> humanReadableAudit(
            @PathVariable String tenantId,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String outcome,
            @RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.AUDIT_READ,"IDENTITY_AUDIT",tenantId);
        return projections.humanReadableAudit(tenantId,category,outcome,pagination.limit(limit),cursor);
    }

    @GetMapping("/tenants/{tenantId}/security-policy-revisions")
    public CursorPage<SecurityPolicyRevisionResponse> securityPolicyRevisions(
            @PathVariable String tenantId,@RequestParam(defaultValue="") String policyKind,@RequestParam(defaultValue="20") int limit,
            @RequestParam(required=false) String cursor,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.POLICY_READ,"SECURITY_POLICY",tenantId);
        return projections.securityPolicyRevisions(tenantId,policyKind,pagination.limit(limit),cursor);
    }

    @GetMapping("/tenants/{tenantId}/role-bindings")
    public CursorPage<RoleBindingResponse> roleBindings(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "") String roleId,
            @RequestParam(defaultValue = "") String principalId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.ROLE_BINDING_READ, "ROLE_BINDING", "");
        return projections.roleBindings(tenantId, roleId, principalId, pagination.limit(limit), cursor);
    }

    @PostMapping("/tenants/{tenantId}/roles/{roleId}/permissions/hardening-preview")
    public RbacHardeningPreviewResponse previewRolePermissionHardening(
            @PathVariable String tenantId,@PathVariable String roleId,
            @Valid @RequestBody ReplaceRolePermissionsRequest body,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.ROLE_BINDING_READ,"ROLE",roleId);
        return hardening.previewPermissionReplacement(tenantId,roleId,body,context);
    }

    @PostMapping("/tenants/{tenantId}/roles/{roleId}/permissions/approval-requests")
    public ResponseEntity<RbacCriticalApprovalResponse> requestRolePermissionApproval(
            @PathVariable String tenantId,@PathVariable String roleId,
            @Valid @RequestBody ReplaceRolePermissionsRequest body,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);requireSensitiveWrite(context);
        guard.requireTenant(context,IamPermissions.ROLE_APPROVAL_REQUEST,"ROLE",roleId);
        return ResponseEntity.status(201).body(hardening.requestPermissionApproval(tenantId,roleId,body,context));
    }

    @PostMapping("/tenants/{tenantId}/role-bindings/hardening-preview")
    public RbacHardeningPreviewResponse previewRoleBindingHardening(
            @PathVariable String tenantId,@Valid @RequestBody BindRoleRequest body,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.ROLE_BINDING_READ,"ROLE_BINDING",body.authorizationTarget());
        return hardening.previewBinding(tenantId,body,context);
    }

    @PostMapping("/tenants/{tenantId}/role-bindings/approval-requests")
    public ResponseEntity<RbacCriticalApprovalResponse> requestRoleBindingApproval(
            @PathVariable String tenantId,@Valid @RequestBody BindRoleRequest body,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);requireSensitiveWrite(context);
        guard.requireTenant(context,IamPermissions.ROLE_APPROVAL_REQUEST,"ROLE_BINDING",body.authorizationTarget());
        return ResponseEntity.status(201).body(hardening.requestBindingApproval(tenantId,body,context));
    }

    @GetMapping("/tenants/{tenantId}/rbac-approvals")
    public java.util.List<RbacCriticalApprovalResponse> rbacApprovals(@PathVariable String tenantId,
            @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="50") int limit,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.ROLE_APPROVAL_READ,"RBAC_APPROVAL","");
        return hardening.approvals(tenantId,status,limit);
    }

    @GetMapping("/tenants/{tenantId}/rbac-approvals/{approvalId}")
    public RbacCriticalApprovalResponse rbacApproval(@PathVariable String tenantId,@PathVariable String approvalId,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);
        guard.requireTenant(context,IamPermissions.ROLE_APPROVAL_READ,"RBAC_APPROVAL",approvalId);
        return hardening.approval(tenantId,approvalId);
    }

    @PostMapping("/tenants/{tenantId}/rbac-approvals/{approvalId}/approve")
    public RbacCriticalApprovalResponse approveRbacChange(@PathVariable String tenantId,@PathVariable String approvalId,
            @Valid @RequestBody RbacApprovalDecisionRequest body,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);requireSensitiveWrite(context);
        guard.requireTenant(context,IamPermissions.ROLE_APPROVAL_APPROVE,"RBAC_APPROVAL",approvalId);
        return hardening.approve(tenantId,approvalId,body.reason(),context);
    }

    @PostMapping("/tenants/{tenantId}/rbac-approvals/{approvalId}/reject")
    public RbacCriticalApprovalResponse rejectRbacChange(@PathVariable String tenantId,@PathVariable String approvalId,
            @Valid @RequestBody RbacApprovalDecisionRequest body,HttpServletRequest request) {
        IamApiRequestContext context=tenantContext(request,tenantId);requireSensitiveWrite(context);
        guard.requireTenant(context,IamPermissions.ROLE_APPROVAL_APPROVE,"RBAC_APPROVAL",approvalId);
        return hardening.reject(tenantId,approvalId,body.reason(),context);
    }

    @PostMapping("/tenants/{tenantId}/role-bindings/preview")
    public RoleBindingAssignmentPreviewResponse previewRoleBinding(
            @PathVariable String tenantId,
            @Valid @RequestBody PreviewRoleBindingRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.ROLE_BINDING_READ, "ROLE_BINDING", body.principalId());
        return effectiveAccess.previewAssignment(tenantId, body);
    }

    @PostMapping("/tenants/{tenantId}/role-bindings")
    public ResponseEntity<RoleBindingResponse> bindRole(
            @PathVariable String tenantId,
            @Valid @RequestBody BindRoleRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.ROLE_BINDING_MANAGE, "ROLE_BINDING", body.authorizationTarget());
        return ResponseEntity.status(201).body(rbac.bindTenant(body, context));
    }

    @GetMapping("/tenants/{tenantId}/role-bindings/{bindingId}/revocation-preview")
    public RoleBindingRevocationPreviewResponse previewRoleBindingRevocation(
            @PathVariable String tenantId,
            @PathVariable String bindingId,
            @RequestParam String userId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.ROLE_BINDING_READ, "ROLE_BINDING", bindingId);
        return effectiveAccess.previewRevocation(tenantId, userId, bindingId);
    }

    @PostMapping("/tenants/{tenantId}/role-bindings/{bindingId}/revoke")
    public ResponseEntity<Void> revokeRoleBinding(
            @PathVariable String tenantId,
            @PathVariable String bindingId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody RevokeBindingRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.ROLE_BINDING_MANAGE, "ROLE_BINDING", bindingId);
        rbac.revokeTenantBinding(bindingId, body, context.requireExpectedVersion(ifMatch), context);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants/{tenantId}/users/{userId}/ui-access")
    public EffectiveUiAccessResponse effectiveUiAccess(
            @PathVariable String tenantId,
            @PathVariable String userId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.ROLE_BINDING_READ, "USER", userId);
        return uiFeatures.explainEffectiveAccess(tenantId, effectiveAccess.effectiveAuthority(tenantId, userId));
    }

    @GetMapping("/tenants/{tenantId}/users/{userId}/effective-access")
    public EffectiveAccessResponse effectiveAccess(
            @PathVariable String tenantId,
            @PathVariable String userId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.ROLE_BINDING_READ, "USER", userId);
        return effectiveAccess.effectiveAccess(tenantId, userId);
    }

    // ---------- Security and audit ----------

    @GetMapping("/tenants/{tenantId}/sessions")
    public CursorPage<SessionResponse> sessions(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "") String subjectId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.SESSION_READ, "SESSION", "");
        return projections.sessions(tenantId, subjectId, pagination.limit(limit), cursor);
    }

    @PostMapping("/tenants/{tenantId}/sessions/{sessionId}/revoke")
    public ResponseEntity<Void> revokeSession(
            @PathVariable String tenantId,
            @PathVariable String sessionId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody RevokeSessionRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.SESSION_REVOKE, "SESSION", sessionId);
        sessions.revoke(sessionId, context.requireExpectedVersion(ifMatch), body.reason(), context);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/revoke-sessions")
    public ResponseEntity<Void> revokeUserSessions(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody RevokeSessionRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.SESSION_REVOKE, "USER", userId);
        sessions.revokeAllForUser(userId, body.reason(), context);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody AdministrativeResetRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.USER_UPDATE, "USER", userId);
        credentials.initiatePasswordReset(userId, body.reason(), context);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/temporary-password")
    public ResponseEntity<Void> setTemporaryPassword(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody SetTemporaryPasswordRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.USER_UPDATE, "USER", userId);
        credentials.setTemporaryPassword(userId, body.temporaryPassword(), body.reason(), context);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/setup-credential")
    public ResponseEntity<CredentialSetupResponse> setupCredential(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody CredentialSetupRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.USER_UPDATE, "USER", userId);
        CredentialSetupResponse response = credentials.initiatePasswordSetup(
                userId, body.deliveryMethod(), body.reason(), context);
        return ResponseEntity.accepted().header("Cache-Control", "no-store").body(response);
    }

    @PostMapping("/tenants/{tenantId}/users/{userId}/reset-mfa")
    public ResponseEntity<Void> resetMfa(
            @PathVariable String tenantId,
            @PathVariable String userId,
            @Valid @RequestBody AdministrativeResetRequest body,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        requireSensitiveWrite(context);
        guard.requireTenant(context, IamPermissions.MFA_RESET, "USER", userId);
        credentials.resetMfa(userId, body.reason(), context);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tenants/{tenantId}/audit")
    public CursorPage<IdentityAuditResponse> audit(
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "") String eventType,
            @RequestParam(defaultValue = "") String actorId,
            @RequestParam(defaultValue = "") String targetId,
            HttpServletRequest request) {
        IamApiRequestContext context = tenantContext(request, tenantId);
        guard.requireTenant(context, IamPermissions.AUDIT_READ, "IDENTITY_AUDIT", "");
        return projections.identityAudit(tenantId, pagination.limit(limit), cursor, eventType, actorId, targetId);
    }

    private IamApiRequestContext tenantContext(HttpServletRequest request, String tenantId) {
        IamApiRequestContext context = contexts.from(request);
        if (!tenantId.equals(context.activeTenantId())) throw new IllegalArgumentException("AUTH_SCOPE_MISMATCH");
        return context;
    }

    private UserResponse requirePlatformUser(String userId) {
        return projections.platformUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("IDENTITY_PLATFORM_USER_NOT_FOUND"));
    }

    private UserResponse requireTenantUser(String tenantId, String userId) {
        return projections.user(tenantId, userId)
                .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
    }

    private void requireSensitiveWrite(IamApiRequestContext context) {
        sensitiveWrites.require(context);
    }

    private static boolean isInstanceRoot(IamApiRequestContext context) {
        return context.requireAuthentication().principal().principalType() == PrincipalRef.PrincipalType.INSTANCE_ROOT;
    }


    private Set<String> effectiveDepartmentScopeIds(String tenantId, Set<String> scopes) {
        Set<String> direct = scopeIds(scopes, "DEPARTMENT");
        Set<String> subtreeRoots = scopeIds(scopes, "DEPARTMENT_SUBTREE");
        if (subtreeRoots.isEmpty()) return direct;
        Set<String> effective = new java.util.LinkedHashSet<>(direct);
        effective.addAll(projections.departmentIdsWithinSubtrees(tenantId, subtreeRoots));
        return Set.copyOf(effective);
    }

    private static Set<String> scopeIds(Set<String> scopes, String scopeType) {
        String prefix = scopeType + ":";
        return scopes.stream()
                .filter(scope -> scope.startsWith(prefix) && scope.length() > prefix.length())
                .map(scope -> scope.substring(prefix.length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void requireUserReadScope(IamApiRequestContext context, String tenantId, String userId) {
        if (isInstanceRoot(context)) {
            guard.requireTenant(context, IamPermissions.USER_READ, "USER", userId);
            return;
        }
        IamEffectiveAuthorityResponse authority = effectiveAccess.effectiveAuthority(tenantId, context.actorId());
        Set<String> scopes = authority.permissionScopes().getOrDefault(IamPermissions.USER_READ, Set.of());
        if (scopes.contains("TENANT:" + tenantId)) {
            guard.requireTenant(context, IamPermissions.USER_READ, "USER", userId);
            return;
        }
        UserOrganizationScopeResponse target = projections.userOrganizationScope(tenantId, userId);
        Set<String> allowedDepartments = effectiveDepartmentScopeIds(tenantId, scopes);
        for (String departmentId : new java.util.TreeSet<>(target.departmentIds())) {
            if (allowedDepartments.contains(departmentId)) {
                guard.requireDepartment(context, IamPermissions.USER_READ, "USER", departmentId);
                return;
            }
        }
        Set<String> allowedGroups = scopeIds(scopes, "GROUP");
        for (String groupId : new java.util.TreeSet<>(target.groupIds())) {
            if (allowedGroups.contains(groupId)) {
                guard.requireGroup(context, IamPermissions.USER_READ, "USER", groupId);
                return;
            }
        }
        // Force the canonical engine to produce the final deny reason/evidence.
        guard.requireTenant(context, IamPermissions.USER_READ, "USER", userId);
    }

    private void requireDepartmentDestinationManage(IamApiRequestContext context, String parentDepartmentId) {
        if (parentDepartmentId == null || parentDepartmentId.isBlank()) {
            guard.requireTenant(context, IamPermissions.DEPARTMENT_MANAGE, "DEPARTMENT", context.activeTenantId());
            return;
        }
        guard.requireDepartment(context, IamPermissions.DEPARTMENT_MANAGE, parentDepartmentId);
    }

    private void requireGroupDestinationManage(IamApiRequestContext context, String parentGroupId) {
        if (parentGroupId == null || parentGroupId.isBlank()) {
            guard.requireTenant(context, IamPermissions.GROUP_MANAGE, "GROUP", context.activeTenantId());
            return;
        }
        guard.requireGroup(context, IamPermissions.GROUP_MANAGE, parentGroupId);
    }

    private void requireMembershipScope(
            IamApiRequestContext context, String tenantId, String membershipId, String expectedType) {
        MembershipResponse membership = projections.membership(tenantId, membershipId)
                .orElseThrow(() -> IamApiException.notFound(
                        "MEMBERSHIP_NOT_FOUND", "Membership was not found in the active Tenant"));
        if (!expectedType.equals(membership.membershipType())) {
            throw IamApiException.badRequest(
                    "MEMBERSHIP_TYPE_MISMATCH", "Membership type does not match this operation");
        }
        if ("DEPARTMENT".equals(expectedType)) {
            guard.requireDepartment(context, IamPermissions.MEMBERSHIP_MANAGE, "MEMBERSHIP", membership.resourceId());
            return;
        }
        if ("GROUP".equals(expectedType)) {
            guard.requireGroup(context, IamPermissions.MEMBERSHIP_MANAGE, "MEMBERSHIP", membership.resourceId());
            return;
        }
        guard.requireTenant(context, IamPermissions.MEMBERSHIP_MANAGE, "MEMBERSHIP", membershipId);
    }

    private static boolean sameOptionalId(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    private static Long optionalLong(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }
}
