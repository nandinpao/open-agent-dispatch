package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.api.application.port.IamActivationDeliveryPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserLifecycleApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.UserOnboardingRequest;
import com.opensocket.aievent.core.iam.api.response.MembershipResponse;
import com.opensocket.aievent.core.iam.api.response.RoleBindingResponse;
import com.opensocket.aievent.core.iam.api.response.UserOnboardingResponse;
import com.opensocket.aievent.core.iam.api.response.UserResponse;
import com.opensocket.aievent.core.iam.authentication.application.command.SetPasswordCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.PasswordAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.identity.application.command.CreateHumanUserCommand;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserByEmailQuery;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserByUsernameQuery;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserQuery;
import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import com.opensocket.aievent.core.iam.identity.domain.IdentityDomainException;
import com.opensocket.aievent.core.iam.identity.domain.EmailAddress;
import com.opensocket.aievent.core.iam.identity.domain.HumanUser;
import com.opensocket.aievent.core.iam.identity.domain.IdentityReasonCode;
import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import com.opensocket.aievent.core.iam.organization.application.command.AddDepartmentMembershipCommand;
import com.opensocket.aievent.core.iam.organization.application.command.AddGroupMembershipCommand;
import com.opensocket.aievent.core.iam.organization.application.command.AddTenantMembershipCommand;
import com.opensocket.aievent.core.iam.organization.application.port.in.DepartmentCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.GroupCommandPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.organization.domain.DepartmentMembership;
import com.opensocket.aievent.core.iam.organization.domain.GroupMembership;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembership;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.rbac.application.command.BindRoleCommand;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.token.application.command.IssueOneTimeTokenCommand;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Composition-root orchestration for Tenant user onboarding.
 *
 * <p>{@link IamIdempotencyExecutor} delegates to the transactional IAM idempotency adapter, so
 * identity, organization membership, Role Binding, invitation token and outbox writes share one
 * PostgreSQL transaction. Delivery receives only the one-time secret and never persists it.</p>
 */
public final class IamUserLifecycleRuntimeOrchestrator implements IamUserLifecycleApiPort {
    private final IdentityCommandPort identities;
    private final IdentityQueryPort identityQueries;
    private final TenantCommandPort tenants;
    private final DepartmentCommandPort departments;
    private final GroupCommandPort groups;
    private final RbacAdministrationPort rbac;
    private final PasswordAuthenticationCommandPort passwords;
    private final AccessTokenCommandPort tokens;
    private final IamActivationDeliveryPort delivery;
    private final IamIdempotencyExecutor idempotency;

    public IamUserLifecycleRuntimeOrchestrator(
            IdentityCommandPort identities,
            IdentityQueryPort identityQueries,
            TenantCommandPort tenants,
            DepartmentCommandPort departments,
            GroupCommandPort groups,
            RbacAdministrationPort rbac,
            PasswordAuthenticationCommandPort passwords,
            AccessTokenCommandPort tokens,
            IamActivationDeliveryPort delivery,
            IamIdempotencyExecutor idempotency) {
        this.identities = identities;
        this.identityQueries = identityQueries;
        this.tenants = tenants;
        this.departments = departments;
        this.groups = groups;
        this.rbac = rbac;
        this.passwords = passwords;
        this.tokens = tokens;
        this.delivery = delivery;
        this.idempotency = idempotency;
    }

