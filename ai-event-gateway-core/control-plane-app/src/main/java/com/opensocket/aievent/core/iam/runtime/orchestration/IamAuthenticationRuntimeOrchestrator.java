package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.api.application.port.IamAuthenticationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamAdministrationProjectionPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import com.opensocket.aievent.core.iam.api.application.service.IamEffectiveAccessQueryService;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.authentication.application.command.*;
import com.opensocket.aievent.core.iam.authentication.application.port.in.*;
import com.opensocket.aievent.core.iam.authentication.application.port.out.*;
import com.opensocket.aievent.core.iam.authentication.application.result.PasswordAuthenticationResult;
import com.opensocket.aievent.core.iam.authentication.domain.*;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.command.ChangeHumanUserStatusCommand;
import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserQuery;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.organization.application.command.ChangeTenantMembershipStatusCommand;
import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import com.opensocket.aievent.core.iam.runtime.credential.CanonicalCredentialBroker;
import com.opensocket.aievent.core.iam.runtime.credential.LegacyPasswordCredentialAdapter;
import com.opensocket.aievent.core.iam.security.contract.*;
import com.opensocket.aievent.core.iam.token.application.command.*;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

public final class IamAuthenticationRuntimeOrchestrator implements IamAuthenticationApiPort {
    public static final String PASSWORD_CHANGE_REQUIRED_METHOD = "PASSWORD_CHANGE_REQUIRED";
    public static final String ROOT_BOOTSTRAP_REQUIRED_METHOD = "ROOT_BOOTSTRAP_REQUIRED";
    public static final String HUMAN_MFA_ENROLLMENT_REQUIRED_METHOD = "HUMAN_MFA_ENROLLMENT_REQUIRED";

    private final PasswordAuthenticationCommandPort passwords;
    private final MfaAuthenticationCommandPort mfa;
    private final SessionCommandPort sessions;
    private final BrowserSessionRepository sessionRepo;
    private final SessionPolicyRepository policies;
    private final PasswordCredentialRepository credentials;
    private final RootBootstrapStateRepository bootstrapStates;
    private final AccessTokenCommandPort tokens;
    private final IdentityQueryPort identities;
    private final IdentityCommandPort identityCommands;
    private final TenantCommandPort tenantCommands;
    private final IamAdministrationProjectionPort projections;
    private final IamApiRuntimeDao dao;
    private final IamLoginChallengeService challenges;
    private final IamOneTimeSecretDeliveryPort delivery;
    private final IamIdempotencyExecutor idem;
    private final TransactionTemplate tx;
    private final IamRuntimeProperties properties;
    private final CanonicalCredentialBroker credentialBroker;
    private final LegacyPasswordCredentialAdapter legacyPasswords;
    private final IamEffectiveAccessQueryService effectiveAccess;
    private final IamFederationRuntimeOrchestrator federation;
    private final Clock clock;

    public IamAuthenticationRuntimeOrchestrator(
            PasswordAuthenticationCommandPort passwords,
            MfaAuthenticationCommandPort mfa,
            SessionCommandPort sessions,
            BrowserSessionRepository sessionRepo,
            SessionPolicyRepository policies,
            PasswordCredentialRepository credentials,
            RootBootstrapStateRepository bootstrapStates,
            AccessTokenCommandPort tokens,
            IdentityQueryPort identities,
            IdentityCommandPort identityCommands,
            TenantCommandPort tenantCommands,
            IamAdministrationProjectionPort projections,
            IamApiRuntimeDao dao,
            IamLoginChallengeService challenges,
            IamOneTimeSecretDeliveryPort delivery,
            IamIdempotencyExecutor idem,
            TransactionTemplate tx,
            IamRuntimeProperties properties,
            CanonicalCredentialBroker credentialBroker,
            LegacyPasswordCredentialAdapter legacyPasswords,
            IamEffectiveAccessQueryService effectiveAccess,
            IamFederationRuntimeOrchestrator federation,
            Clock clock) {
        this.passwords = passwords;
        this.mfa = mfa;
        this.sessions = sessions;
        this.sessionRepo = sessionRepo;
        this.policies = policies;
        this.credentials = credentials;
        this.bootstrapStates = bootstrapStates;
        this.tokens = tokens;
        this.identities = identities;
        this.identityCommands = identityCommands;
        this.tenantCommands = tenantCommands;
        this.projections = projections;
        this.dao = dao;
        this.challenges = challenges;
        this.delivery = delivery;
        this.idem = idem;
        this.tx = tx;
        this.properties = properties;
        this.credentialBroker = credentialBroker;
        this.legacyPasswords = legacyPasswords;
        this.effectiveAccess = Objects.requireNonNull(effectiveAccess);
        this.federation = Objects.requireNonNull(federation);
        this.clock = clock;
    }

