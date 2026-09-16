package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.api.application.port.IamCredentialAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamActivationDeliveryPort;
import com.opensocket.aievent.core.iam.api.application.port.IamSessionAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.response.CredentialSetupResponse;
import com.opensocket.aievent.core.iam.authentication.application.command.ResetMfaCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.SetPasswordCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.RevokeSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.MfaAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.PasswordAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.SessionCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordCredentialRepository;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.identity.application.command.ChangeHumanUserStatusCommand;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserQuery;
import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.token.application.command.IssueOneTimeTokenCommand;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/** Canonical audited administration for browser sessions, password setup and MFA reset. */
public final class IamSessionCredentialRuntimeOrchestrator
        implements IamSessionAdministrationApiPort, IamCredentialAdministrationApiPort {
    private final SessionCommandPort sessions;
    private final BrowserSessionRepository repo;
    private final MfaAuthenticationCommandPort mfa;
    private final PasswordAuthenticationCommandPort passwords;
    private final PasswordCredentialRepository passwordCredentials;
    private final AccessTokenCommandPort tokens;
    private final IamActivationDeliveryPort delivery;
    private final IamIdempotencyExecutor idem;
    private final IdentityQueryPort identities;
    private final IdentityCommandPort identityCommands;

    public IamSessionCredentialRuntimeOrchestrator(
            SessionCommandPort sessions,
            BrowserSessionRepository repo,
            MfaAuthenticationCommandPort mfa,
            PasswordAuthenticationCommandPort passwords,
            PasswordCredentialRepository passwordCredentials,
            AccessTokenCommandPort tokens,
            IamActivationDeliveryPort delivery,
            IamIdempotencyExecutor idem,
            IdentityQueryPort identities,
            IdentityCommandPort identityCommands) {
        this.sessions = sessions;
        this.repo = repo;
        this.mfa = mfa;
        this.passwords = passwords;
        this.passwordCredentials = passwordCredentials;
        this.tokens = tokens;
        this.delivery = delivery;
        this.idem = idem;
        this.identities = identities;
        this.identityCommands = identityCommands;
    }

    @Override
    public void revoke(String sessionId, long version, String reason, IamApiRequestContext context) {
        String tenant = context.activeTenantId();
        idem.execute(tenant, context.actorId(), "security.session.revoke", context.requireIdempotencyKey(),
                Map.of("sessionId", sessionId, "version", version, "reason", reason), 204, String.class,
                () -> IamTenantContextHolder.withContext(new IamTenantExecutionContext(tenant, context.actorId()), () -> {
                    sessions.revoke(new RevokeSessionCommand(
                            sessionId, context.actorId(), reason, context.correlationId(), context.requestedAt(), version));
                    return "OK";
                }));
    }

    @Override
    public void revokeAllForUser(String userId, String reason, IamApiRequestContext context) {
        String tenant = context.activeTenantId();
        idem.execute(tenant, context.actorId(), "security.session.revoke-all", context.requireIdempotencyKey(),
                Map.of("userId", userId, "reason", reason), 204, String.class,
                () -> IamTenantContextHolder.withContext(new IamTenantExecutionContext(tenant, context.actorId()), () -> {
                    repo.revokeAll(CredentialSubjectType.HUMAN_USER, userId, tenant,
                            context.actorId(), reason, context.requestedAt());
                    return "OK";
                }));
    }

    @Override
    public void initiatePasswordReset(String userId, String reason, IamApiRequestContext context) {
        initiatePasswordSetup(userId, "", reason, context);
    }

    @Override
    public CredentialSetupResponse initiatePasswordSetup(
            String userId, String deliveryMethod, String reason, IamApiRequestContext context) {
        String tenant = context.activeTenantId();
        return idem.execute(tenant, context.actorId(), "security.password.setup.initiate", context.requireIdempotencyKey(),
                Map.of("userId", userId, "deliveryMethod", deliveryMethod == null ? "" : deliveryMethod, "reason", reason),
                202, CredentialSetupResponse.class,
                () -> IamTenantContextHolder.withContext(new IamTenantExecutionContext(tenant, context.actorId()), () -> {
                    var user = identities.findHumanUser(new FindHumanUserQuery(userId))
                            .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
                    var issued = tokens.issueOneTime(new IssueOneTimeTokenCommand(
                            tenant, userId, AccessTokenType.PASSWORD_RESET_TOKEN, Duration.ofHours(1),
                            context.actorId(), context.correlationId()));
                    String recipient = user.email().map(email -> email.value()).orElse(user.username().value());
                    String purpose = user.status() == AccountStatus.PASSWORD_RESET_REQUIRED
                            || user.status() == AccountStatus.PENDING_ACTIVATION
                            ? "ACCOUNT_SETUP" : "ADMIN_PASSWORD_RESET";
                    var receipt = delivery.deliver(new IamActivationDeliveryPort.DeliveryCommand(
                            tenant, userId, issued.tokenId(), purpose, deliveryMethod, recipient,
                            issued.token(), issued.expiresAt(), context.actorId(), context.correlationId()));
                    return new CredentialSetupResponse(
                            userId, purpose, receipt.deliveryId(), receipt.deliveryMethod(), receipt.deliveryStatus(),
                            receipt.recipientReference(), receipt.expiresAt(), receipt.failureCode(), receipt.setupActionUrl());
                }));
    }

    @Override
    public void setTemporaryPassword(
            String userId, String temporaryPassword, String reason, IamApiRequestContext context) {
        String tenant = context.activeTenantId();
        idem.execute(tenant, context.actorId(), "security.password.temporary-set", context.requireIdempotencyKey(),
                Map.of("userId", userId, "temporaryPasswordConfigured", true, "reason", reason), 204, String.class,
                () -> IamTenantContextHolder.withContext(new IamTenantExecutionContext(tenant, context.actorId()), () -> {
                    var user = identities.findHumanUser(new FindHumanUserQuery(userId))
                            .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
                    long credentialVersion = passwordCredentials.find(CredentialSubjectType.HUMAN_USER, userId)
                            .map(credential -> credential.version()).orElse(0L);
                    passwords.setPassword(new SetPasswordCommand(
                            CredentialSubjectType.HUMAN_USER, userId, user.username().value(), tenant,
                            temporaryPassword.toCharArray(), true, context.actorId(), context.correlationId(),
                            context.requestedAt(), credentialVersion));
                    if (user.status() == AccountStatus.ACTIVE
                            || user.status() == AccountStatus.LOCKED
                            || user.status() == AccountStatus.PENDING_ACTIVATION
                            || user.status() == AccountStatus.MFA_ENROLLMENT_REQUIRED) {
                        identityCommands.changeHumanUserStatus(new ChangeHumanUserStatusCommand(
                                userId, AccountStatus.PASSWORD_RESET_REQUIRED, user.version(),
                                "Administrator set a temporary password; password change is required at next sign-in",
                                context.actorId(), context.correlationId(), UUID.randomUUID().toString()));
                    }
                    return "OK";
                }));
    }

    @Override
    public void resetMfa(String userId, String reason, IamApiRequestContext context) {
        String tenant = context.activeTenantId();
        idem.execute(tenant, context.actorId(), "security.mfa.reset", context.requireIdempotencyKey(),
                Map.of("userId", userId, "reason", reason), 204, String.class,
                () -> IamTenantContextHolder.withContext(new IamTenantExecutionContext(tenant, context.actorId()), () -> {
                    var user = identities.findHumanUser(new FindHumanUserQuery(userId))
                            .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
                    mfa.reset(new ResetMfaCommand(
                            CredentialSubjectType.HUMAN_USER, userId, context.actorId(), reason,
                            context.correlationId(), context.requestedAt()));
                    repo.revokeAll(CredentialSubjectType.HUMAN_USER, userId, tenant,
                            context.actorId(), "MFA_RESET", context.requestedAt());
                    if (user.status() == AccountStatus.ACTIVE) {
                        identityCommands.changeHumanUserStatus(new ChangeHumanUserStatusCommand(
                                userId,
                                AccountStatus.MFA_ENROLLMENT_REQUIRED,
                                user.version(),
                                "MFA reset by administrator; enrollment is required before sign-in is ready",
                                context.actorId(),
                                context.correlationId(),
                                UUID.randomUUID().toString()));
                    }
                    return "OK";
                }));
    }
}
