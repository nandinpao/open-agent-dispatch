package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.api.application.port.IamFederationAdministrationApiPort;
import com.opensocket.aievent.core.iam.api.application.port.IamFederationAuthenticationApiPort;
import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.api.error.IamApiException;
import com.opensocket.aievent.core.iam.api.idempotency.IamIdempotencyExecutor;
import com.opensocket.aievent.core.iam.api.request.LinkExternalIdentityRequest;
import com.opensocket.aievent.core.iam.api.request.OidcStartRequest;
import com.opensocket.aievent.core.iam.api.request.UpdateFederationPolicyRequest;
import com.opensocket.aievent.core.iam.api.request.UpsertAuthenticationProviderRequest;
import com.opensocket.aievent.core.iam.api.response.AuthenticationProviderResponse;
import com.opensocket.aievent.core.iam.api.response.ExternalIdentityLinkResponse;
import com.opensocket.aievent.core.iam.api.response.FederatedSessionResponse;
import com.opensocket.aievent.core.iam.api.response.FederationPolicyResponse;
import com.opensocket.aievent.core.iam.api.response.FederationProviderPublicResponse;
import com.opensocket.aievent.core.iam.api.response.OidcStartResponse;
import com.opensocket.aievent.core.iam.api.response.SessionResponse;
import com.opensocket.aievent.core.iam.authentication.application.command.CreateBrowserSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.SessionCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SessionPolicyRepository;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;
import com.opensocket.aievent.core.iam.identity.application.command.ChangeHumanUserStatusCommand;
import com.opensocket.aievent.core.iam.identity.application.command.CreateHumanUserCommand;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityCommandPort;
import com.opensocket.aievent.core.iam.identity.application.port.in.IdentityQueryPort;
import com.opensocket.aievent.core.iam.identity.application.query.FindHumanUserQuery;
import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;
import com.opensocket.aievent.core.iam.identity.domain.HumanUser;
import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import com.opensocket.aievent.core.iam.organization.application.command.AddTenantMembershipCommand;
import com.opensocket.aievent.core.iam.organization.application.port.in.TenantCommandPort;
import com.opensocket.aievent.core.iam.organization.domain.MembershipStatus;
import com.opensocket.aievent.core.iam.organization.domain.TenantMembershipSource;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.runtime.config.IamFederationProperties;
import com.opensocket.aievent.core.iam.runtime.security.FederationStateCodec;
import com.opensocket.aievent.core.iam.runtime.security.OidcProtocolClient;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * P2.4 canonical federation runtime.
 *
 * <p>Federation authenticates one existing OpenDispatch Person. Tenant, Department, Group,
 * Role Binding and Resource Access remain OpenDispatch authority and are never imported from
 * IdP claims.</p>
 */
