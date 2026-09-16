package com.opensocket.aievent.core.iam.runtime.security;

import com.opensocket.aievent.core.iam.authentication.application.command.TouchSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.SessionCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SessionPolicyRepository;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;
import com.opensocket.aievent.core.iam.persistence.exception.IamOptimisticLockException;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantContextHolder;
import com.opensocket.aievent.core.iam.persistence.tenant.IamTenantExecutionContext;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import com.opensocket.aievent.core.iam.runtime.orchestration.IamAuthenticationRuntimeOrchestrator;
import com.opensocket.aievent.core.iam.runtime.orchestration.IamFederationRuntimeOrchestrator;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationAssurance;
import com.opensocket.aievent.core.iam.security.contract.AuthenticationContext;
import com.opensocket.aievent.core.iam.security.contract.ExternalIssuerRef;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.SessionRef;
import com.opensocket.aievent.core.iam.security.contract.SubjectRef;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Authenticates the signed browser Session cookie without invalidating it when one browser page
 * issues several administration requests concurrently.
 */
public final class IamRuntimeSessionAuthenticator {
    private static final int TOUCH_RETRY_LIMIT = 8;
    private static final Duration SESSION_TOUCH_INTERVAL = Duration.ofSeconds(30);

    private final BrowserSessionRepository sessions;
    private final SessionCommandPort commands;
    private final SessionPolicyRepository policies;
    private final SecurityEpochPort epochs;
    private final IamApiRuntimeDao dao;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final IamRuntimeProperties properties;

    public IamRuntimeSessionAuthenticator(
            BrowserSessionRepository sessions,
            SessionCommandPort commands,
            SessionPolicyRepository policies,
            SecurityEpochPort epochs,
            IamApiRuntimeDao dao,
            TransactionTemplate transactions,
            Clock clock,
            IamRuntimeProperties properties) {
        this.sessions = sessions;
        this.commands = commands;
        this.policies = policies;
        this.epochs = epochs;
        this.dao = Objects.requireNonNull(dao);
        this.transactions = transactions;
        this.clock = clock;
        this.properties = properties;
    }

    public AuthenticationContext authenticate(IamSessionCookieCodec.Locator locator) {
        String persistenceScope = "TENANT".equals(locator.scope()) ? locator.tenantId() : "INSTANCE";
        return IamTenantContextHolder.withContext(
                new IamTenantExecutionContext(persistenceScope, "session:" + locator.sessionId()),
                () -> load(locator));
    }

    private AuthenticationContext load(IamSessionCookieCodec.Locator locator) {
        BrowserSession current = requireCurrent(locator);
        SessionPolicy policy = resolvePolicy(current);
        BrowserSession touched = touchConcurrentlySafe(locator, policy);

        SubjectRef.IdentityType identity = touched.subjectType() == CredentialSubjectType.INSTANCE_ROOT
                ? SubjectRef.IdentityType.INSTANCE_ROOT
                : SubjectRef.IdentityType.HUMAN_USER;
        PrincipalRef.PrincipalType principal = touched.subjectType() == CredentialSubjectType.INSTANCE_ROOT
                ? PrincipalRef.PrincipalType.INSTANCE_ROOT
                : PrincipalRef.PrincipalType.USER;
        AuthenticationAssurance.Level level = touched.mfaVerifiedAt().isPresent()
                ? AuthenticationAssurance.Level.MFA
                : AuthenticationAssurance.Level.PASSWORD;

        return new AuthenticationContext(
                new SubjectRef(identity, touched.subjectId()),
                new PrincipalRef(principal, touched.subjectId()),
                touched.tenant(),
                Optional.of(new SessionRef(touched.sessionId())),
                new AuthenticationAssurance(
                        level,
                        touched.methods(),
                        touched.mfaVerifiedAt().orElse(touched.createdAt())),
                touched.securityEpoch(),
                externalIssuer(touched),
                touched.createdAt(),
                touched.absoluteExpiresAt());
    }