    @Override
    public LoginResponse login(LoginRequest request, IamApiRequestContext context) {
        // Tenant is provisioning data, not an authentication choice. Resolve the person's home
        // workspace from canonical Tenant Membership before invoking the credential provider.
        // requestedTenantId is intentionally ignored for interactive password sign-in.
        String configuredTenant = automaticTenantForUsername(request.username());
        CanonicalCredentialBroker.BrokeredAuthentication brokered = withOptionalTenant(
                configuredTenant,
                "login:" + request.username(),
                () -> credentialBroker.authenticate(new AuthenticatePasswordCommand(
                        request.username(), request.password().toCharArray(), configuredTenant,
                        context.clientAddress(), context.userAgent(), context.correlationId(), context.requestedAt())));
        PasswordAuthenticationResult auth = brokered.result();
        if (auth.subjectType() == CredentialSubjectType.HUMAN_USER
                && (auth.tenantId() == null || auth.tenantId().isBlank())) {
            String resolvedTenant = automaticTenantForUser(auth.subjectId(), true);
            auth = new PasswordAuthenticationResult(
                    auth.subjectType(), auth.subjectId(), auth.username(), resolvedTenant, auth.mfaRequired(),
                    auth.passwordChangeRequired(), auth.credentialVersion(), auth.authenticatedAt());
        }
        Set<String> credentialMethods = brokered.authenticationMethods();
        List<TenantChoiceResponse> choices = auth.subjectType() == CredentialSubjectType.HUMAN_USER
                ? choices(auth.subjectId()) : List.of();
        if (auth.subjectType() == CredentialSubjectType.HUMAN_USER) {
            federation.requireLocalLoginAllowed(auth.tenantId());
        }

        // Forced password change always precedes MFA and receives a deliberately restricted session.
        if (auth.passwordChangeRequired()) {
            SessionResponse limited = createSession(
                    auth.subjectType(), auth.subjectId(), auth.tenantId(),
                    withMethod(credentialMethods, PASSWORD_CHANGE_REQUIRED_METHOD), Optional.empty(), context);
            return new LoginResponse("PASSWORD_CHANGE_REQUIRED", "", limited, choices,
                    List.of("CHANGE_PASSWORD"), auth.credentialVersion());
        }

        if (auth.subjectType() == CredentialSubjectType.INSTANCE_ROOT) {
            RootBootstrapState bootstrap = bootstrapStates.find();
            if (bootstrap.status() != RootBootstrapState.Status.COMPLETED && !bootstrap.mfaConfigured()) {
                SessionResponse bootstrapSession = createSession(
                        auth.subjectType(), auth.subjectId(), auth.tenantId(),
                        withMethod(credentialMethods, ROOT_BOOTSTRAP_REQUIRED_METHOD), Optional.empty(), context);
                return new LoginResponse("MFA_ENROLLMENT_REQUIRED", "", bootstrapSession, choices,
                        List.of("COMPLETE_BOOTSTRAP"), auth.credentialVersion());
            }
        }

        if (auth.subjectType() == CredentialSubjectType.HUMAN_USER) {
            var human = identities.findHumanUser(new FindHumanUserQuery(auth.subjectId())).orElse(null);
            if (human != null && human.status() == AccountStatus.MFA_ENROLLMENT_REQUIRED) {
                SessionResponse enrollmentSession = createSession(
                        auth.subjectType(), auth.subjectId(), auth.tenantId(),
                        withMethod(credentialMethods, HUMAN_MFA_ENROLLMENT_REQUIRED_METHOD), Optional.empty(), context);
                return new LoginResponse("MFA_ENROLLMENT_REQUIRED", "", enrollmentSession, choices,
                        List.of("ENROLL_MFA"), auth.credentialVersion());
            }
        }

        if (auth.mfaRequired()) {
            String challenge = challenges.issue(
                    auth.subjectType(), auth.subjectId(), auth.tenantId(), auth.authenticatedAt(),
                    context.correlationId(), context.clientAddress(), context.userAgent());
            return new LoginResponse("MFA_REQUIRED", challenge, null, choices,
                    actions(auth), auth.credentialVersion());
        }

        SessionResponse session = createSession(
                auth.subjectType(), auth.subjectId(), auth.tenantId(), credentialMethods, Optional.empty(), context);
        return new LoginResponse("AUTHENTICATED", "", session, choices,
                actions(auth), auth.credentialVersion());
    }

