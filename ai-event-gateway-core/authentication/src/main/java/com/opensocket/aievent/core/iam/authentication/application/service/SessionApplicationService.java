package com.opensocket.aievent.core.iam.authentication.application.service;

import com.opensocket.aievent.core.iam.authentication.application.command.CreateBrowserSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.RevokeSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.RotateSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.TouchSessionCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.SessionCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.AuthenticationEventPublisher;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.OneTimeSecretPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationReasonCode;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;
import com.opensocket.aievent.core.iam.authentication.event.AuthenticationSecurityEvent;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Creates, validates, rotates activity time and revokes browser sessions. */
public final class SessionApplicationService implements SessionCommandPort {
    private final BrowserSessionRepository sessions;
    private final OneTimeSecretPort secrets;
    private final SecurityEpochPort epochs;
    private final AuthenticationEventPublisher events;
    private final Clock clock;

    public SessionApplicationService(
            BrowserSessionRepository sessions,
            OneTimeSecretPort secrets,
            SecurityEpochPort epochs,
            AuthenticationEventPublisher events,
            Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.epochs = Objects.requireNonNull(epochs, "epochs");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public BrowserSession create(CreateBrowserSessionCommand command, SessionPolicy policy) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(policy, "policy");
        Instant occurredAt = time(command.occurredAt());
        String tenantId = tenantId(command.tenant());

        List<BrowserSession> active = sessions.findActive(
                command.subjectType(), command.subjectId(), tenantId);
        int excess = active.size() - policy.maxConcurrentSessions() + 1;
        if (excess > 0) {
            active.stream()
                    .sorted(Comparator.comparing(BrowserSession::lastSeenAt))
                    .limit(excess)
                    .forEach(session -> sessions.save(
                            session.revoke(command.subjectId(), "MAX_CONCURRENT_SESSIONS", occurredAt),
                            session.version()));
        }

        SecurityEpoch currentEpoch = epochs.current(tenantId, command.subjectId());
        BrowserSession session = BrowserSession.create(
                secrets.generate(32), command.subjectType(), command.subjectId(), command.tenant(),
                command.methods(), command.mfaVerifiedAt(), occurredAt, policy,
                command.ipAddress(), command.userAgent(), currentEpoch);
        sessions.save(session, 0);
        publish("SESSION_CREATED", session, command.subjectId(), command.correlationId(),
                "SESSION_CREATED", occurredAt);
        return session;
    }

    @Override
    public BrowserSession touch(TouchSessionCommand command, SessionPolicy policy) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(policy, "policy");
        Instant occurredAt = time(command.occurredAt());
        BrowserSession session = sessions.find(command.sessionId())
                .orElseThrow(() -> new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_SESSION_REVOKED, "Session not found"));
        session.requireActive(occurredAt);

        SecurityEpoch authority = epochs.current(tenantId(session.tenant()), session.subjectId());
        if (!session.securityEpoch().isAtLeast(authority)) {
            throw new AuthenticationDomainException(
                    AuthenticationReasonCode.AUTH_SESSION_STALE_EPOCH, "Session security epoch is stale");
        }

        BrowserSession next = session.touch(occurredAt, policy);
        sessions.save(next, command.expectedVersion());
        return next;
    }

    @Override
    public BrowserSession rotate(RotateSessionCommand command, SessionPolicy policy) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(policy, "policy");
        Instant occurredAt = time(command.occurredAt());
        BrowserSession current = sessions.find(command.sessionId())
                .orElseThrow(() -> new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_SESSION_REVOKED, "Session not found"));
        current.requireActive(occurredAt);

        String tenantId = tenantId(current.tenant());
        SecurityEpoch authority = epochs.current(tenantId, current.subjectId());
        if (!current.securityEpoch().isAtLeast(authority)) {
            throw new AuthenticationDomainException(
                    AuthenticationReasonCode.AUTH_SESSION_STALE_EPOCH, "Session security epoch is stale");
        }

        String actorId = command.actorId() == null || command.actorId().isBlank()
                ? current.subjectId()
                : command.actorId();
        BrowserSession revoked = current.revoke(actorId, "SESSION_ROTATED", occurredAt);
        sessions.save(revoked, command.expectedVersion());

        String ipAddress = command.ipAddress() == null || command.ipAddress().isBlank()
                ? current.ipAddress()
                : command.ipAddress();
        String userAgent = command.userAgent() == null || command.userAgent().isBlank()
                ? current.userAgent()
                : command.userAgent();
        BrowserSession replacement = BrowserSession.create(
                secrets.generate(32), current.subjectType(), current.subjectId(), current.tenant(),
                current.methods(), current.mfaVerifiedAt(), occurredAt, policy,
                ipAddress, userAgent, authority);
        sessions.save(replacement, 0);
        events.publish(new AuthenticationSecurityEvent(
                UUID.randomUUID().toString(), "SESSION_ROTATED",
                replacement.subjectType().name(), replacement.subjectId(), tenantId,
                actorId, command.correlationId(), "SESSION_ROTATED",
                Map.of(
                        "oldSessionId", current.sessionId(),
                        "newSessionId", replacement.sessionId()),
                occurredAt));
        return replacement;
    }

    @Override
    public BrowserSession revoke(RevokeSessionCommand command) {
        Objects.requireNonNull(command, "command");
        Instant occurredAt = time(command.occurredAt());
        BrowserSession current = sessions.find(command.sessionId())
                .orElseThrow(() -> new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_SESSION_REVOKED, "Session not found"));
        BrowserSession revoked = current.revoke(command.actorId(), command.reason(), occurredAt);
        sessions.save(revoked, command.expectedVersion());
        publish("SESSION_REVOKED", revoked, command.actorId(), command.correlationId(),
                command.reason(), occurredAt);
        return revoked;
    }

    private void publish(String eventType, BrowserSession session, String actorId,
                         String correlationId, String reason, Instant occurredAt) {
        events.publish(new AuthenticationSecurityEvent(UUID.randomUUID().toString(), eventType,
                session.subjectType().name(), session.subjectId(), tenantId(session.tenant()),
                actorId, correlationId, reason, Map.of("sessionId", session.sessionId()), occurredAt));
    }

    private String tenantId(TenantRef tenant) {
        return tenant.scope() == TenantRef.Scope.TENANT ? tenant.tenantId() : "";
    }

    private Instant time(Instant value) {
        return value == null ? clock.instant() : value;
    }
}
