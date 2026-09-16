package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.api.application.port.IamUserAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamUserProvisioningApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.request.CreateUserRequest;
import com.opensocket.aievent.core.iam.api.request.UpdateUserRequest;
import com.opensocket.aievent.core.iam.api.response.UserResponse;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.identity.application.command.CreateHumanUserCommand;
import com.opensocket.aievent.core.iam.identity.application.command.UpdateHumanUserProfileCommand;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.organization.application.command.AddTenantMembershipCommand;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.token.application.command.IssueOneTimeTokenCommand;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Tenant-scoped provisioning and profile administration. Global account lifecycle is Platform-only. */
public final class IamUserRuntimeOrchestrator
        implements IamUserProvisioningApiPort, IamUserAdministrationApiPort {
    private final IdentityCommandPort identities;
    private final TenantCommandPort tenants;
    private final AccessTokenCommandPort tokens;
    private final IamOneTimeSecretDeliveryPort delivery;
    private final IamIdempotencyExecutor idempotency;

    public IamUserRuntimeOrchestrator(
            IdentityCommandPort identities,
            TenantCommandPort tenants,
            AccessTokenCommandPort tokens,
            IamOneTimeSecretDeliveryPort delivery,
            IamIdempotencyExecutor idempotency) {
        this.identities = identities;
        this.tenants = tenants;
        this.tokens = tokens;
        this.delivery = delivery;
        this.idempotency = idempotency;
    }

    @Override
    public UserResponse createUser(CreateUserRequest request, IamApiRequestContext context) {
        String tenantId = context.activeTenantId();
        String key = context.requireIdempotencyKey();
        String userId = IamGeneratedIdentityIds.resolve(request.userId(), "TENANT:" + tenantId + ":USER_CREATE", key);
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.user.create",
                key,
                request,
                201,
                UserResponse.class,
                () -> IamTenantContextHolder.withContext(
                        new IamTenantExecutionContext(tenantId, context.actorId()),
                        () -> {
                            var user = identities.createHumanUser(new CreateHumanUserCommand(
                                    userId,
                                    request.username(),
                                    request.email(),
                                    request.displayName(),
                                    request.creationMode(),
                                    context.actorId(),
                                    context.correlationId(),
                                    event("USER_CREATED", key)));
                            boolean invitation = request.creationMode() == UserCreationMode.INVITATION;
                            tenants.addTenantMembership(new AddTenantMembershipCommand(
                                    event("membership", key),
                                    tenantId,
                                    userId,
                                    "",
                                    null,
                                    invitation ? MembershipStatus.INVITED : MembershipStatus.ACTIVE,
                                    false,
                                    invitation ? TenantMembershipSource.INVITATION : TenantMembershipSource.ADMIN_CREATED,
                                    context.requireAuditReason(),
                                    context.actorId(),
                                    context.correlationId(),
                                    event("TENANT_MEMBERSHIP_CHANGED", key)));
                            String recipient = request.email() == null || request.email().isBlank()
                                    ? request.username()
                                    : request.email();
                            if (invitation) {
                                var issued = tokens.issueOneTime(new IssueOneTimeTokenCommand(
                                        tenantId,
                                        userId,
                                        AccessTokenType.INVITATION_TOKEN,
                                        Duration.ofHours(24),
                                        context.actorId(),
                                        context.correlationId()));
                                delivery.deliver(
                                        "USER_INVITATION", recipient, issued.token(), issued.expiresAt(),
                                        context.correlationId());
                            } else if (request.creationMode() == UserCreationMode.ADMIN_CREATED) {
                                var issued = tokens.issueOneTime(new IssueOneTimeTokenCommand(
                                        tenantId,
                                        userId,
                                        AccessTokenType.PASSWORD_RESET_TOKEN,
                                        Duration.ofHours(24),
                                        context.actorId(),
                                        context.correlationId()));
                                delivery.deliver(
                                        "ACCOUNT_SETUP", recipient, issued.token(), issued.expiresAt(),
                                        context.correlationId());
                            }
                            return UserResponse.from(user);
                        }));
    }

    @Override
    public UserResponse updateUser(
            String userId,
            UpdateUserRequest request,
            long expectedVersion,
            IamApiRequestContext context) {
        String tenantId = context.activeTenantId();
        String key = context.requireIdempotencyKey();
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.user.update",
                key,
                request,
                200,
                UserResponse.class,
                () -> IamTenantContextHolder.withContext(
                        new IamTenantExecutionContext(tenantId, context.actorId()),
                        () -> UserResponse.from(identities.updateHumanUserProfile(
                                new UpdateHumanUserProfileCommand(
                                        userId,
                                        request.displayName(),
                                        request.email(),
                                        expectedVersion,
                                        context.actorId(),
                                        context.correlationId())))));
    }

    private static String event(String prefix, String key) {
        return UUID.nameUUIDFromBytes((prefix + ":" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
