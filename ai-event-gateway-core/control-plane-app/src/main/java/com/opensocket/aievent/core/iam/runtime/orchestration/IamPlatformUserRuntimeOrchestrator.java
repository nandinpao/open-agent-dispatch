package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.api.application.port.IamPlatformUserAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.ChangeUserStatusRequest;
import com.opensocket.aievent.core.iam.api.request.CreateUserRequest;
import com.opensocket.aievent.core.iam.api.request.UpdateUserRequest;
import com.opensocket.aievent.core.iam.api.response.UserResponse;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordCredentialRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.MfaMethodRepository;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.identity.application.command.ChangeHumanUserStatusCommand;
import com.opensocket.aievent.core.iam.identity.application.command.CreateHumanUserCommand;
import com.opensocket.aievent.core.iam.identity.application.command.UpdateHumanUserProfileCommand;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import com.opensocket.aievent.core.iam.organization.application.port.out.DepartmentRepository;
import com.opensocket.aievent.core.iam.organization.domain.OrganizationDomainException;
import com.opensocket.aievent.core.iam.organization.domain.OrganizationReasonCode;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** INSTANCE-scope user administration. It never creates an implicit Tenant Membership. */
public final class IamPlatformUserRuntimeOrchestrator implements IamPlatformUserAdministrationApiPort {
    private final IdentityCommandPort identities;
    private final PasswordCredentialRepository credentials;
    private final BrowserSessionRepository sessions;
    private final MfaMethodRepository mfaMethods;
    private final IamIdempotencyExecutor idempotency;
    private final DepartmentRepository departments;
    private final Clock clock;

    public IamPlatformUserRuntimeOrchestrator(
            IdentityCommandPort identities,
            PasswordCredentialRepository credentials,
            BrowserSessionRepository sessions,
            MfaMethodRepository mfaMethods,
            IamIdempotencyExecutor idempotency,
            DepartmentRepository departments,
            Clock clock) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.mfaMethods = Objects.requireNonNull(mfaMethods, "mfaMethods");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.departments = Objects.requireNonNull(departments, "departments");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public UserResponse createUser(CreateUserRequest request, IamApiRequestContext context) {
        if (request.creationMode() == UserCreationMode.INVITATION) {
            throw new IllegalArgumentException("IDENTITY_PLATFORM_INVITATION_REQUIRES_TENANT");
        }
        String key = context.requireIdempotencyKey();
        String userId = IamGeneratedIdentityIds.resolve(request.userId(), "INSTANCE:PLATFORM_USER_CREATE", key);
        return idempotency.execute("INSTANCE", context.actorId(), "identity.platform_user.create", key,
                request, 201, UserResponse.class,
                () -> UserResponse.from(identities.createHumanUser(new CreateHumanUserCommand(
                        userId, request.username(), request.email(), request.displayName(),
                        request.creationMode(), context.actorId(), context.correlationId(),
                        event("PLATFORM_USER_CREATED", key)))));
    }

    @Override
    public UserResponse updateUser(
            String userId, UpdateUserRequest request, long expectedVersion, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        return idempotency.execute("INSTANCE", context.actorId(), "identity.platform_user.update", key,
                request, 200, UserResponse.class,
                () -> UserResponse.from(identities.updateHumanUserProfile(new UpdateHumanUserProfileCommand(
                        userId, request.displayName(), request.email(), expectedVersion,
                        context.actorId(), context.correlationId()))));
    }

    @Override
    public UserResponse changeUserStatus(
            String userId, ChangeUserStatusRequest request, long expectedVersion, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        if (request.status() != AccountStatus.ACTIVE) {
            var assignments = departments.findActiveManagedByUser(
                    new PrincipalRef(PrincipalRef.PrincipalType.USER, userId));
            if (!assignments.isEmpty()) {
                throw new OrganizationDomainException(
                        OrganizationReasonCode.OFFICIAL_MANAGER_ASSIGNMENT_BLOCKS_USER_STATUS,
                        "Reassign Official Department Manager responsibilities before disabling this user");
            }
        }
        return idempotency.execute("INSTANCE", context.actorId(), "identity.platform_user.status", key,
                request, 200, UserResponse.class, () -> {
                    var changed = identities.changeHumanUserStatus(
                            new ChangeHumanUserStatusCommand(userId, request.status(), expectedVersion,
                                    request.reason(), context.actorId(), context.correlationId(),
                                    event("PLATFORM_USER_STATUS_CHANGED", key)));
                    if (request.status() == AccountStatus.ACTIVE
                            && mfaMethods.findActive(CredentialSubjectType.HUMAN_USER, userId).isEmpty()) {
                        changed = identities.changeHumanUserStatus(new ChangeHumanUserStatusCommand(
                                userId, AccountStatus.MFA_ENROLLMENT_REQUIRED, changed.version(),
                                "Account reactivated; MFA enrollment is required before normal sign-in",
                                context.actorId(), context.correlationId(),
                                event("PLATFORM_USER_MFA_REENROLLMENT_REQUIRED", key)));
                    }
                    if (changed.status() != AccountStatus.ACTIVE) {
                        sessions.revokeAllTenants(CredentialSubjectType.HUMAN_USER, userId,
                                context.actorId(), request.reason(), clock.instant());
                    }
                    return UserResponse.from(changed);
                });
    }

    @Override
    public void requirePasswordChange(String userId, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        idempotency.execute("INSTANCE", context.actorId(), "identity.platform_user.require_password_change", key,
                userId, 200, Boolean.class, () -> {
                    var current = credentials.find(CredentialSubjectType.HUMAN_USER, userId)
                            .orElseThrow(() -> new IllegalArgumentException("AUTH_PASSWORD_CREDENTIAL_NOT_FOUND"));
                    if (!current.mustChange()) {
                        credentials.save(current.requireChange(), current.version());
                    }
                    sessions.revokeAllTenants(CredentialSubjectType.HUMAN_USER, userId,
                            context.actorId(), context.requireAuditReason(), clock.instant());
                    return Boolean.TRUE;
                });
    }

    @Override
    public void revokeAllSessions(String userId, IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        idempotency.execute("INSTANCE", context.actorId(), "identity.platform_user.revoke_sessions", key,
                userId, 200, Boolean.class, () -> {
                    sessions.revokeAllTenants(CredentialSubjectType.HUMAN_USER, userId,
                            context.actorId(), context.requireAuditReason(), clock.instant());
                    return Boolean.TRUE;
                });
    }

    private static String event(String prefix, String key) {
        return UUID.nameUUIDFromBytes((prefix + ":" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
