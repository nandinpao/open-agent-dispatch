package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.api.application.port.IamBootstrapApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamOneTimeSecretDeliveryPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.*;
import com.opensocket.aievent.core.iam.api.response.*;
import com.opensocket.aievent.core.iam.authentication.application.command.*;
import com.opensocket.aievent.core.iam.authentication.application.port.in.MfaAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.RootAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.in.SessionCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.RootBootstrapStateRepository;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.RootBootstrapState;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;
import com.opensocket.aievent.core.iam.identity.application.command.*;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.identity.domain.*;
import com.opensocket.aievent.core.iam.organization.application.command.*;
import com.opensocket.aievent.core.iam.organization.application.port.in.OrganizationQueryPort;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.organization.application.query.FindTenantQuery;
import com.opensocket.aievent.core.iam.organization.domain.Tenant;
import com.opensocket.aievent.core.iam.organization.domain.TenantStatus;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.rbac.application.command.BindRoleCommand;
import com.opensocket.aievent.core.iam.rbac.application.port.in.RbacAdministrationPort;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.token.application.command.IssueOneTimeTokenCommand;
import com.opensocket.aievent.core.iam.token.application.port.in.AccessTokenCommandPort;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

public final class IamBootstrapRuntimeOrchestrator implements IamBootstrapApiPort {
    private final RootBootstrapStateRepository states;
    private final IdentityCommandPort identities;
    private final IdentityQueryPort identityQueries;
    private final MfaAuthenticationCommandPort mfa;
    private final RootAuthenticationCommandPort rootAuth;
    private final SessionCommandPort sessions;
    private final BrowserSessionRepository sessionRepository;
    private final TenantCommandPort tenants;
    private final OrganizationQueryPort organizationQueries;
    private final RbacAdministrationPort rbac;
    private final AccessTokenCommandPort tokens;
    private final IamOneTimeSecretDeliveryPort delivery;
    private final IamIdempotencyExecutor idem;
    private final Clock clock;

    public IamBootstrapRuntimeOrchestrator(
            RootBootstrapStateRepository states,
            IdentityCommandPort identities,
            IdentityQueryPort identityQueries,
            MfaAuthenticationCommandPort mfa,
            RootAuthenticationCommandPort rootAuth,
            SessionCommandPort sessions,
            BrowserSessionRepository sessionRepository,
            TenantCommandPort tenants,
            OrganizationQueryPort organizationQueries,
            RbacAdministrationPort rbac,
            AccessTokenCommandPort tokens,
            IamOneTimeSecretDeliveryPort delivery,
            IamIdempotencyExecutor idem,
            Clock clock) {
        this.states = states;
        this.identities = identities;
        this.identityQueries = identityQueries;
        this.mfa = mfa;
        this.rootAuth = rootAuth;
        this.sessions = sessions;
        this.sessionRepository = sessionRepository;
        this.tenants = tenants;
        this.organizationQueries = organizationQueries;
        this.rbac = rbac;
        this.tokens = tokens;
        this.delivery = delivery;
        this.idem = idem;
        this.clock = clock;
    }

    @Override public BootstrapStatusResponse status() { return status(states.find()); }

    @Override
    public MfaEnrollmentResponse beginRootMfa(BeginMfaEnrollmentRequest request, IamApiRequestContext context) {
        requireUnrestrictedRoot(context);
        return idem.execute("INSTANCE", "root", "bootstrap.root.mfa.begin",
                context.requireIdempotencyKey(), request, 200, MfaEnrollmentResponse.class, () -> {
                    var result = mfa.begin(new BeginTotpEnrollmentCommand(
                            com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType.INSTANCE_ROOT,
                            "root", request.accountLabel(), request.issuer(), "root",
                            context.correlationId(), context.requestedAt()));
                    return new MfaEnrollmentResponse(
                            result.methodId(), result.otpauthUri(), result.secret(), result.recoveryCodes(),
                            result.expectedVersion());
                });
    }

    @Override
    public SessionResponse confirmRootMfa(ConfirmMfaEnrollmentRequest request, IamApiRequestContext context) {
        AuthenticationContext authentication = requireUnrestrictedRoot(context);
        return idem.execute("INSTANCE", "root", "bootstrap.root.mfa.confirm",
                context.requireIdempotencyKey(), request, 200, SessionResponse.class, () -> {
                    mfa.confirm(new ConfirmTotpEnrollmentCommand(
                            request.methodId(), request.code(), "root", context.correlationId(),
                            context.requestedAt(), request.expectedVersion()));
                    record(RootBootstrapStepCommand.Step.MFA_CONFIGURED, context);
                    revokeBootstrapSession(authentication, context);
                    return createMfaBootstrapSession(authentication, context);
                });
    }