    @Override
    public LoginResponse verifyMfa(VerifyLoginMfaRequest request, IamApiRequestContext context) {
        IamLoginChallengeService.Challenge challenge = challenges.inspect(request.challengeId());
        boolean ok = withOptionalTenant(challenge.tenantId(), "mfa:" + challenge.subjectId(),
                () -> mfa.verify(new VerifyMfaCommand(
                        challenge.subjectType(), challenge.subjectId(), request.code(), request.recoveryCode(),
                        context.correlationId(), context.requestedAt())));
        if (!ok) throw new IllegalArgumentException("AUTH_MFA_INVALID");
        challenges.consume(request.challengeId(), challenge);
        Set<String> methods = request.recoveryCode()
                ? Set.of("PASSWORD", "RECOVERY_CODE") : Set.of("PASSWORD", "TOTP");
        SessionResponse session = createSession(
                challenge.subjectType(), challenge.subjectId(), challenge.tenantId(),
                methods, Optional.of(context.requestedAt()), context);
        long credentialVersion = credentials.find(challenge.subjectType(), challenge.subjectId())
                .map(PasswordCredential::version).orElse(0L);
        return new LoginResponse("AUTHENTICATED", "", session,
                challenge.subjectType() == CredentialSubjectType.HUMAN_USER
                        ? choices(challenge.subjectId()) : List.of(),
                List.of(), credentialVersion);
    }

    @Override public SessionResponse currentSession(IamApiRequestContext context) {
        return loadSession(context.requireAuthentication());
    }

    @Override
    public SessionResponse switchTenant(SwitchTenantRequest request, IamApiRequestContext context) {
        // Compatibility route only. Tenant is an administrator-provisioned home workspace, not a
        // session preference. Normal human identities cannot change authorization context after
        // sign-in. Instance Root changes only the administration URL/request context and never
        // rotates a human Tenant Session through this endpoint.
        throw com.opensocket.aievent.core.iam.api.error.IamApiException.forbidden(
                "AUTH_TENANT_SWITCH_DISABLED",
                "Tenant switching is not available. Your workspace is assigned by an administrator through Tenant Membership.",
                "");
    }

    @Override
    public void logout(IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        idem.execute(context.activeTenantId(), context.actorId(), "auth.logout", key,
                Map.of("session", sessionId(context.requireAuthentication())), 204, String.class, () -> {
                    BrowserSession current = findSession(context.requireAuthentication());
                    withOptionalTenant(current.tenant().tenantId(), context.actorId(), () -> sessions.revoke(
                            new RevokeSessionCommand(current.sessionId(), context.actorId(), "LOGOUT",
                                    context.correlationId(), context.requestedAt(), current.version())));
                    return "OK";
                });
    }

    @Override
    public void logoutAll(IamApiRequestContext context) {
        String key = context.requireIdempotencyKey();
        idem.execute(context.activeTenantId(), context.actorId(), "auth.logout-all", key,
                Map.of("subject", context.actorId()), 204, String.class, () -> {
                    AuthenticationContext authentication = context.requireAuthentication();
                    if (authentication.subject().identityType() == SubjectRef.IdentityType.INSTANCE_ROOT) {
                        sessionRepo.revokeAll(CredentialSubjectType.INSTANCE_ROOT,
                                authentication.subject().subjectId(), "", authentication.subject().subjectId(),
                                "LOGOUT_ALL", context.requestedAt());
                    } else {
                        for (TenantChoiceResponse choice : choices(authentication.subject().subjectId())) {
                            withOptionalTenant(choice.tenantId(), authentication.subject().subjectId(),
                                    () -> sessionRepo.revokeAll(CredentialSubjectType.HUMAN_USER,
                                            authentication.subject().subjectId(), choice.tenantId(),
                                            authentication.subject().subjectId(), "LOGOUT_ALL", context.requestedAt()));
                        }
                    }
                    return "OK";
                });
    }