    @Override
    public UserOnboardingResponse onboard(UserOnboardingRequest request, IamApiRequestContext context) {
        String tenantId = context.activeTenantId();
        String key = context.requireIdempotencyKey();
        Optional<HumanUser> resolvedExistingIdentity = resolveExistingIdentity(request);
        boolean existingIdentity = resolvedExistingIdentity.isPresent();
        String userId = resolvedExistingIdentity
                .map(user -> user.userId().value())
                .orElseGet(() -> IamGeneratedIdentityIds.resolve(
                        request.userId(), "TENANT:" + tenantId + ":USER_ONBOARD", key));
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.user.onboard",
                key,
                request.idempotencyMaterial(),
                201,
                UserOnboardingResponse.class,
                () -> IamTenantContextHolder.withContext(
                        new IamTenantExecutionContext(tenantId, context.actorId()),
                        () -> execute(request, context, tenantId, key, userId, existingIdentity)));
    }

    private UserOnboardingResponse execute(
            UserOnboardingRequest request,
            IamApiRequestContext context,
            String tenantId,
            String key,
            String userId,
            boolean existingIdentity) {
        var user = existingIdentity
                ? identityQueries.findHumanUser(new FindHumanUserQuery(userId))
                        .orElseThrow(() -> new IllegalStateException("Existing identity disappeared during onboarding"))
                : identities.createHumanUser(new CreateHumanUserCommand(
                userId,
                request.username(),
                request.email(),
                request.displayName(),
                request.creationMode(),
                context.actorId(),
                context.correlationId(),
                event("USER_ONBOARDED", key)));

        if (existingIdentity && !isAdmittableExistingIdentity(user.status())) {
            throw new IdentityDomainException(IdentityReasonCode.EXISTING_USER_NOT_ADMITTABLE,
                    "The existing identity is " + user.status()
                            + " and cannot be admitted to a Tenant workspace without an explicit identity lifecycle action");
        }

        boolean temporaryPasswordConfigured = request.initialPassword() != null && !request.initialPassword().isBlank();
        if (existingIdentity && temporaryPasswordConfigured) {
            throw new IdentityDomainException(IdentityReasonCode.IDENTITY_ATTRIBUTES_CONFLICT,
                    "Existing sign-in credentials are identity-wide and must be preserved during Tenant re-admission");
        }
        if (!existingIdentity && temporaryPasswordConfigured) {
            passwords.setPassword(new SetPasswordCommand(
                    CredentialSubjectType.HUMAN_USER, userId, user.username().value(), tenantId,
                    request.initialPassword().toCharArray(), true, context.actorId(), context.correlationId(),
                    context.requestedAt(), 0L));
        }

        boolean effectiveDefaultTenant = (!existingIdentity
                && request.membershipStatus() == com.opensocket.aievent.core.iam.organization.domain.MembershipStatus.ACTIVE)
                || request.defaultTenant();
        TenantMembership tenantMembership = tenants.addTenantMembership(new AddTenantMembershipCommand(
                request.membershipId(),
                tenantId,
                userId,
                request.employeeId(),
                request.membershipExpiresAt(),
                request.membershipStatus(),
                effectiveDefaultTenant,
                request.membershipSource(),
                request.reason(),
                context.actorId(),
                context.correlationId(),
                event("TENANT_MEMBERSHIP_ONBOARDED", key)));

        List<MembershipResponse> departmentResponses = new ArrayList<>();
        List<UserOnboardingRequest.DepartmentAssignment> orderedDepartments = new ArrayList<>(request.departments());
        orderedDepartments.sort(Comparator.comparing(UserOnboardingRequest.DepartmentAssignment::primary).reversed());
        for (UserOnboardingRequest.DepartmentAssignment assignment : orderedDepartments) {
            DepartmentMembership membership = departments.addDepartmentMembership(new AddDepartmentMembershipCommand(
                    assignment.membershipId(),
                    tenantId,
                    userId,
                    assignment.departmentId(),
                    assignment.membershipType(),
                    assignment.primary(),
                    assignment.expiresAt(),
                    context.actorId(),
                    context.correlationId(),
                    event("DEPARTMENT_MEMBERSHIP_ONBOARDED:" + assignment.membershipId(), key)));
            departmentResponses.add(departmentResponse(membership));
        }

        List<MembershipResponse> groupResponses = new ArrayList<>();
        for (UserOnboardingRequest.GroupAssignment assignment : request.groups()) {
            GroupMembership membership = groups.addGroupMembership(new AddGroupMembershipCommand(
                    assignment.membershipId(),
                    tenantId,
                    userId,
                    assignment.groupId(),
                    assignment.membershipRole(),
                    assignment.expiresAt(),
                    context.actorId(),
                    context.correlationId(),
                    event("GROUP_MEMBERSHIP_ONBOARDED:" + assignment.membershipId(), key)));
            groupResponses.add(groupResponse(membership));
        }

        List<RoleBindingResponse> roleResponses = new ArrayList<>();
        for (UserOnboardingRequest.RoleAssignment assignment : request.roles()) {
            validateRoleScope(assignment, tenantId);
            var binding = rbac.bindRole(new BindRoleCommand(
                    assignment.bindingId(),
                    tenantId,
                    new PrincipalRef(PrincipalRef.PrincipalType.USER, userId),
                    assignment.roleId(),
                    assignment.scopeType(),
                    assignment.scopeId(),
                    assignment.effectiveAt() == null ? context.requestedAt() : assignment.effectiveAt(),
                    assignment.expiresAt(),
                    context.actorId(),
                    request.reason(),
                    context.requestedAt()));
            roleResponses.add(RoleBindingResponse.from(binding));
        }

        boolean invitationIssued = (!existingIdentity && request.creationMode() == UserCreationMode.INVITATION)
                || (existingIdentity && user.status() == AccountStatus.PENDING_ACTIVATION);
        boolean passwordSetupRequired = (!existingIdentity && request.creationMode() == UserCreationMode.ADMIN_CREATED
                && !temporaryPasswordConfigured)
                || (existingIdentity && user.status() == AccountStatus.PASSWORD_RESET_REQUIRED);
        boolean setupIssued = false;
        String deliveryReference = user.email().map(EmailAddress::value).orElse(user.username().value());
        IamActivationDeliveryPort.DeliveryReceipt receipt = null;
        if (invitationIssued) {
            var issued = tokens.issueOneTime(new IssueOneTimeTokenCommand(
                    tenantId, userId, AccessTokenType.INVITATION_TOKEN, Duration.ofHours(24),
                    context.actorId(), context.correlationId()));
            receipt = delivery.deliver(new IamActivationDeliveryPort.DeliveryCommand(
                    tenantId, userId, issued.tokenId(), "USER_INVITATION", request.activationDeliveryMethod(),
                    deliveryReference, issued.token(), issued.expiresAt(), context.actorId(), context.correlationId()));
            setupIssued = true;
        } else if (passwordSetupRequired) {
            var issued = tokens.issueOneTime(new IssueOneTimeTokenCommand(
                    tenantId, userId, AccessTokenType.PASSWORD_RESET_TOKEN, Duration.ofHours(24),
                    context.actorId(), context.correlationId()));
            receipt = delivery.deliver(new IamActivationDeliveryPort.DeliveryCommand(
                    tenantId, userId, issued.tokenId(), "ACCOUNT_SETUP", request.activationDeliveryMethod(),
                    deliveryReference, issued.token(), issued.expiresAt(), context.actorId(), context.correlationId()));
            setupIssued = true;
        }

        List<String> requiredActions = requiredActions(
                user.status(), existingIdentity, invitationIssued, temporaryPasswordConfigured, request.applicationAccessDeferred());
        return new UserOnboardingResponse(
                UserResponse.from(user), tenantResponse(tenantMembership), departmentResponses, groupResponses,
                roleResponses, invitationIssued, setupIssued, temporaryPasswordConfigured, request.authenticationMethod(),
                receipt == null ? "NOT_REQUIRED" : receipt.deliveryMethod(),
                receipt == null ? "NOT_ISSUED" : receipt.deliveryStatus(),
                receipt == null ? deliveryReference : receipt.recipientReference(),
                receipt == null ? "" : receipt.deliveryId(),
                receipt == null ? null : receipt.expiresAt(),
                receipt == null ? "" : receipt.failureCode(),
                receipt == null ? "" : receipt.setupActionUrl(),
                requiredActions);
    }

    private Optional<HumanUser> resolveExistingIdentity(UserOnboardingRequest request) {
        String requestedUserId = request.userId() == null ? "" : request.userId().trim();
        if (!requestedUserId.isBlank()) {
            return identityQueries.findHumanUser(new FindHumanUserQuery(requestedUserId));
        }

        Optional<HumanUser> usernameMatch = exactUsernameMatch(request.username());
        Optional<HumanUser> emailMatch = exactEmailMatch(request.email());
        if (usernameMatch.isPresent() && emailMatch.isPresent()
                && !usernameMatch.orElseThrow().userId().equals(emailMatch.orElseThrow().userId())) {
            throw new IdentityDomainException(IdentityReasonCode.IDENTITY_ATTRIBUTES_CONFLICT,
                    "The requested sign-in name and email belong to different existing identities");
        }
        return usernameMatch.isPresent() ? usernameMatch : emailMatch;
    }

    private Optional<HumanUser> exactUsernameMatch(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        return identityQueries.findHumanUserByUsername(new FindHumanUserByUsernameQuery(username));
    }

    private Optional<HumanUser> exactEmailMatch(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return identityQueries.findHumanUserByEmail(new FindHumanUserByEmailQuery(email));
    }

    private static boolean isAdmittableExistingIdentity(AccountStatus status) {
        return Set.of(
                AccountStatus.ACTIVE,
                AccountStatus.PENDING_ACTIVATION,
                AccountStatus.PASSWORD_RESET_REQUIRED,
                AccountStatus.MFA_ENROLLMENT_REQUIRED).contains(status);
    }

    private static List<String> requiredActions(
            AccountStatus status,
            boolean existingIdentity,
            boolean invitationIssued,
            boolean temporaryPasswordConfigured,
            boolean applicationAccessDeferred) {
        List<String> actions = new ArrayList<>();
        if (!existingIdentity) {
            if (temporaryPasswordConfigured) {
                actions.add("CHANGE_PASSWORD");
                actions.add("ENROLL_MFA");
            } else if (invitationIssued) {
                actions.add("ACTIVATE_ACCOUNT");
                actions.add("SET_PASSWORD");
                actions.add("ENROLL_MFA");
            } else {
                actions.add("SET_PASSWORD");
                actions.add("ENROLL_MFA");
            }
        } else {
            switch (status) {
                case PENDING_ACTIVATION -> { actions.add("ACTIVATE_ACCOUNT"); actions.add("SET_PASSWORD"); actions.add("ENROLL_MFA"); }
                case PASSWORD_RESET_REQUIRED -> { actions.add("SET_PASSWORD"); actions.add("ENROLL_MFA"); }
                case MFA_ENROLLMENT_REQUIRED -> actions.add("ENROLL_MFA");
                case ACTIVE -> { }
                default -> throw new IdentityDomainException(IdentityReasonCode.EXISTING_USER_NOT_ADMITTABLE,
                        "The existing identity is not eligible for Tenant re-admission");
            }
        }
        if (applicationAccessDeferred) actions.add("ASSIGN_RESPONSIBILITY");
        return List.copyOf(actions);
    }

    private static MembershipResponse tenantResponse(TenantMembership membership) {
        return new MembershipResponse(
                membership.membershipId().value(),
                "TENANT",
                membership.tenantId().value(),
                membership.userPrincipal().principalId(),
                membership.tenantId().value(),
                membership.membershipSource().name(),
                membership.status().name(),
                membership.defaultTenant(),
                membership.joinedAt(),
                membership.expiresAt().orElse(null),
                membership.version(),
                membership.employeeId().orElse(null));
    }

    private static MembershipResponse departmentResponse(DepartmentMembership membership) {
        return new MembershipResponse(
                membership.membershipId().value(),
                "DEPARTMENT",
                membership.tenantId().value(),
                membership.userPrincipal().principalId(),
                membership.departmentId().value(),
                membership.membershipType().name(),
                membership.status().name(),
                membership.primary(),
                membership.effectiveAt(),
                membership.expiresAt().orElse(null),
                membership.version());
    }

    private static MembershipResponse groupResponse(GroupMembership membership) {
        return new MembershipResponse(
                membership.membershipId().value(),
                "GROUP",
                membership.tenantId().value(),
                membership.userPrincipal().principalId(),
                membership.groupId().value(),
                membership.membershipRole().name(),
                membership.status().name(),
                false,
                membership.effectiveAt(),
                membership.expiresAt().orElse(null),
                membership.version());
    }


    private static void validateRoleScope(UserOnboardingRequest.RoleAssignment assignment, String tenantId) {
        switch (assignment.scopeType()) {
            case "TENANT" -> {
                if (!tenantId.equals(assignment.scopeId())) {
                    throw new IllegalArgumentException("Tenant Role Binding scope must equal the active Tenant");
                }
            }
            case "DEPARTMENT", "GROUP" -> { }
            default -> throw new IllegalArgumentException(
                    "Onboarding supports TENANT, DEPARTMENT or GROUP Role Binding scopes only");
        }
    }

    private static String event(String prefix, String key) {
        return UUID.nameUUIDFromBytes((prefix + ":" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