    @Override
    public TenantResponse createFirstTenant(CreateTenantRequest request, IamApiRequestContext context) {
        requireUnrestrictedRoot(context);
        String idempotencyKey = context.requireIdempotencyKey();
        String tenantId = request.tenantId() == null || request.tenantId().isBlank()
                ? resource("tenant", "bootstrap.tenant.create", "INSTANCE", idempotencyKey)
                : request.tenantId().trim();
        return idem.execute("INSTANCE", "root", "bootstrap.tenant.create",
                idempotencyKey, request, 201, TenantResponse.class, () -> {
                    var tenant = tenants.createTenant(new CreateTenantCommand(
                            tenantId, request.tenantCode(), request.tenantName(), request.legalName(),
                            request.timezone(), request.locale(), request.dataRegion(), "root",
                            context.correlationId(), event("TENANT_CREATED", idempotencyKey)));
                    record(RootBootstrapStepCommand.Step.TENANT_CREATED, context);
                    return TenantResponse.from(tenant);
                });
    }

    @Override
    public UserResponse createFirstTenantAdmin(CreateTenantAdminRequest request, IamApiRequestContext context) {
        requireUnrestrictedRoot(context);
        String idempotencyKey = context.requireIdempotencyKey();
        String userId = request.userId() == null || request.userId().isBlank()
                ? resource("user", "bootstrap.tenant-admin.create", request.tenantId(), idempotencyKey)
                : request.userId().trim();
        return idem.execute("INSTANCE", "root", "bootstrap.tenant-admin.create",
                idempotencyKey, request, 201, UserResponse.class,
                () -> IamTenantContextHolder.withContext(
                        new IamTenantExecutionContext(request.tenantId(), "root"), () -> {
                            var user = identities.createHumanUser(new CreateHumanUserCommand(
                                    userId, request.username(), request.email(), request.displayName(),
                                    UserCreationMode.ADMIN_CREATED, "root", context.correlationId(),
                                    event("USER_CREATED", context.idempotencyKey())));
                            tenants.addTenantMembership(new AddTenantMembershipCommand(
                                    event("membership", context.idempotencyKey()), request.tenantId(), userId,
                                    "", null, "root", context.correlationId(),
                                    event("TENANT_MEMBERSHIP_CHANGED", context.idempotencyKey())));
                            var tenantAdministratorRole = rbac.resolveRoleByCode(
                                    request.tenantId(), "TENANT_ADMIN");
                            rbac.bindRole(new BindRoleCommand(
                                    event("binding", context.idempotencyKey()), request.tenantId(),
                                    new PrincipalRef(PrincipalRef.PrincipalType.USER, userId),
                                    tenantAdministratorRole.roleId().value(), "TENANT", request.tenantId(),
                                    context.requestedAt(), null, "root",
                                    "Create the first Tenant administrator during controlled Root bootstrap.",
                                    context.requestedAt()));
                            activateBootstrapTenant(request.tenantId(), context);
                            var issued = tokens.issueOneTime(new IssueOneTimeTokenCommand(
                                    request.tenantId(), userId, AccessTokenType.PASSWORD_RESET_TOKEN,
                                    Duration.ofHours(24), "root", context.correlationId()));
                            delivery.deliver(
                                    "TENANT_ADMIN_INITIAL_PASSWORD", userId, issued.token(),
                                    issued.expiresAt(), context.correlationId());
                            record(RootBootstrapStepCommand.Step.TENANT_ADMIN_CREATED, context);
                            return UserResponse.from(user);
                        }));
    }