    @Override
    public void changePassword(ChangePasswordRequest request, IamApiRequestContext context) {
        AuthenticationContext authentication = context.requireAuthentication();
        String tenant = authentication.activeTenant().tenantId();
        String username = identities.findHumanUser(new FindHumanUserQuery(authentication.subject().subjectId()))
                .map(user -> user.username().value()).orElse("root");
        idem.execute(tenant.isBlank() ? "INSTANCE" : tenant, context.actorId(), "auth.change-password",
                context.requireIdempotencyKey(), request, 204, String.class, () -> {
                    PasswordCredential changed;
                    if (authentication.assurance().methods().contains(LegacyPasswordCredentialAdapter.METHOD)) {
                        legacyPasswords.requireCurrentPassword(
                                authentication.subject().subjectId(), username, request.currentPassword().toCharArray());
                        long currentVersion = credentials.find(
                                CredentialSubjectType.HUMAN_USER, authentication.subject().subjectId())
                                .map(PasswordCredential::version).orElse(0L);
                        changed = withOptionalTenant(tenant, context.actorId(),
                                () -> passwords.setPassword(new SetPasswordCommand(
                                        CredentialSubjectType.HUMAN_USER, authentication.subject().subjectId(),
                                        username, tenant, request.newPassword().toCharArray(), false,
                                        context.actorId(), context.correlationId(), context.requestedAt(),
                                        currentVersion)));
                        legacyPasswords.markReplaced(
                                authentication.subject().subjectId(), context.actorId(), context.requestedAt());
                    } else {
                        changed = withOptionalTenant(tenant, context.actorId(),
                                () -> passwords.changePassword(new ChangePasswordCommand(
                                        type(authentication), authentication.subject().subjectId(), username, tenant,
                                        request.currentPassword().toCharArray(), request.newPassword().toCharArray(),
                                        context.actorId(), context.correlationId(), context.requestedAt(),
                                        request.expectedVersion())));
                    }
                    if (authentication.subject().identityType() == SubjectRef.IdentityType.INSTANCE_ROOT
                            && authentication.assurance().methods().contains(PASSWORD_CHANGE_REQUIRED_METHOD)) {
                        markInitialRootPasswordChanged(changed, context);
                    } else if (authentication.subject().identityType() == SubjectRef.IdentityType.HUMAN_USER
                            && authentication.assurance().methods().contains(PASSWORD_CHANGE_REQUIRED_METHOD)) {
                        var human = identities.findHumanUser(new FindHumanUserQuery(authentication.subject().subjectId()))
                                .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
                        if (human.status() == AccountStatus.ACTIVE
                                || human.status() == AccountStatus.PASSWORD_RESET_REQUIRED) {
                            transitionUser(human.userId().value(), human.version(), AccountStatus.MFA_ENROLLMENT_REQUIRED,
                                    "Initial temporary password changed; MFA enrollment is required before normal sign-in",
                                    human.userId().value(), context.correlationId(), context.requestedAt());
                        }
                    }
                    return "OK";
                });
    }

    @Override
    public void requestPasswordReset(ForgotPasswordRequest request, IamApiRequestContext context) {
        idem.execute("INSTANCE", "ANONYMOUS", "auth.password-reset.request",
                context.requireIdempotencyKey(), request, 202, String.class, () -> {
                    String normalized = request.username().trim().toLowerCase(Locale.ROOT);
                    Map<String,Object> user = tx.execute(status -> dao.findUserByNormalizedUsername(normalized));
                    if (user == null) return "ACCEPTED";
                    String userId = text(user, "userId");
                    // forgot-password is intentionally anonymous and enumeration-safe.
                    // Resolve only the delivery Tenant from the instance-level user/Tenant directory;
                    // never call choices(userId) here because choices() also projects effective RBAC
                    // authority and therefore requires an authenticated Tenant execution context.
                    String tenant = passwordResetTenantForUser(userId);
                    if (tenant.isBlank()) return "ACCEPTED";
                    var issued = withOptionalTenant(tenant, "ANONYMOUS", () -> tokens.issueOneTime(
                            new IssueOneTimeTokenCommand(tenant, userId, AccessTokenType.PASSWORD_RESET_TOKEN,
                                    Duration.ofHours(1), "ANONYMOUS", context.correlationId())));
                    delivery.deliver("PASSWORD_RESET", request.username(), issued.token(),
                            issued.expiresAt(), context.correlationId());
                    return "ACCEPTED";
                });
    }