    private Optional<ExternalIssuerRef> externalIssuer(BrowserSession session) {
        if (session.subjectType() == CredentialSubjectType.INSTANCE_ROOT
                || session.tenant().scope() != TenantRef.Scope.TENANT) {
            return Optional.empty();
        }
        String providerId = session.methods().stream()
                .filter(method -> method.startsWith(IamFederationRuntimeOrchestrator.PROVIDER_METHOD_PREFIX))
                .map(method -> method.substring(IamFederationRuntimeOrchestrator.PROVIDER_METHOD_PREFIX.length()))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
        if (providerId.isBlank()) return Optional.empty();
        java.util.Map<String,Object> link = dao.findExternalCredentialLinkByUserProvider(
                session.tenant().tenantId(), providerId, session.subjectId());
        if (link == null) {
            throw new IllegalArgumentException("AUTH_FEDERATION_SESSION_LINK_MISSING");
        }
        Object issuer = link.get("issuerUri");
        Object subject = link.get("externalSubject");
        if (issuer == null || subject == null || String.valueOf(issuer).isBlank() || String.valueOf(subject).isBlank()) {
            throw new IllegalArgumentException("AUTH_FEDERATION_SESSION_LINK_INVALID");
        }
        return Optional.of(new ExternalIssuerRef(String.valueOf(issuer), String.valueOf(subject)));
    }

    private SessionPolicy resolvePolicy(BrowserSession current) {
        if (current.methods().contains(IamAuthenticationRuntimeOrchestrator.PASSWORD_CHANGE_REQUIRED_METHOD)) {
            return SessionPolicy.passwordChangeRequired(properties.getRootPasswordChangeSessionTtl());
        }
        if (current.tenant().scope() == TenantRef.Scope.INSTANCE) {
            return SessionPolicy.rootRecovery();
        }
        return inTransaction(() -> policies.findTenantPolicy(current.tenant().tenantId())
                .orElseGet(policies::instanceMinimum));
    }

    private BrowserSession touchConcurrentlySafe(
            IamSessionCookieCodec.Locator locator,
            SessionPolicy policy) {
        for (int attempt = 0; attempt < TOUCH_RETRY_LIMIT; attempt++) {
            BrowserSession latest = requireCurrent(locator);
            Instant now = clock.instant();

            // Administration pages fan out into several parallel GET requests. Refreshing the idle
            // deadline once per interval is sufficient and avoids turning every read into a write race.
            if (latest.lastSeenAt().plus(SESSION_TOUCH_INTERVAL).isAfter(now)) {
                return latest;
            }

            try {
                // SessionCommandPort is transaction-proxied. It intentionally runs outside the read
                // TransactionTemplate above, so one optimistic-lock failure cannot mark every retry
                // rollback-only.
                return commands.touch(
                        new TouchSessionCommand(
                                latest.sessionId(),
                                "session-touch",
                                now,
                                latest.version()),
                        policy);
            } catch (IamOptimisticLockException conflict) {
                Thread.onSpinWait();
            }
        }

        // Another parallel request has already refreshed this Session. Missing one idle extension
        // must never revoke the still-active cookie or force the browser back to Login.
        return requireCurrent(locator);
    }

    private BrowserSession requireCurrent(IamSessionCookieCodec.Locator locator) {
        return inTransaction(() -> {
            BrowserSession current = sessions.find(locator.sessionId())
                    .orElseThrow(() -> new IllegalArgumentException("AUTH_SESSION_REVOKED"));
            if (!current.tenant().tenantId().equals(locator.tenantId())) {
                throw new IllegalArgumentException("AUTH_SCOPE_MISMATCH");
            }
            current.requireActive(clock.instant());
            SecurityEpoch authority = epochs.current(current.tenant().tenantId(), current.subjectId());
            if (!authority.isAtLeast(current.securityEpoch())
                    || !current.securityEpoch().isAtLeast(authority)) {
                throw new IllegalArgumentException("AUTH_POLICY_VERSION_STALE");
            }
            return current;
        });
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return Objects.requireNonNull(
                transactions.execute(status -> work.get()),
                "IAM transaction returned no result");
    }
}