    /**
     * A newly provisioned Tenant is deliberately not eligible for authentication
     * tokens. Step 3 is the atomic readiness boundary: only after the first named
     * administrator, Tenant membership and TENANT_ADMIN binding exist may the
     * Tenant become ACTIVE and receive its initial password-reset token.
     */
    Tenant activateBootstrapTenant(String tenantId, IamApiRequestContext context) {
        Tenant tenant = organizationQueries.findTenant(new FindTenantQuery(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("BOOTSTRAP_TENANT_NOT_FOUND"));
        if (tenant.status() == TenantStatus.ACTIVE) {
            return tenant;
        }
        if (tenant.status() != TenantStatus.PROVISIONING) {
            throw new IllegalArgumentException("BOOTSTRAP_TENANT_NOT_PROVISIONABLE");
        }
        return tenants.changeTenantStatus(new ChangeTenantStatusCommand(
                tenantId, TenantStatus.ACTIVE, tenant.version(), "root",
                context.correlationId(), event("TENANT_ACTIVATED", context.idempotencyKey())));
    }

    @Override
    public BootstrapStatusResponse complete(CompleteBootstrapRequest request, IamApiRequestContext context) {
        AuthenticationContext authentication = requireUnrestrictedRoot(context);
        return idem.execute("INSTANCE", "root", "bootstrap.complete",
                context.requireIdempotencyKey(), request, 200, BootstrapStatusResponse.class, () -> {
                    RootBootstrapState completed = rootAuth.recordStep(new RootBootstrapStepCommand(
                            RootBootstrapStepCommand.Step.COMPLETE, "root", context.correlationId(),
                            context.requestedAt(), request.expectedVersion()));
                    RootIdentity current = identityQueries.findRootIdentity().orElseThrow();
                    if (current.status() == RootIdentityStatus.BOOTSTRAP_PENDING) {
                        identities.changeRootIdentityStatus(new ChangeRootIdentityStatusCommand(
                                RootIdentityStatus.ACTIVE, current.version(), "BOOTSTRAP_COMPLETED", "root",
                                context.correlationId(), event("ROOT_BOOTSTRAP_COMPLETED", context.idempotencyKey())));
                    }
                    // A bootstrap-restricted session must never survive the readiness boundary.
                    // Revoke it in the same transaction; the controller also expires the browser cookie.
                    revokeBootstrapSession(authentication, context);
                    return status(completed);
                });
    }

    private void record(RootBootstrapStepCommand.Step step, IamApiRequestContext context) {
        RootBootstrapState current = states.find();
        rootAuth.recordStep(new RootBootstrapStepCommand(
                step, "root", context.correlationId(), context.requestedAt(), current.version()));
    }

    private AuthenticationContext requireUnrestrictedRoot(IamApiRequestContext context) {
        AuthenticationContext authentication = context.requireAuthentication();
        if (authentication.subject().identityType() != SubjectRef.IdentityType.INSTANCE_ROOT
                || !"root".equals(authentication.subject().subjectId())) {
            throw new IllegalArgumentException("ROOT_OPERATION_NOT_ALLOWED");
        }
        if (authentication.assurance().methods().contains(
                IamAuthenticationRuntimeOrchestrator.PASSWORD_CHANGE_REQUIRED_METHOD)) {
            throw new IllegalArgumentException("AUTH_PASSWORD_CHANGE_REQUIRED");
        }
        return authentication;
    }

    /**
     * MFA enrollment increments the Root principal security epoch. The old
     * restricted bootstrap session is therefore intentionally stale. Replace
     * it atomically so Step 2 can continue without an unauthenticated gap.
     */
    private SessionResponse createMfaBootstrapSession(
            AuthenticationContext authentication,
            IamApiRequestContext context) {
        LinkedHashSet<String> methods = new LinkedHashSet<>(authentication.assurance().methods());
        methods.remove(IamAuthenticationRuntimeOrchestrator.PASSWORD_CHANGE_REQUIRED_METHOD);
        methods.add("TOTP");
        methods.add(IamAuthenticationRuntimeOrchestrator.ROOT_BOOTSTRAP_REQUIRED_METHOD);
        BrowserSession session = sessions.create(
                new CreateBrowserSessionCommand(
                        CredentialSubjectType.INSTANCE_ROOT,
                        "root",
                        com.opensocket.aievent.core.iam.security.contract.TenantRef.instance(),
                        Set.copyOf(methods),
                        Optional.of(context.requestedAt()),
                        context.clientAddress(),
                        context.userAgent(),
                        context.correlationId(),
                        context.requestedAt()),
                SessionPolicy.rootRecovery());
        return new SessionResponse(
                session.sessionId(), session.subjectType().name(), session.subjectId(),
                session.tenant().tenantId(), session.methods(), session.createdAt(),
                session.lastSeenAt(), session.idleExpiresAt(), session.absoluteExpiresAt(),
                session.ipAddress(), session.userAgent(), session.status().name(), session.version());
    }

    private void revokeBootstrapSession(
            AuthenticationContext authentication,
            IamApiRequestContext context) {
        authentication.session().flatMap(session -> sessionRepository.find(session.sessionId()))
                .ifPresent(current -> sessionRepository.save(
                        current.revoke("root", "MFA_BOOTSTRAP_SESSION_UPGRADED", context.requestedAt()),
                        current.version()));
    }

    private static BootstrapStatusResponse status(RootBootstrapState state) {
        Set<String> done = new LinkedHashSet<>();
        if (state.passwordConfigured()) done.add("PASSWORD_CONFIGURED");
        if (state.mfaConfigured()) done.add("MFA_CONFIGURED");
        if (state.tenantCreated()) done.add("TENANT_CREATED");
        if (state.tenantAdminCreated()) done.add("TENANT_ADMIN_CREATED");
        return new BootstrapStatusResponse(
                state.status().name(), state.version(), done,
                state.status() != RootBootstrapState.Status.COMPLETED);
    }

    private static String event(String prefix, String key) {
        return UUID.nameUUIDFromBytes((prefix + ":" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static String resource(String prefix, String operation, String scope, String key) {
        return prefix + "-" + UUID.nameUUIDFromBytes(
                (operation + "|" + scope + "|" + key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