public final class IamFederationRuntimeOrchestrator
        implements IamFederationAdministrationApiPort, IamFederationAuthenticationApiPort {

    public static final String FEDERATED_TENANT_BOUND_METHOD = "FEDERATED_TENANT_BOUND";
    public static final String OIDC_METHOD = "OIDC";
    public static final String UPSTREAM_MFA_METHOD = "UPSTREAM_MFA";
    public static final String PROVIDER_METHOD_PREFIX = "FEDERATED_PROVIDER:";

    private final IamApiRuntimeDao dao;
    private final TransactionTemplate tx;
    private final IamFederationProperties properties;
    private final FederationStateCodec stateCodec;
    private final OidcProtocolClient oidc;
    private final IdentityQueryPort identityQueries;
    private final IdentityCommandPort identities;
    private final TenantCommandPort tenants;
    private final SessionCommandPort sessions;
    private final SessionPolicyRepository sessionPolicies;
    private final IamIdempotencyExecutor idempotency;
    private final Clock clock;

    public IamFederationRuntimeOrchestrator(
            IamApiRuntimeDao dao,
            TransactionTemplate tx,
            IamFederationProperties properties,
            FederationStateCodec stateCodec,
            OidcProtocolClient oidc,
            IdentityQueryPort identityQueries,
            IdentityCommandPort identities,
            TenantCommandPort tenants,
            SessionCommandPort sessions,
            SessionPolicyRepository sessionPolicies,
            IamIdempotencyExecutor idempotency,
            Clock clock) {
        this.dao = Objects.requireNonNull(dao);
        this.tx = Objects.requireNonNull(tx);
        this.properties = Objects.requireNonNull(properties);
        this.stateCodec = Objects.requireNonNull(stateCodec);
        this.oidc = Objects.requireNonNull(oidc);
        this.identityQueries = Objects.requireNonNull(identityQueries);
        this.identities = Objects.requireNonNull(identities);
        this.tenants = Objects.requireNonNull(tenants);
        this.sessions = Objects.requireNonNull(sessions);
        this.sessionPolicies = Objects.requireNonNull(sessionPolicies);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public List<AuthenticationProviderResponse> providers(String tenantId, IamApiRequestContext context) {
        requireActiveTenant(tenantId, context);
        return tenantRead(tenantId, context.actorId(), () -> dao.listAuthenticationProviders(tenantId).stream()
                .map(IamFederationRuntimeOrchestrator::providerResponse)
                .toList());
    }

    @Override
    public AuthenticationProviderResponse upsertProvider(
            String tenantId,
            UpsertAuthenticationProviderRequest request,
            long expectedVersion,
            IamApiRequestContext context) {
        requireActiveTenant(tenantId, context);
        context.requireAuditReason();
        String key = context.requireIdempotencyKey();
        ProviderInput checked = validateProvider(request);
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.federation.provider.upsert",
                key,
                request,
                expectedVersion == 0 ? 201 : 200,
                AuthenticationProviderResponse.class,
                () -> tenantRead(tenantId, context.actorId(), () -> {
                    Map<String, Object> row = providerRow(tenantId, checked, context);
                    int changed;
                    if (expectedVersion == 0) {
                        if (dao.findAuthenticationProvider(tenantId, checked.providerId()) != null) {
                            throw IamApiException.conflict(
                                    "AUTH_FEDERATION_PROVIDER_ID_CONFLICT",
                                    "That authentication provider ID already exists in this Tenant.");
                        }
                        changed = dao.insertAuthenticationProvider(row);
                    } else {
                        changed = dao.updateAuthenticationProvider(row, expectedVersion);
                    }
                    if (changed != 1) {
                        throw new IamOptimisticLockException(
                                "AuthenticationProvider", checked.providerId(), expectedVersion);
                    }
                    return providerResponse(requireProvider(tenantId, checked.providerId()));
                }));
    }

    @Override
    public AuthenticationProviderResponse changeProviderStatus(
            String tenantId,
            String providerId,
            String status,
            long expectedVersion,
            IamApiRequestContext context) {
        requireActiveTenant(tenantId, context);
        context.requireAuditReason();
        String key = context.requireIdempotencyKey();
        String target = normalizedEnum(status, Set.of("ACTIVE", "DISABLED"), "AUTH_FEDERATION_PROVIDER_STATUS_INVALID");
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.federation.provider.status",
                key,
                Map.of("providerId", providerId, "status", target, "expectedVersion", expectedVersion),
                200,
                AuthenticationProviderResponse.class,
                () -> tenantRead(tenantId, context.actorId(), () -> {
                    requireProvider(tenantId, providerId);
                    if (dao.changeAuthenticationProviderStatus(
                                    tenantId,
                                    required(providerId, "providerId", 128),
                                    target,
                                    context.requestedAt(),
                                    context.actorId(),
                                    expectedVersion)
                            != 1) {
                        throw new IamOptimisticLockException("AuthenticationProvider", providerId, expectedVersion);
                    }
                    return providerResponse(requireProvider(tenantId, providerId));
                }));
    }

    @Override
    public FederationPolicyResponse policy(String tenantId, IamApiRequestContext context) {
        requireActiveTenant(tenantId, context);
        return tenantRead(tenantId, context.actorId(), () -> policyResponse(requirePolicy(tenantId)));
    }

    @Override
    public FederationPolicyResponse updatePolicy(
            String tenantId,
            UpdateFederationPolicyRequest request,
            long expectedVersion,
            IamApiRequestContext context) {
        requireActiveTenant(tenantId, context);
        context.requireAuditReason();
        String key = context.requireIdempotencyKey();
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.federation.policy.update",
                key,
                request,
                200,
                FederationPolicyResponse.class,
                () -> tenantRead(tenantId, context.actorId(), () -> {
                    if (request.samlLoginEnabled()) {
                        throw IamApiException.badRequest(
                                "AUTH_FEDERATION_SAML_RUNTIME_NOT_ENABLED",
                                "SAML is reserved by the federation contract but is not enabled in the v17 OIDC runtime.");
                    }
                    if (!request.localLoginEnabled() && !request.oidcLoginEnabled()) {
                        throw IamApiException.badRequest(
                                "AUTH_FEDERATION_INTERACTIVE_LOGIN_REQUIRED",
                                "Keep Local login enabled or enable OIDC so Tenant users are not locked out.");
                    }
                    String defaultProvider = clean(request.defaultProviderId());
                    if (request.oidcLoginEnabled()) {
                        List<Map<String, Object>> activeOidc = dao.listAuthenticationProviders(tenantId).stream()
                                .filter(row -> "ACTIVE".equals(text(row, "status")))
                                .filter(row -> "OIDC".equals(text(row, "providerType")))
                                .toList();
                        if (activeOidc.isEmpty()) {
                            throw IamApiException.badRequest(
                                    "AUTH_FEDERATION_ACTIVE_OIDC_PROVIDER_REQUIRED",
                                    "Enable at least one OIDC provider before enabling OIDC login.");
                        }
                        if (!defaultProvider.isBlank()
                                && activeOidc.stream().noneMatch(row -> defaultProvider.equals(text(row, "providerId")))) {
                            throw IamApiException.badRequest(
                                    "AUTH_FEDERATION_DEFAULT_PROVIDER_INVALID",
                                    "The default provider must be an active OIDC provider in this Tenant.");
                        }
                    } else if (!defaultProvider.isBlank()) {
                        throw IamApiException.badRequest(
                                "AUTH_FEDERATION_DEFAULT_PROVIDER_INVALID",
                                "Remove the default provider when OIDC login is disabled.");
                    }
                    Map<String, Object> row = new HashMap<>();
                    row.put("tenantId", tenantId);
                    row.put("localLoginEnabled", request.localLoginEnabled());
                    row.put("oidcLoginEnabled", request.oidcLoginEnabled());
                    row.put("samlLoginEnabled", false);
                    row.put("providerDiscoveryEnabled", request.providerDiscoveryEnabled());
                    row.put("defaultProviderId", defaultProvider);
                    row.put("updatedAt", context.requestedAt());
                    row.put("updatedBy", context.actorId());
                    if (dao.updateFederationPolicy(row, expectedVersion) != 1) {
                        throw new IamOptimisticLockException("FederationPolicy", tenantId, expectedVersion);
                    }
                    return policyResponse(requirePolicy(tenantId));
                }));
    }

    @Override
    public List<ExternalIdentityLinkResponse> userLinks(
            String tenantId, String userId, IamApiRequestContext context) {
        requireActiveTenant(tenantId, context);
        requireUser(userId);
        return tenantRead(tenantId, context.actorId(), () -> dao.listUserExternalIdentityLinks(tenantId, userId).stream()
                .map(IamFederationRuntimeOrchestrator::externalLinkResponse)
                .toList());
    }

    @Override
    public ExternalIdentityLinkResponse linkUser(
            String tenantId,
            String userId,
            LinkExternalIdentityRequest request,
            IamApiRequestContext context) {
        requireActiveTenant(tenantId, context);
        context.requireAuditReason();
        String key = context.requireIdempotencyKey();
        return idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.federation.user-link.create",
                key,
                request,
                201,
                ExternalIdentityLinkResponse.class,
                () -> tenantRead(tenantId, context.actorId(), () -> {
                    HumanUser user = requireFederationLinkableUser(userId);
                    requireActiveFederatedTenantMembership(tenantId, user.userId().value(), context.requestedAt());
                    Map<String, Object> provider = requireProvider(tenantId, request.providerId());
                    requireActiveOidc(provider);
                    String subject = required(request.externalSubject(), "externalSubject", 320);
                    if (dao.findExternalCredentialLink(tenantId, request.providerId(), subject) != null) {
                        throw IamApiException.conflict(
                                "AUTH_FEDERATION_SUBJECT_ALREADY_LINKED",
                                "That external identity is already linked to an OpenDispatch Person.");
                    }
                    if (dao.findExternalCredentialLinkByUserProvider(tenantId, request.providerId(), userId) != null) {
                        throw IamApiException.conflict(
                                "AUTH_FEDERATION_USER_PROVIDER_ALREADY_LINKED",
                                "This Person already has an active identity link for that provider.");
                    }
                    Map<String, Object> row = externalLinkRow(
                            tenantId,
                            provider,
                            user,
                            subject,
                            request.upstreamUsername(),
                            request.upstreamEmail(),
                            request.upstreamEmailVerified(),
                            context.actorId(),
                            context.requestedAt(),
                            "ADMIN_EXPLICIT_LINK");
                    if (dao.insertExternalCredentialLink(row) != 1) {
                        throw IamApiException.conflict(
                                "AUTH_FEDERATION_LINK_CONFLICT",
                                "The external identity link could not be created because another link now owns that subject.");
                    }
                    return externalLinkResponse(requireExternalLink(tenantId, text(row, "credentialLinkId")));
                }));
    }

    @Override
    public void unlinkUser(
            String tenantId,
            String userId,
            String credentialLinkId,
            long expectedVersion,
            IamApiRequestContext context) {
        requireActiveTenant(tenantId, context);
        context.requireAuditReason();
        String key = context.requireIdempotencyKey();
        idempotency.execute(
                tenantId,
                context.actorId(),
                "identity.federation.user-link.disable",
                key,
                Map.of("userId", userId, "credentialLinkId", credentialLinkId, "expectedVersion", expectedVersion),
                204,
                String.class,
                () -> tenantRead(tenantId, context.actorId(), () -> {
                    Map<String, Object> link = requireExternalLink(tenantId, credentialLinkId);
                    if (!userId.equals(text(link, "userId"))) {
                        throw IamApiException.notFound(
                                "AUTH_FEDERATION_LINK_NOT_FOUND",
                                "That external identity link is not attached to this Person.");
                    }
                    if (dao.disableExternalCredentialLink(
                                    tenantId,
                                    credentialLinkId,
                                    context.requestedAt(),
                                    context.actorId(),
                                    expectedVersion)
                            != 1) {
                        throw new IamOptimisticLockException("ExternalIdentityLink", credentialLinkId, expectedVersion);
                    }
                    return "OK";
                }));
    }

    @Override
    public List<FederationProviderPublicResponse> publicProviders(
            String tenant, IamApiRequestContext context) {
        if (!properties.isEnabled()) return List.of();
        Map<String, Object> tenantRow = resolveTenant(tenant);
        String tenantId = text(tenantRow, "tenantId");
        return tenantRead(tenantId, "federation-discovery", () -> {
            Map<String, Object> policy = requirePolicy(tenantId);
            if (!bool(policy, "oidcLoginEnabled") || !bool(policy, "providerDiscoveryEnabled")) return List.of();
            String defaultProviderId = text(policy, "defaultProviderId");
            return dao.listAuthenticationProviders(tenantId).stream()
                    .filter(row -> "ACTIVE".equals(text(row, "status")))
                    .filter(row -> "OIDC".equals(text(row, "providerType")))
                    .map(row -> new FederationProviderPublicResponse(
                            tenantId,
                            text(row, "providerId"),
                            text(row, "providerCode"),
                            text(row, "providerType"),
                            text(row, "displayName"),
                            text(row, "providerId").equals(defaultProviderId),
                            "REQUIRE_ASSERTED".equals(text(row, "upstreamMfaMode"))))
                    .toList();
        });
    }

    @Override
    public OidcStartResponse startOidc(OidcStartRequest request, IamApiRequestContext context) {
        requireFederationEnabled();
        Map<String, Object> tenantRow = resolveTenant(request.tenantId());
        String tenantId = text(tenantRow, "tenantId");
        String providerId = required(request.providerId(), "providerId", 128);
        String returnTo = safeReturnTo(request.returnTo());
        Instant issued = context.requestedAt();
        Instant expires = issued.plus(properties.getLoginAttemptTtl());
        String state = stateCodec.newState();
        String nonce = stateCodec.nonce(state);
        String verifier = stateCodec.codeVerifier(state);
        String challenge = stateCodec.codeChallenge(state);

        Map<String, Object> provider = tenantRead(tenantId, "federation-start", () -> {
            Map<String, Object> policy = requirePolicy(tenantId);
            if (!bool(policy, "oidcLoginEnabled")) {
                throw IamApiException.forbidden(
                        "AUTH_FEDERATION_OIDC_DISABLED",
                        "OIDC sign-in is disabled for this Tenant.",
                        "");
            }
            Map<String, Object> row = requireProvider(tenantId, providerId);
            requireActiveOidc(row);
            Map<String, Object> attempt = new HashMap<>();
            attempt.put("tenantId", tenantId);
            attempt.put("attemptId", UUID.randomUUID().toString());
            attempt.put("providerId", providerId);
            attempt.put("stateHash", stateCodec.stateHash(state));
            attempt.put("nonceHash", stateCodec.nonceHash(state));
            attempt.put("redirectPath", returnTo);
            attempt.put("issuedAt", issued);
            attempt.put("expiresAt", expires);
            attempt.put("correlationId", context.correlationId());
            if (dao.insertOidcLoginAttempt(attempt) != 1) {
                throw new IllegalStateException("AUTH_FEDERATION_LOGIN_ATTEMPT_CREATE_FAILED");
            }
            return row;
        });
        OidcProtocolClient.Provider config = oidcProvider(provider);
        String authorizationUrl = oidc.authorizationUrl(
                config,
                properties.getOidcCallbackUrl(),
                state,
                nonce,
                challenge);
        // verifier is deterministically derived from state and intentionally not persisted.
        if (verifier.isBlank()) throw new IllegalStateException("AUTH_FEDERATION_PKCE_FAILED");
        return new OidcStartResponse(authorizationUrl, providerId, text(provider, "displayName"), expires);
    }

    @Override
    public FederatedSessionResponse completeOidc(
            String code, String state, IamApiRequestContext context) {
        requireFederationEnabled();
        String stateHash = stateCodec.stateHash(state);
        Map<String, Object> attempt = instanceRead("federation-callback", () -> dao.findOidcLoginAttemptByStateHash(stateHash));
        if (attempt == null) {
            throw IamApiException.unauthorized(
                    "AUTH_FEDERATION_STATE_INVALID",
                    "The federation sign-in state is invalid or no longer available.");
        }
        String tenantId = text(attempt, "tenantId");
        String attemptId = text(attempt, "attemptId");
        long attemptVersion = longValue(attempt, "version");
        Instant now = context.requestedAt();
        if (!"PENDING".equals(text(attempt, "status")) || !now.isBefore(instant(attempt, "expiresAt"))) {
            failAttempt(tenantId, attemptId, attemptVersion, now, "AUTH_FEDERATION_STATE_EXPIRED");
            throw IamApiException.unauthorized(
                    "AUTH_FEDERATION_STATE_EXPIRED",
                    "The federation sign-in attempt expired. Start sign-in again.");
        }
        if (!Objects.equals(stateCodec.nonceHash(state), text(attempt, "nonceHash"))) {
            failAttempt(tenantId, attemptId, attemptVersion, now, "AUTH_FEDERATION_NONCE_MISMATCH");
            throw IamApiException.unauthorized(
                    "AUTH_FEDERATION_NONCE_MISMATCH",
                    "The federation sign-in nonce did not match the issued attempt.");
        }

        OidcProtocolClient.Provider provider = oidcProvider(attempt);
        OidcProtocolClient.IdentityEvidence evidence;
        try {
            evidence = oidc.exchange(
                    provider,
                    properties.getOidcCallbackUrl(),
                    code,
                    stateCodec.codeVerifier(state),
                    stateCodec.nonce(state));
        } catch (RuntimeException ex) {
            String failureCode = errorCode(ex, "AUTH_FEDERATION_TOKEN_EXCHANGE_FAILED");
            failAttempt(tenantId, attemptId, attemptVersion, now, failureCode);
            if (ex instanceof IamApiException iam) throw iam;
            if (failureCode.endsWith("MISMATCH")
                    || failureCode.endsWith("MISSING")
                    || failureCode.contains("ID_TOKEN")
                    || failureCode.contains("SUBJECT")) {
                throw IamApiException.unauthorized(
                        failureCode,
                        "Enterprise sign-in evidence from the identity provider could not be validated. Start sign-in again.");
            }
            throw new IamApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    failureCode,
                    "The enterprise identity provider could not complete sign-in. Verify provider connectivity and configuration, then retry.",
                    "");
        }
        if ("REQUIRE_ASSERTED".equals(text(attempt, "upstreamMfaMode")) && !evidence.mfaAsserted()) {
            failAttempt(tenantId, attemptId, attemptVersion, now, "AUTH_FEDERATION_UPSTREAM_MFA_REQUIRED");
            throw IamApiException.unauthorized(
                    "AUTH_FEDERATION_UPSTREAM_MFA_REQUIRED",
                    "This Tenant requires the identity provider to assert an approved MFA method.");
        }

        FederatedLogin login = tenantRead(tenantId, "federation-callback", () -> {
            Map<String, Object> policy = requirePolicy(tenantId);
            if (!bool(policy, "oidcLoginEnabled")) {
                throw IamApiException.forbidden(
                        "AUTH_FEDERATION_OIDC_DISABLED",
                        "OIDC sign-in is disabled for this Tenant.",
                        "");
            }
            Map<String, Object> currentProvider = requireProvider(tenantId, provider.providerId());
            requireActiveOidc(currentProvider);
            Map<String, Object> link = resolveOrCreateLink(tenantId, currentProvider, evidence, context);
            String linkedUserId = text(link, "userId");
            requireActiveFederatedTenantMembership(tenantId, linkedUserId, now);
            HumanUser user = activateFederatedUserIfEligible(linkedUserId, currentProvider, evidence, context);
            if (user.userId().value().equalsIgnoreCase("root")) {
                throw IamApiException.forbidden(
                        "AUTH_FEDERATION_ROOT_FORBIDDEN",
                        "The instance Root identity is local/break-glass only and cannot use federation.",
                        "");
            }
            if (dao.updateExternalCredentialLinkAuthentication(
                            tenantId,
                            text(link, "credentialLinkId"),
                            now,
                            evidence.username(),
                            evidence.email(),
                            evidence.emailVerified(),
                            "federation:" + provider.providerId(),
                            longValue(link, "version"))
                    != 1) {
                throw new IamOptimisticLockException(
                        "ExternalIdentityLink",
                        text(link, "credentialLinkId"),
                        longValue(link, "version"));
            }
            if (dao.consumeOidcLoginAttempt(tenantId, attemptId, now, attemptVersion) != 1) {
                throw IamApiException.unauthorized(
                        "AUTH_FEDERATION_STATE_REPLAYED",
                        "The federation sign-in state has already been consumed. Start sign-in again.");
            }
            return new FederatedLogin(user.userId().value(), text(attempt, "redirectPath"));
        });

        Set<String> methods = new LinkedHashSet<>();
        methods.add(OIDC_METHOD);
        methods.add(FEDERATED_TENANT_BOUND_METHOD);
        methods.add(PROVIDER_METHOD_PREFIX + provider.providerId());
        Optional<Instant> mfaAt = Optional.empty();
        if (evidence.mfaAsserted()) {
            methods.add(UPSTREAM_MFA_METHOD);
            mfaAt = Optional.of(now);
        }
        SessionResponse session = createFederatedSession(
                tenantId,
                login.userId(),
                Set.copyOf(methods),
                mfaAt,
                context);
        return new FederatedSessionResponse(session, safeReturnTo(login.returnTo()));
    }

    /** Used by local-password login and Tenant switching to enforce per-Tenant federation policy. */
    public void requireLocalLoginAllowed(String tenantId) {
        if (!properties.isEnabled() || tenantId == null || tenantId.isBlank()) return;
        boolean allowed = tenantRead(tenantId, "local-login-policy", () -> bool(requirePolicy(tenantId), "localLoginEnabled"));
        if (!allowed) {
            throw IamApiException.forbidden(
                    "AUTH_LOCAL_LOGIN_DISABLED",
                    "Local password sign-in is disabled for this Tenant. Use an approved enterprise sign-in provider.",
                    "");
        }
    }

    private Map<String, Object> resolveOrCreateLink(
            String tenantId,
            Map<String, Object> provider,
            OidcProtocolClient.IdentityEvidence evidence,
            IamApiRequestContext context) {
        Map<String, Object> existing = dao.findExternalCredentialLink(tenantId, text(provider, "providerId"), evidence.subject());
        if (existing != null) return existing;

        String linkMode = text(provider, "linkMode");
        if ("VERIFIED_EMAIL_EXISTING_USER".equals(linkMode)
                && Boolean.TRUE.equals(evidence.emailVerified())
                && !clean(evidence.email()).isBlank()) {
            Map<String, Object> matched = dao.findTenantUserByNormalizedEmail(
                    tenantId, clean(evidence.email()).toLowerCase(Locale.ROOT));
            if (matched != null) {
                HumanUser user = requireFederationLinkableUser(text(matched, "userId"));
                return insertAutomaticLink(tenantId, provider, user, evidence, context, "VERIFIED_EMAIL_LINK");
            }
        }

        if ("CREATE_ACTIVE_SSO_USER".equals(text(provider, "jitMode"))) {
            if (!Boolean.TRUE.equals(evidence.emailVerified()) || clean(evidence.email()).isBlank()) {
                throw IamApiException.unauthorized(
                        "AUTH_FEDERATION_JIT_VERIFIED_EMAIL_REQUIRED",
                        "JIT federation provisioning requires a verified upstream email address.");
            }
            HumanUser user = createJitUser(tenantId, provider, evidence, context);
            return insertAutomaticLink(tenantId, provider, user, evidence, context, "OIDC_JIT_PROVISIONING");
        }

        throw IamApiException.unauthorized(
                "AUTH_FEDERATION_IDENTITY_NOT_LINKED",
                "This external identity is not linked to an active Person in this Tenant. Ask an administrator to link the identity first.");
    }

    private HumanUser createJitUser(
            String tenantId,
            Map<String, Object> provider,
            OidcProtocolClient.IdentityEvidence evidence,
            IamApiRequestContext context) {
        String providerId = text(provider, "providerId");
        String actor = "federation:" + providerId;
        String userId = UUID.randomUUID().toString();
        String username = jitUsername(text(provider, "providerCode"), evidence.subject(), evidence.username(), evidence.email());
        String displayName = clean(evidence.displayName());
        if (displayName.isBlank()) displayName = !clean(evidence.username()).isBlank() ? clean(evidence.username()) : clean(evidence.email());
        HumanUser created = identities.createHumanUser(new CreateHumanUserCommand(
                userId,
                username,
                evidence.email(),
                displayName,
                UserCreationMode.ADMIN_CREATED,
                actor,
                context.correlationId(),
                UUID.randomUUID().toString()));
        HumanUser active = identities.changeHumanUserStatus(new ChangeHumanUserStatusCommand(
                userId,
                AccountStatus.ACTIVE,
                created.version(),
                "Federated JIT identity verified by active OIDC provider",
                actor,
                context.correlationId(),
                UUID.randomUUID().toString()));
        tenants.addTenantMembership(new AddTenantMembershipCommand(
                UUID.randomUUID().toString(),
                tenantId,
                userId,
                "",
                null,
                MembershipStatus.ACTIVE,
                false,
                TenantMembershipSource.PLATFORM_PROVISIONING,
                "Federated JIT Tenant admission",
                actor,
                context.correlationId(),
                UUID.randomUUID().toString()));
        return active;
    }

    private Map<String, Object> insertAutomaticLink(
            String tenantId,
            Map<String, Object> provider,
            HumanUser user,
            OidcProtocolClient.IdentityEvidence evidence,
            IamApiRequestContext context,
            String source) {
        Map<String, Object> row = externalLinkRow(
                tenantId,
                provider,
                user,
                evidence.subject(),
                evidence.username(),
                evidence.email(),
                Boolean.TRUE.equals(evidence.emailVerified()),
                "federation:" + text(provider, "providerId"),
                context.requestedAt(),
                source);
        if (dao.insertExternalCredentialLink(row) != 1) {
            Map<String, Object> concurrent = dao.findExternalCredentialLink(
                    tenantId, text(provider, "providerId"), evidence.subject());
            if (concurrent != null) return concurrent;
            throw IamApiException.conflict(
                    "AUTH_FEDERATION_LINK_CONFLICT",
                    "The external identity link changed while sign-in was being processed. Start sign-in again.");
        }
        return requireExternalLink(tenantId, text(row, "credentialLinkId"));
    }

    private SessionResponse createFederatedSession(
            String tenantId,
            String userId,
            Set<String> methods,
            Optional<Instant> mfaAt,
            IamApiRequestContext context) {
        SessionPolicy policy = tenantRead(tenantId, userId, () -> sessionPolicies.findTenantPolicy(tenantId)
                .orElseGet(sessionPolicies::instanceMinimum));
        BrowserSession session = tenantRead(tenantId, userId, () -> sessions.create(
                new CreateBrowserSessionCommand(
                        CredentialSubjectType.HUMAN_USER,
                        userId,
                        TenantRef.tenant(tenantId),
                        methods,
                        mfaAt,
                        context.clientAddress(),
                        context.userAgent(),
                        context.correlationId(),
                        context.requestedAt()),
                policy));
        return new SessionResponse(
                session.sessionId(),
                session.subjectType().name(),
                session.subjectId(),
                session.tenant().tenantId(),
                session.methods(),
                session.createdAt(),
                session.lastSeenAt(),
                session.idleExpiresAt(),
                session.absoluteExpiresAt(),
                session.ipAddress(),
                session.userAgent(),
                session.status().name(),
                session.version());
    }

    private ProviderInput validateProvider(UpsertAuthenticationProviderRequest request) {
        String type = normalizedEnum(request.providerType(), Set.of("OIDC"), "AUTH_FEDERATION_PROVIDER_TYPE_UNSUPPORTED");
        String issuer = normalizeIssuer(request.issuerUri());
        String clientSecretRef = required(request.clientSecretRef(), "clientSecretRef", 320);
        if (!(clientSecretRef.startsWith("env:") || clientSecretRef.startsWith("property:"))) {
            throw IamApiException.badRequest(
                    "AUTH_FEDERATION_CLIENT_SECRET_REFERENCE_REQUIRED",
                    "Store the OIDC client secret outside the database and use an env: or property: secret reference.");
        }
        List<String> scopes = normalizeList(request.scopes());
        if (scopes.stream().noneMatch(value -> "openid".equalsIgnoreCase(value))) {
            throw IamApiException.badRequest(
                    "AUTH_FEDERATION_OPENID_SCOPE_REQUIRED",
                    "OIDC providers must request the openid scope.");
        }
        if (request.authorizationEnabled()) {
            throw IamApiException.badRequest(
                    "AUTH_FEDERATION_EXTERNAL_AUTHORIZATION_FORBIDDEN",
                    "External provider claims may authenticate a Person but cannot grant OpenDispatch roles or permissions.");
        }
        return new ProviderInput(
                required(request.providerId(), "providerId", 128),
                required(request.providerCode(), "providerCode", 80).toLowerCase(Locale.ROOT),
                type,
                required(request.displayName(), "displayName", 160),
                issuer,
                required(request.clientId(), "clientId", 320),
                clientSecretRef,
                scopes,
                defaulted(request.subjectClaim(), "sub"),
                defaulted(request.usernameClaim(), "preferred_username"),
                defaulted(request.emailClaim(), "email"),
                defaulted(request.displayNameClaim(), "name"),
                defaulted(request.amrClaim(), "amr"),
                defaulted(request.acrClaim(), "acr"),
                normalizedEnum(request.upstreamMfaMode(), Set.of("NONE", "TRUST_AMR", "TRUST_ACR", "TRUST_AMR_OR_ACR", "REQUIRE_ASSERTED"), "AUTH_FEDERATION_MFA_MODE_INVALID"),
                normalizeList(request.trustedAmrValues()),
                normalizeList(request.trustedAcrValues()),
                normalizedEnum(request.linkMode(), Set.of("EXPLICIT_ONLY", "VERIFIED_EMAIL_EXISTING_USER"), "AUTH_FEDERATION_LINK_MODE_INVALID"),
                normalizedEnum(request.jitMode(), Set.of("DISABLED", "CREATE_ACTIVE_SSO_USER"), "AUTH_FEDERATION_JIT_MODE_INVALID"));
    }

    private Map<String, Object> providerRow(String tenantId, ProviderInput value, IamApiRequestContext context) {
        Map<String, Object> row = new HashMap<>();
        row.put("tenantId", tenantId);
        row.put("providerId", value.providerId());
        row.put("providerCode", value.providerCode());
        row.put("providerType", value.providerType());
        row.put("displayName", value.displayName());
        row.put("issuerUri", value.issuerUri());
        row.put("clientId", value.clientId());
        row.put("clientSecretRef", value.clientSecretRef());
        row.put("scopesCsv", csv(value.scopes()));
        row.put("subjectClaim", value.subjectClaim());
        row.put("usernameClaim", value.usernameClaim());
        row.put("emailClaim", value.emailClaim());
        row.put("displayNameClaim", value.displayNameClaim());
        row.put("amrClaim", value.amrClaim());
        row.put("acrClaim", value.acrClaim());
        row.put("upstreamMfaMode", value.upstreamMfaMode());
        row.put("trustedAmrValuesCsv", csv(value.trustedAmrValues()));
        row.put("trustedAcrValuesCsv", csv(value.trustedAcrValues()));
        row.put("linkMode", value.linkMode());
        row.put("jitMode", value.jitMode());
        row.put("authorizationEnabled", false);
        row.put("updatedAt", context.requestedAt());
        row.put("updatedBy", context.actorId());
        return row;
    }

    private Map<String, Object> externalLinkRow(
            String tenantId,
            Map<String, Object> provider,
            HumanUser user,
            String subject,
            String username,
            String email,
            boolean emailVerified,
            String actor,
            Instant at,
            String source) {
        Map<String, Object> row = new HashMap<>();
        row.put("credentialLinkId", UUID.randomUUID().toString());
        row.put("tenantId", tenantId);
        row.put("providerId", text(provider, "providerId"));
        row.put("providerType", text(provider, "providerType"));
        row.put("issuerUri", text(provider, "issuerUri"));
        row.put("externalSubject", required(subject, "externalSubject", 320));
        row.put("userId", user.userId().value());
        row.put("canonicalUsername", user.username().value());
        row.put("sourceReference", source);
        row.put("lastAuthenticatedAt", at);
        row.put("createdAt", at);
        row.put("updatedBy", actor);
        row.put("upstreamUsername", clean(username));
        row.put("upstreamEmail", clean(email));
        row.put("upstreamEmailVerified", emailVerified);
        return row;
    }

    private OidcProtocolClient.Provider oidcProvider(Map<String, Object> row) {
        return new OidcProtocolClient.Provider(
                text(row, "tenantId"),
                text(row, "providerId"),
                text(row, "providerCode"),
                text(row, "displayName"),
                text(row, "issuerUri"),
                text(row, "clientId"),
                text(row, "clientSecretRef"),
                split(row.get("scopesCsv")),
                text(row, "subjectClaim"),
                text(row, "usernameClaim"),
                text(row, "emailClaim"),
                text(row, "displayNameClaim"),
                text(row, "amrClaim"),
                text(row, "acrClaim"),
                text(row, "upstreamMfaMode"),
                split(row.get("trustedAmrValuesCsv")),
                split(row.get("trustedAcrValuesCsv")),
                text(row, "linkMode"),
                text(row, "jitMode"));
    }

    private static AuthenticationProviderResponse providerResponse(Map<String, Object> row) {
        return new AuthenticationProviderResponse(
                text(row, "tenantId"), text(row, "providerId"), text(row, "providerCode"), text(row, "providerType"),
                text(row, "displayName"), text(row, "status"), text(row, "issuerUri"), text(row, "clientId"),
                text(row, "clientSecretRef"), split(row.get("scopesCsv")), text(row, "subjectClaim"),
                text(row, "usernameClaim"), text(row, "emailClaim"), text(row, "displayNameClaim"), text(row, "amrClaim"),
                text(row, "acrClaim"), text(row, "upstreamMfaMode"), split(row.get("trustedAmrValuesCsv")),
                split(row.get("trustedAcrValuesCsv")), text(row, "linkMode"), text(row, "jitMode"),
                bool(row, "authorizationEnabled"), longValue(row, "version"));
    }

    private static FederationPolicyResponse policyResponse(Map<String, Object> row) {
        return new FederationPolicyResponse(
                text(row, "tenantId"), bool(row, "localLoginEnabled"), bool(row, "oidcLoginEnabled"),
                bool(row, "samlLoginEnabled"), bool(row, "providerDiscoveryEnabled"), text(row, "defaultProviderId"),
                longValue(row, "version"));
    }

    private static ExternalIdentityLinkResponse externalLinkResponse(Map<String, Object> row) {
        return new ExternalIdentityLinkResponse(
                text(row, "credentialLinkId"), text(row, "tenantId"), text(row, "providerId"), text(row, "providerCode"),
                text(row, "providerType"), text(row, "issuerUri"), text(row, "externalSubject"), text(row, "upstreamUsername"),
                text(row, "upstreamEmail"), nullableBoolean(row, "upstreamEmailVerified"), text(row, "userId"),
                text(row, "canonicalUsername"), text(row, "status"), nullableInstant(row, "lastAuthenticatedAt"),
                longValue(row, "version"));
    }

    private Map<String, Object> resolveTenant(String tenant) {
        String value = required(tenant, "tenant", 128);
        Map<String, Object> row = instanceRead("federation-tenant-resolution", () -> dao.resolveFederationTenant(value));
        if (row == null) {
            throw IamApiException.notFound(
                    "AUTH_FEDERATION_TENANT_NOT_FOUND",
                    "No active Tenant matches that workspace code or ID.");
        }
        return row;
    }

    private Map<String, Object> requirePolicy(String tenantId) {
        Map<String, Object> row = dao.findFederationPolicy(tenantId);
        if (row == null) throw new IllegalStateException("AUTH_FEDERATION_POLICY_MISSING");
        return row;
    }

    private Map<String, Object> requireProvider(String tenantId, String providerId) {
        Map<String, Object> row = dao.findAuthenticationProvider(tenantId, required(providerId, "providerId", 128));
        if (row == null) {
            throw IamApiException.notFound(
                    "AUTH_FEDERATION_PROVIDER_NOT_FOUND",
                    "That authentication provider does not exist in this Tenant.");
        }
        return row;
    }

    private Map<String, Object> requireExternalLink(String tenantId, String credentialLinkId) {
        Map<String, Object> row = dao.findExternalCredentialLinkById(tenantId, required(credentialLinkId, "credentialLinkId", 128));
        if (row == null) {
            throw IamApiException.notFound(
                    "AUTH_FEDERATION_LINK_NOT_FOUND",
                    "That external identity link does not exist.");
        }
        return row;
    }

    private HumanUser requireUser(String userId) {
        return identityQueries.findHumanUser(new FindHumanUserQuery(required(userId, "userId", 128)))
                .orElseThrow(() -> IamApiException.notFound("IDENTITY_USER_NOT_FOUND", "That Person does not exist."));
    }

    private HumanUser requireFederationLinkableUser(String userId) {
        HumanUser user = requireUser(userId);
        if (!Set.of(
                        AccountStatus.ACTIVE,
                        AccountStatus.PENDING_ACTIVATION,
                        AccountStatus.PASSWORD_RESET_REQUIRED,
                        AccountStatus.MFA_ENROLLMENT_REQUIRED)
                .contains(user.status())) {
            throw IamApiException.forbidden(
                    "AUTH_FEDERATION_ACCOUNT_NOT_LINKABLE",
                    "This Person is suspended, disabled, locked, or deleted and cannot be linked to enterprise sign-in.",
                    "");
        }
        return user;
    }

    private HumanUser activateFederatedUserIfEligible(
            String userId,
            Map<String, Object> provider,
            OidcProtocolClient.IdentityEvidence evidence,
            IamApiRequestContext context) {
        HumanUser user = requireFederationLinkableUser(userId);
        if (user.status() == AccountStatus.ACTIVE) return user;
        if ("REQUIRE_ASSERTED".equals(text(provider, "upstreamMfaMode")) && !evidence.mfaAsserted()) {
            throw IamApiException.unauthorized(
                    "AUTH_FEDERATION_UPSTREAM_MFA_REQUIRED",
                    "This Tenant requires the identity provider to assert an approved MFA method.");
        }
        return identities.changeHumanUserStatus(new ChangeHumanUserStatusCommand(
                user.userId().value(),
                AccountStatus.ACTIVE,
                user.version(),
                "Enterprise identity proof completed account activation",
                "federation:" + text(provider, "providerId"),
                context.correlationId(),
                UUID.randomUUID().toString()));
    }

    private void requireActiveFederatedTenantMembership(String tenantId, String userId, Instant at) {
        if (dao.countActiveFederatedTenantMembership(tenantId, userId, at) != 1) {
            throw IamApiException.forbidden(
                    "AUTH_TENANT_MEMBERSHIP_REQUIRED",
                    "An active Tenant membership is required for enterprise sign-in. Ask an administrator to activate workspace access first.",
                    "");
        }
    }

    private HumanUser requireActiveUser(String userId) {
        HumanUser user = requireUser(userId);
        if (user.status() != AccountStatus.ACTIVE) {
            throw IamApiException.forbidden(
                    "AUTH_FEDERATION_ACCOUNT_NOT_ACTIVE",
                    "The linked OpenDispatch Person is not active. Resolve the account status before enterprise sign-in.",
                    "");
        }
        return user;
    }

    private static void requireActiveOidc(Map<String, Object> provider) {
        if (!"ACTIVE".equals(text(provider, "status"))) {
            throw IamApiException.forbidden(
                    "AUTH_FEDERATION_PROVIDER_DISABLED",
                    "That enterprise sign-in provider is disabled.",
                    "");
        }
        if (!"OIDC".equals(text(provider, "providerType"))) {
            throw IamApiException.badRequest(
                    "AUTH_FEDERATION_PROVIDER_TYPE_UNSUPPORTED",
                    "The v17 federation runtime currently supports OIDC providers only.");
        }
        if (bool(provider, "authorizationEnabled")) {
            throw new IllegalStateException("AUTH_FEDERATION_EXTERNAL_AUTHORIZATION_FORBIDDEN");
        }
    }

    private void requireFederationEnabled() {
        if (!properties.isEnabled()) {
            throw IamApiException.notFound(
                    "AUTH_FEDERATION_DISABLED",
                    "Enterprise sign-in is not enabled on this OpenDispatch instance.");
        }
    }

    private static void requireActiveTenant(String tenantId, IamApiRequestContext context) {
        if (!required(tenantId, "tenantId", 128).equals(context.activeTenantId())) {
            throw IamApiException.forbidden(
                    "AUTH_TENANT_MISMATCH",
                    "The requested Tenant does not match the active authenticated Tenant.",
                    "");
        }
    }

    private void failAttempt(String tenantId, String attemptId, long expectedVersion, Instant at, String code) {
        try {
            tenantRead(tenantId, "federation-failure", () -> {
                dao.failOidcLoginAttempt(tenantId, attemptId, at, code, expectedVersion);
                return null;
            });
        } catch (RuntimeException ignored) {
            // Failure evidence is best effort. Never replace the authentication failure with an audit-write failure.
        }
    }

    private <T> T tenantRead(String tenantId, String actor, Supplier<T> work) {
        return IamTenantContextHolder.withContext(
                new IamTenantExecutionContext(tenantId, actor == null || actor.isBlank() ? "federation" : actor),
                () -> tx.execute(status -> work.get()));
    }

    private <T> T instanceRead(String actor, Supplier<T> work) {
        return IamTenantContextHolder.withContext(
                new IamTenantExecutionContext("INSTANCE", actor),
                () -> tx.execute(status -> work.get()));
    }

    private static String safeReturnTo(String value) {
        String path = clean(value);
        if (path.isBlank()) return "/";
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.contains("\r") || path.contains("\n")) {
            throw IamApiException.badRequest(
                    "AUTH_FEDERATION_RETURN_PATH_INVALID",
                    "Federated sign-in can return only to a local OpenDispatch path.");
        }
        return path.length() > 512 ? "/" : path;
    }

    private static String normalizeIssuer(String value) {
        String issuer = required(value, "issuerUri", 1024);
        URI uri;
        try {
            uri = URI.create(issuer);
        } catch (IllegalArgumentException ex) {
            throw IamApiException.badRequest("AUTH_FEDERATION_ISSUER_INVALID", "OIDC issuer URI is invalid.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean localhost = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!("https".equals(scheme) || ("http".equals(scheme) && localhost))) {
            throw IamApiException.badRequest(
                    "AUTH_FEDERATION_ISSUER_HTTPS_REQUIRED",
                    "OIDC issuer must use HTTPS. HTTP is allowed only for localhost development providers.");
        }
        while (issuer.endsWith("/")) issuer = issuer.substring(0, issuer.length() - 1);
        return issuer;
    }

    private static String jitUsername(String providerCode, String subject, String upstreamUsername, String email) {
        String candidate = clean(upstreamUsername);
        if (candidate.isBlank() && !clean(email).isBlank()) candidate = clean(email).split("@", 2)[0];
        candidate = candidate.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        candidate = candidate.replaceAll("-+", "-");
        if (candidate.length() > 40) candidate = candidate.substring(0, 40);
        String suffix = UUID.nameUUIDFromBytes((providerCode + ":" + subject).getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 8);
        String prefix = candidate.isBlank() ? "sso" : candidate;
        return prefix + "-" + suffix;
    }

    private static String csv(List<String> values) {
        return String.join(String.valueOf((char) 31), values == null ? List.of() : values);
    }

    private static List<String> split(Object value) {
        if (value == null) return List.of();
        String text = String.valueOf(value);
        if (text.isBlank()) return List.of();
        return List.of(text.split(String.valueOf((char) 31), -1)).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .map(IamFederationRuntimeOrchestrator::clean)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static String normalizedEnum(String value, Set<String> allowed, String code) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw IamApiException.badRequest(code, "Unsupported federation option: " + normalized);
        }
        return normalized;
    }

    private static String defaulted(String value, String fallback) {
        String normalized = clean(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String required(String value, String field, int max) {
        String normalized = clean(value);
        if (normalized.isBlank()) throw IamApiException.badRequest("IAM_REQUEST_REJECTED", field + " is required");
        if (normalized.length() > max) throw IamApiException.badRequest("IAM_REQUEST_REJECTED", field + " exceeds " + max + " characters");
        return normalized;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String text(Map<String, Object> row, String key) { Object value = row == null ? null : row.get(key); return value == null ? "" : String.valueOf(value).trim(); }
    private static boolean bool(Map<String, Object> row, String key) { Object value = row == null ? null : row.get(key); return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value)); }
    private static Boolean nullableBoolean(Map<String, Object> row, String key) { Object value = row == null ? null : row.get(key); if (value == null) return null; return value instanceof Boolean b ? b : Boolean.valueOf(String.valueOf(value)); }
    private static long longValue(Map<String, Object> row, String key) { Object value = row == null ? null : row.get(key); return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value)); }
    private static Instant instant(Map<String, Object> row, String key) { Object value = row == null ? null : row.get(key); if (value instanceof Instant i) return i; return Instant.parse(String.valueOf(value)); }
    private static Instant nullableInstant(Map<String, Object> row, String key) { Object value = row == null ? null : row.get(key); if (value == null) return null; return value instanceof Instant i ? i : Instant.parse(String.valueOf(value)); }
    private static String errorCode(Throwable error, String fallback) {
        String message = error == null ? "" : clean(error.getMessage());
        if (message.startsWith("AUTH_")) {
            int colon = message.indexOf(':');
            return colon > 0 ? message.substring(0, colon) : message;
        }
        return fallback;
    }

    private record ProviderInput(
            String providerId,
            String providerCode,
            String providerType,
            String displayName,
            String issuerUri,
            String clientId,
            String clientSecretRef,
            List<String> scopes,
            String subjectClaim,
            String usernameClaim,
            String emailClaim,
            String displayNameClaim,
            String amrClaim,
            String acrClaim,
            String upstreamMfaMode,
            List<String> trustedAmrValues,
            List<String> trustedAcrValues,
            String linkMode,
            String jitMode) {}

    private record FederatedLogin(String userId, String returnTo) {}
}