    @Override
    public void resetPassword(ResetPasswordRequest request, IamApiRequestContext context) {
        String prefix = prefix(request.token());
        Map<String,Object> directory = tx.execute(status ->
                dao.resolveOneTimeTokenTenant(prefix, AccessTokenType.PASSWORD_RESET_TOKEN.name()));
        if (directory == null) throw new IllegalArgumentException("AUTH_TOKEN_INVALID");
        String tenant = text(directory, "tenantId");
        idem.execute(tenant, "ANONYMOUS", "auth.password-reset.consume",
                context.requireIdempotencyKey(), request, 204, String.class, () -> {
                    var validated = withOptionalTenant(tenant, "ANONYMOUS", () -> tokens.consumeOneTime(
                            new ConsumeOneTimeTokenCommand(request.token(), tenant,
                                    AccessTokenType.PASSWORD_RESET_TOKEN, context.clientAddress(),
                                    context.correlationId())));
                    var user = identities.findHumanUser(new FindHumanUserQuery(validated.principal().principalId()))
                            .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
                    long version = credentials.find(CredentialSubjectType.HUMAN_USER, user.userId().value())
                            .map(PasswordCredential::version).orElse(0L);
                    boolean initialSetup = version == 0L || user.status() == AccountStatus.PASSWORD_RESET_REQUIRED;
                    withOptionalTenant(tenant, "ANONYMOUS", () -> passwords.setPassword(new SetPasswordCommand(
                            CredentialSubjectType.HUMAN_USER, user.userId().value(), user.username().value(),
                            tenant, request.newPassword().toCharArray(), false, user.userId().value(),
                            context.correlationId(), context.requestedAt(), version)));
                    if (initialSetup && (user.status() == AccountStatus.ACTIVE
                            || user.status() == AccountStatus.PASSWORD_RESET_REQUIRED)) {
                        transitionUser(user.userId().value(), user.version(), AccountStatus.MFA_ENROLLMENT_REQUIRED,
                                "Initial password configured; MFA enrollment is required", user.userId().value(),
                                context.correlationId(), context.requestedAt());
                    }
                    return "OK";
                });
    }

    @Override
    public void activateInvitation(ActivateInvitationRequest request, IamApiRequestContext context) {
        String prefix = prefix(request.token());
        Map<String,Object> directory = tx.execute(status ->
                dao.resolveOneTimeTokenTenant(prefix, AccessTokenType.INVITATION_TOKEN.name()));
        if (directory == null) throw new IllegalArgumentException("AUTH_TOKEN_INVALID");
        String tenant = text(directory, "tenantId");
        idem.execute(tenant, "ANONYMOUS", "auth.invitation.activate", context.requireIdempotencyKey(), request, 204,
                String.class, () -> withOptionalTenant(tenant, "ANONYMOUS", () -> {
                    var validated = tokens.consumeOneTime(new ConsumeOneTimeTokenCommand(
                            request.token(), tenant, AccessTokenType.INVITATION_TOKEN, context.clientAddress(),
                            context.correlationId()));
                    var user = identities.findHumanUser(new FindHumanUserQuery(validated.principal().principalId()))
                            .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
                    long credentialVersion = credentials.find(CredentialSubjectType.HUMAN_USER, user.userId().value())
                            .map(PasswordCredential::version).orElse(0L);
                    passwords.setPassword(new SetPasswordCommand(
                            CredentialSubjectType.HUMAN_USER, user.userId().value(), user.username().value(), tenant,
                            request.newPassword().toCharArray(), false, user.userId().value(), context.correlationId(),
                            context.requestedAt(), credentialVersion));
                    if (user.status() == AccountStatus.PENDING_ACTIVATION
                            || user.status() == AccountStatus.PASSWORD_RESET_REQUIRED) {
                        transitionUser(user.userId().value(), user.version(), AccountStatus.MFA_ENROLLMENT_REQUIRED,
                                "Invitation accepted; MFA enrollment is required", user.userId().value(),
                                context.correlationId(), context.requestedAt());
                    }
                    var tenantMembership = projections.memberships(tenant, user.userId().value(), 100, "").items().stream()
                            .filter(item -> "TENANT".equals(item.membershipType()) && tenant.equals(item.resourceId()))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("AUTH_TENANT_MEMBERSHIP_REQUIRED"));
                    if ("INVITED".equals(tenantMembership.status())) {
                        tenantCommands.changeTenantMembershipStatus(new ChangeTenantMembershipStatusCommand(
                                tenant, tenantMembership.membershipId(), MembershipStatus.ACTIVE, tenantMembership.version(),
                                "Invitation accepted", user.userId().value(), context.correlationId(),
                                UUID.randomUUID().toString()));
                    }
                    return "OK";
                }));
    }

    @Override
    public MfaEnrollmentResponse beginUserMfa(BeginMfaEnrollmentRequest request, IamApiRequestContext context) {
        var authentication = context.requireAuthentication();
        if (authentication.subject().identityType() != SubjectRef.IdentityType.HUMAN_USER) {
            throw new IllegalArgumentException("AUTH_MFA_ENROLLMENT_HUMAN_USER_REQUIRED");
        }
        if (!authentication.assurance().methods().contains(HUMAN_MFA_ENROLLMENT_REQUIRED_METHOD)) {
            throw new IllegalArgumentException("AUTH_MFA_ENROLLMENT_SESSION_REQUIRED");
        }
        var result = mfa.begin(new BeginTotpEnrollmentCommand(
                CredentialSubjectType.HUMAN_USER, authentication.subject().subjectId(), request.accountLabel(),
                request.issuer(), authentication.subject().subjectId(), context.correlationId(), context.requestedAt()));
        return new MfaEnrollmentResponse(result.methodId(), result.otpauthUri(), result.secret(),
                result.recoveryCodes(), result.expectedVersion());
    }

    @Override
    public void confirmUserMfa(ConfirmMfaEnrollmentRequest request, IamApiRequestContext context) {
        var authentication = context.requireAuthentication();
        if (authentication.subject().identityType() != SubjectRef.IdentityType.HUMAN_USER
                || !authentication.assurance().methods().contains(HUMAN_MFA_ENROLLMENT_REQUIRED_METHOD)) {
            throw new IllegalArgumentException("AUTH_MFA_ENROLLMENT_SESSION_REQUIRED");
        }
        String userId = authentication.subject().subjectId();
        String tenant = context.activeTenantId();
        idem.execute(tenant, userId, "auth.mfa.enrollment.confirm", context.requireIdempotencyKey(), request, 204,
                String.class, () -> withOptionalTenant(tenant, userId, () -> {
                    mfa.confirm(new ConfirmTotpEnrollmentCommand(request.methodId(), request.code(), userId,
                            context.correlationId(), context.requestedAt(), request.expectedVersion()));
                    var user = identities.findHumanUser(new FindHumanUserQuery(userId))
                            .orElseThrow(() -> new IllegalArgumentException("IDENTITY_USER_NOT_FOUND"));
                    if (user.status() == AccountStatus.MFA_ENROLLMENT_REQUIRED) {
                        transitionUser(userId, user.version(), AccountStatus.ACTIVE,
                                "MFA enrollment completed", userId, context.correlationId(), context.requestedAt());
                    }
                    return "OK";
                }));
    }

    private void transitionUser(String userId, long expectedVersion, AccountStatus target, String reason,
                                String actorId, String correlationId, Instant at) {
        identityCommands.changeHumanUserStatus(new ChangeHumanUserStatusCommand(
                userId, target, expectedVersion, reason, actorId, correlationId, UUID.randomUUID().toString()));
    }

    private SessionResponse createSession(
            CredentialSubjectType type,
            String subject,
            String tenant,
            Set<String> methods,
            Optional<Instant> mfaAt,
            IamApiRequestContext context) {
        TenantRef ref = type == CredentialSubjectType.INSTANCE_ROOT
                ? TenantRef.instance() : TenantRef.tenant(tenant);
        SessionPolicy policy;
        if (methods.contains(PASSWORD_CHANGE_REQUIRED_METHOD)) {
            policy = SessionPolicy.passwordChangeRequired(properties.getRootPasswordChangeSessionTtl());
        } else if (type == CredentialSubjectType.INSTANCE_ROOT) {
            policy = SessionPolicy.rootRecovery();
        } else {
            policy = tenantRead(tenant, subject,
                    () -> policies.findTenantPolicy(tenant).orElse(policies.instanceMinimum()));
        }
        BrowserSession session = withOptionalTenant(tenant, subject, () -> sessions.create(
                new CreateBrowserSessionCommand(type, subject, ref, methods, mfaAt,
                        context.clientAddress(), context.userAgent(), context.correlationId(), context.requestedAt()),
                policy));
        return response(session);
    }

    private void markInitialRootPasswordChanged(PasswordCredential changed, IamApiRequestContext context) {
        Instant at = context.requestedAt();
        Map<String, Object> row = new HashMap<>();
        row.put("eventId", UUID.randomUUID().toString());
        row.put("eventType", "ROOT_INITIAL_PASSWORD_CHANGED");
        row.put("credentialVersion", changed.version());
        row.put("correlationId", context.correlationId());
        row.put("detailsJson", "{\"bootstrapSessionRevoked\":true}");
        row.put("occurredAt", at);
        if (dao.updateInitialRootPasswordState(row) != 1) {
            throw new IllegalStateException("ROOT_INITIAL_PASSWORD_STATE_UPDATE_FAILED");
        }
        if (dao.updateRootLastLogin(row) != 1) {
            throw new IllegalStateException("ROOT_LAST_LOGIN_UPDATE_FAILED");
        }
        if (dao.insertRootInstallationEvent(row) != 1) {
            throw new IllegalStateException("ROOT_INITIAL_PASSWORD_EVIDENCE_FAILED");
        }
    }

    private void requireUnrestricted(AuthenticationContext authentication) {
        if (authentication.assurance().methods().contains(PASSWORD_CHANGE_REQUIRED_METHOD)) {
            throw new IllegalArgumentException("AUTH_PASSWORD_CHANGE_REQUIRED");
        }
    }

    private BrowserSession findSession(AuthenticationContext authentication) {
        String id = sessionId(authentication);
        String tenant = authentication.activeTenant().tenantId();
        Supplier<BrowserSession> lookup = () -> sessionRepo.find(id)
                .orElseThrow(() -> new IllegalArgumentException("AUTH_SESSION_REVOKED"));
        return tenant == null || tenant.isBlank()
                ? readInTransaction(lookup)
                : tenantRead(tenant, authentication.subject().subjectId(), lookup);
    }
    private SessionResponse loadSession(AuthenticationContext authentication) { return response(findSession(authentication)); }

    private String automaticTenantForUsername(String username) {
        if (username == null || username.isBlank() || "root".equalsIgnoreCase(username.trim())) return "";
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        Map<String,Object> user = tx.execute(status -> dao.findUserByNormalizedUsername(normalized));
        if (user == null) return "";
        return automaticTenantForUser(text(user, "userId"), false);
    }

    private String automaticTenantForUser(String userId, boolean failIfUnconfigured) {
        List<Map<String,Object>> rows = tx.execute(status -> dao.activeTenantChoices(userId));
        if (rows == null || rows.isEmpty()) {
            if (!failIfUnconfigured) return "";
            throw com.opensocket.aievent.core.iam.api.error.IamApiException.forbidden(
                    "AUTH_TENANT_MEMBERSHIP_REQUIRED",
                    "This account does not have an active Tenant workspace. Ask an administrator to review the Person's Tenant Membership.",
                    "");
        }
        for (Map<String,Object> row : rows) {
            if (booleanValue(row, "defaultTenant")) return text(row, "tenantId");
        }
        if (rows.size() == 1) return text(rows.getFirst(), "tenantId");
        if (!failIfUnconfigured) return "";
        throw com.opensocket.aievent.core.iam.api.error.IamApiException.forbidden(
                "AUTH_DEFAULT_TENANT_REQUIRED",
                "This account has multiple active Tenant memberships but no default home workspace. An administrator must set the Default Tenant; users do not choose a Tenant during sign-in.",
                "");
    }

    private static boolean booleanValue(Map<String,Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean bool) return bool;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Resolves the Tenant used only to issue an anonymous password-reset token.
     *
     * <p>This deliberately reads the instance-level iam_user_tenant_directory and does not
     * calculate RBAC/effective authority. Public forgot-password must remain enumeration-safe
     * and must not require an authenticated Tenant context merely to discover where the user's
     * reset credential belongs.</p>
     */
    private String passwordResetTenantForUser(String userId) {
        List<Map<String,Object>> rows = tx.execute(status -> dao.activeTenantChoices(userId));
        if (rows == null || rows.isEmpty()) return "";
        for (Map<String,Object> row : rows) {
            if (booleanValue(row, "defaultTenant")) return text(row, "tenantId");
        }
        // Preserve the previous forgot-password behavior: activeTenantChoices is deterministic
        // (default first, then oldest directory entry), so a user with multiple active workspaces
        // still receives one reset token without exposing Tenant selection to the anonymous caller.
        return text(rows.getFirst(), "tenantId");
    }

    private List<TenantChoiceResponse> choices(String user) {
        List<Map<String,Object>> rows = tx.execute(status -> dao.activeTenantChoices(user));
        List<TenantChoiceResponse> result = new ArrayList<>();
        if (rows != null) for (Map<String,Object> row : rows) {
            String tenant = text(row, "tenantId");
            Set<String> roles = effectiveAccess.effectiveAuthority(tenant, user).roleCodes();
            result.add(new TenantChoiceResponse(tenant, text(row,"tenantCode"), text(row,"tenantName"),
                    text(row,"membershipStatus"), roles));
        }
        return List.copyOf(result);
    }

    private static SessionResponse response(BrowserSession session) {
        return new SessionResponse(session.sessionId(), session.subjectType().name(), session.subjectId(),
                session.tenant().tenantId(), session.methods(), session.createdAt(), session.lastSeenAt(),
                session.idleExpiresAt(), session.absoluteExpiresAt(), session.ipAddress(), session.userAgent(),
                session.status().name(), session.version());
    }
    private static Set<String> withMethod(Set<String> methods, String additional) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(methods);
        merged.add(additional);
        return Set.copyOf(merged);
    }

    private static List<String> actions(PasswordAuthenticationResult result) {
        List<String> actions = new ArrayList<>();
        if (result.passwordChangeRequired()) actions.add("CHANGE_PASSWORD");
        if (result.mfaRequired()) actions.add("VERIFY_MFA");
        return List.copyOf(actions);
    }


    private static String text(Map<String,Object> row,String key){Object value=row.get(key);return value==null?"":String.valueOf(value);}
    private static String prefix(String token){
        if(token==null||token.isBlank())throw new IllegalArgumentException("AUTH_TOKEN_INVALID");
        int index=token.lastIndexOf('_');
        if(index<=0)throw new IllegalArgumentException("AUTH_TOKEN_INVALID");
        return token.substring(0,index);
    }
    private static String sessionId(AuthenticationContext authentication){return authentication.session().orElseThrow(()->new IllegalArgumentException("AUTH_SESSION_REQUIRED")).sessionId();}
    private static CredentialSubjectType type(AuthenticationContext authentication){return authentication.subject().identityType()==SubjectRef.IdentityType.INSTANCE_ROOT?CredentialSubjectType.INSTANCE_ROOT:CredentialSubjectType.HUMAN_USER;}

    /**
     * Executes a direct Tenant-scoped repository read with both the PostgreSQL Tenant context and
     * an active Spring transaction. Command ports such as PasswordAuthenticationCommandPort and
     * SessionCommandPort are already transaction-proxied; direct repositories are not.
     */
    private <T> T tenantRead(String tenant, String actor, Supplier<T> action) {
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("AUTH_TENANT_REQUIRED");
        }
        String effectiveActor = actor == null || actor.isBlank() ? "ANONYMOUS" : actor;
        return IamTenantContextHolder.withContext(
                new IamTenantExecutionContext(tenant, effectiveActor),
                () -> readInTransaction(action));
    }

    private <T> T readInTransaction(Supplier<T> action) {
        return Objects.requireNonNull(
                tx.execute(status -> action.get()),
                "IAM read transaction returned no result");
    }

    private <T>T withOptionalTenant(String tenant,String actor,Supplier<T> action){
        if(tenant==null||tenant.isBlank())return action.get();
        return IamTenantContextHolder.withContext(new IamTenantExecutionContext(
                tenant,actor==null||actor.isBlank()?"ANONYMOUS":actor),action);
    }
    private void withOptionalTenant(String tenant,String actor,Runnable action){
        withOptionalTenant(tenant,actor,()->{action.run();return null;});
    }
}
