package com.opensocket.aievent.core.iam.authentication.application.service;

import com.opensocket.aievent.core.iam.authentication.application.command.*;
import com.opensocket.aievent.core.iam.authentication.application.port.in.PasswordAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.*;
import com.opensocket.aievent.core.iam.authentication.application.result.PasswordAuthenticationResult;
import com.opensocket.aievent.core.iam.authentication.domain.*;
import com.opensocket.aievent.core.iam.authentication.event.AuthenticationSecurityEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

public final class PasswordAuthenticationService implements PasswordAuthenticationCommandPort {
    private final PasswordCredentialRepository credentials;
    private final PasswordHistoryRepository history;
    private final PasswordPolicyRepository policies;
    private final PasswordHashingPort hashing;
    private final PasswordBreachCheckPort breach;
    private final SubjectSecurityStateRepository states;
    private final LoginAttemptRepository attempts;
    private final AuthenticationSubjectPort subjects;
    private final BrowserSessionRepository sessions;
    private final SecurityEpochPort epochs;
    private final AuthenticationEventPublisher events;
    private final Clock clock;
    private final LoginRateLimitPolicy rateLimitPolicy;
    private final PasswordHash dummyHash;

    public PasswordAuthenticationService(
            PasswordCredentialRepository credentials,
            PasswordHistoryRepository history,
            PasswordPolicyRepository policies,
            PasswordHashingPort hashing,
            PasswordBreachCheckPort breach,
            SubjectSecurityStateRepository states,
            LoginAttemptRepository attempts,
            AuthenticationSubjectPort subjects,
            BrowserSessionRepository sessions,
            SecurityEpochPort epochs,
            AuthenticationEventPublisher events,
            Clock clock,
            LoginRateLimitPolicy rateLimitPolicy) {
        this.credentials = Objects.requireNonNull(credentials);
        this.history = Objects.requireNonNull(history);
        this.policies = Objects.requireNonNull(policies);
        this.hashing = Objects.requireNonNull(hashing);
        this.breach = Objects.requireNonNull(breach);
        this.states = Objects.requireNonNull(states);
        this.attempts = Objects.requireNonNull(attempts);
        this.subjects = Objects.requireNonNull(subjects);
        this.sessions = Objects.requireNonNull(sessions);
        this.epochs = Objects.requireNonNull(epochs);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
        this.rateLimitPolicy = Objects.requireNonNull(rateLimitPolicy);
        this.dummyHash = hashing.hash("OpenDispatch-Dummy-Credential-2026!".toCharArray());
    }

    @Override
    public PasswordCredential setPassword(SetPasswordCommand command) {
        Instant at = time(command.occurredAt());
        char[] password = Objects.requireNonNull(command.newPassword(), "newPassword");
        try {
            PasswordPolicy policy = policy(command.tenantId());
            validate(password, command.username(), policy);
            PasswordCredential existing = credentials.find(command.subjectType(), command.subjectId()).orElse(null);
            ensureNotReused(command.subjectType(), command.subjectId(), password, policy);
            PasswordCredential next = existing == null
                    ? PasswordCredential.create(command.subjectType(), command.subjectId(), hashing.hash(password), at, policy, command.mustChange())
                    : existing.rotate(hashing.hash(password), at, policy, command.mustChange());
            PasswordCredential saved = credentials.save(next, existing == null ? 0 : command.expectedVersion());
            history.append(new PasswordHistoryEntry(UUID.randomUUID().toString(), command.subjectType(),
                    command.subjectId(), saved.passwordHash(), at));
            history.trim(command.subjectType(), command.subjectId(), policy.passwordHistoryCount());
            revokeCurrentScopeSessions(command.subjectType(), command.subjectId(), command.tenantId(),
                    command.actorId(), "PASSWORD_CHANGED", at);
            // Password credentials are global to the identity, therefore every Tenant session must observe this bump.
            epochs.increment("", command.subjectId(), command.actorId());
            publish("USER_PASSWORD_CHANGED", command.subjectType(), command.subjectId(), command.tenantId(),
                    command.actorId(), command.correlationId(), "PASSWORD_CHANGED", at);
            return saved;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public PasswordCredential changePassword(ChangePasswordCommand command) {
        Instant at = time(command.occurredAt());
        char[] current = Objects.requireNonNull(command.currentPassword(), "currentPassword");
        try {
            PasswordCredential credential = credentials.find(command.subjectType(), command.subjectId())
                    .orElseThrow(this::invalid);
            PasswordPolicy policy = policy(command.tenantId());
            if (!credential.mustChange() && at.isBefore(credential.changedAt().plus(policy.minimumAge()))) {
                throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_PASSWORD_POLICY_VIOLATION,
                        "Password minimum age has not elapsed");
            }
            if (!hashing.matches(current, credential.passwordHash())) throw invalid();
            return setPassword(new SetPasswordCommand(command.subjectType(), command.subjectId(), command.username(),
                    command.tenantId(), command.newPassword(), false, command.actorId(), command.correlationId(),
                    at, command.expectedVersion()));
        } finally {
            Arrays.fill(current, '\0');
        }
    }

    @Override
    public PasswordAuthenticationResult authenticate(AuthenticatePasswordCommand command) {
        Instant at = time(command.occurredAt());
        char[] password = Objects.requireNonNull(command.password(), "password");
        String normalized = normalize(command.username());
        try {
            Instant rateWindowStart = at.minus(rateLimitPolicy.window());
            boolean usernameLimited = attempts.countRecentFailures(normalized, rateWindowStart) >= rateLimitPolicy.usernameFailureLimit();
            boolean ipLimited = command.ipAddress() != null && !command.ipAddress().isBlank()
                    && attempts.countRecentFailuresByIp(command.ipAddress(), rateWindowStart) >= rateLimitPolicy.ipFailureLimit();
            if (usernameLimited || ipLimited) {
                publish("LOGIN_RATE_LIMITED", CredentialSubjectType.HUMAN_USER, "", command.requestedTenantId(),
                        "ANONYMOUS", command.correlationId(), "AUTH_RATE_LIMITED", at);
                throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_RATE_LIMITED,
                        "Login rate limit exceeded");
            }
            AuthenticationSubjectPort.Subject subject = subjects.findByNormalizedUsername(normalized).orElse(null);
            if (subject == null) {
                hashing.matches(password, dummyHash);
                record(command, normalized, "", "AUTH_INVALID_CREDENTIALS", false, at);
                throw invalid();
            }
            boolean rootBootstrapPending = subject.subjectType() == CredentialSubjectType.INSTANCE_ROOT
                    && "BOOTSTRAP_PENDING".equals(subject.status());
            if (!rootBootstrapPending) {
                switch (subject.status()) {
                    case "ACTIVE", "PASSWORD_RESET_REQUIRED", "MFA_ENROLLMENT_REQUIRED" -> {
                        // Authentication may continue. Password/MFA lifecycle checks happen below.
                    }
                    case "PENDING_ACTIVATION" -> {
                        record(command, normalized, subject.subjectId(), "AUTH_ACCOUNT_SETUP_REQUIRED", false, at);
                        throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_ACCOUNT_SETUP_REQUIRED,
                                "Account setup is incomplete. Complete activation or have an administrator set a temporary password.");
                    }
                    case "LOCKED" -> {
                        record(command, normalized, subject.subjectId(), "AUTH_ACCOUNT_LOCKED", false, at);
                        throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_ACCOUNT_LOCKED,
                                "Account is locked");
                    }
                    case "SUSPENDED" -> {
                        record(command, normalized, subject.subjectId(), "AUTH_ACCOUNT_DISABLED", false, at);
                        throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_ACCOUNT_DISABLED,
                                "Account is suspended");
                    }
                    case "DISABLED", "DELETED" -> {
                        record(command, normalized, subject.subjectId(), "AUTH_ACCOUNT_DISABLED", false, at);
                        throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_ACCOUNT_DISABLED,
                                "Account is disabled");
                    }
                    default -> {
                        record(command, normalized, subject.subjectId(), "AUTH_ACCOUNT_DISABLED", false, at);
                        throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_ACCOUNT_DISABLED,
                                "Account status does not allow sign-in");
                    }
                }
            }

            Optional<SubjectSecurityState> existingState = states.find(subject.subjectType(), subject.subjectId());
            SubjectSecurityState state = existingState.orElseGet(
                    () -> SubjectSecurityState.initial(subject.subjectType(), subject.subjectId()));
            long stateExpectedVersion = existingState.map(SubjectSecurityState::version).orElse(0L);
            if (state.lockedAt(at)) {
                record(command, normalized, subject.subjectId(), "AUTH_ACCOUNT_LOCKED", false, at);
                throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_ACCOUNT_LOCKED, "Account is locked");
            }

            PasswordCredential credential = credentials.find(subject.subjectType(), subject.subjectId()).orElse(null);
            PasswordPolicy policy = policy(command.requestedTenantId());
            if (credential == null || !hashing.matches(password, credential.passwordHash())) {
                SubjectSecurityState failed = state.recordFailure(at, policy);
                states.save(failed, stateExpectedVersion);
                String reason = failed.lockedAt(at) ? "AUTH_ACCOUNT_LOCKED" : "AUTH_INVALID_CREDENTIALS";
                record(command, normalized, subject.subjectId(), reason, false, at);
                publish("LOGIN_FAILED", subject.subjectType(), subject.subjectId(), command.requestedTenantId(),
                        subject.subjectId(), command.correlationId(), reason, at);
                if (failed.lockedAt(at)) {
                    epochs.increment("", subject.subjectId(), subject.subjectId());
                    throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_ACCOUNT_LOCKED,
                            "Account locked after failed attempts");
                }
                throw invalid();
            }

            if (command.requestedTenantId() != null && !command.requestedTenantId().isBlank()
                    && subject.subjectType() == CredentialSubjectType.HUMAN_USER) {
                subjects.requireTenantMembership(subject.subjectId(), command.requestedTenantId());
            }
            states.save(state.recordSuccess(at), stateExpectedVersion);
            record(command, normalized, subject.subjectId(), "LOGIN_SUCCEEDED", true, at);
            publish("LOGIN_SUCCEEDED", subject.subjectType(), subject.subjectId(), command.requestedTenantId(),
                    subject.subjectId(), command.correlationId(), "LOGIN_SUCCEEDED", at);
            return new PasswordAuthenticationResult(subject.subjectType(), subject.subjectId(), subject.username(),
                    command.requestedTenantId(), subject.mfaRequired(), credential.mustChange() || credential.expiredAt(at), credential.version(), at);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private PasswordPolicy policy(String tenantId) {
        PasswordPolicy instance = policies.instanceMinimum();
        if (tenantId == null || tenantId.isBlank()) return instance;
        return policies.findTenantPolicy(tenantId).map(tenant -> stricter(instance, tenant)).orElse(instance);
    }

    private PasswordPolicy stricter(PasswordPolicy a, PasswordPolicy b) {
        return new PasswordPolicy(
                Math.max(a.minimumLength(), b.minimumLength()),
                Math.min(a.maximumLength(), b.maximumLength()),
                a.requireUppercase() || b.requireUppercase(),
                a.requireLowercase() || b.requireLowercase(),
                a.requireNumber() || b.requireNumber(),
                a.requireSymbol() || b.requireSymbol(),
                Math.max(a.passwordHistoryCount(), b.passwordHistoryCount()),
                a.maximumAge().compareTo(b.maximumAge()) <= 0 ? a.maximumAge() : b.maximumAge(),
                a.minimumAge().compareTo(b.minimumAge()) >= 0 ? a.minimumAge() : b.minimumAge(),
                Math.min(a.failedAttemptThreshold(), b.failedAttemptThreshold()),
                a.lockoutDuration().compareTo(b.lockoutDuration()) >= 0 ? a.lockoutDuration() : b.lockoutDuration(),
                a.breachedPasswordMode().ordinal() >= b.breachedPasswordMode().ordinal()
                        ? a.breachedPasswordMode() : b.breachedPasswordMode());
    }

    private void validate(char[] raw, String username, PasswordPolicy policy) {
        List<String> violations = policy.violations(raw, username);
        if (!violations.isEmpty()) {
            throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_PASSWORD_POLICY_VIOLATION,
                    String.join(",", violations));
        }
        if (breach.breached(raw) && policy.breachedPasswordMode() == PasswordPolicy.BreachedPasswordMode.BLOCK) {
            throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_PASSWORD_BREACHED,
                    "Password appears in a breach corpus");
        }
    }

    private void ensureNotReused(CredentialSubjectType type, String subjectId, char[] raw, PasswordPolicy policy) {
        for (PasswordHistoryEntry entry : history.findRecent(type, subjectId, policy.passwordHistoryCount())) {
            if (hashing.matches(raw, entry.passwordHash())) {
                throw new AuthenticationDomainException(AuthenticationReasonCode.AUTH_PASSWORD_REUSED,
                        "Password was recently used");
            }
        }
    }

    private void revokeCurrentScopeSessions(CredentialSubjectType type, String subjectId, String tenantId,
                                            String actorId, String reason, Instant at) {
        if (type == CredentialSubjectType.INSTANCE_ROOT) {
            sessions.revokeAll(type, subjectId, "", actorId, reason, at);
        } else if (tenantId != null && !tenantId.isBlank()) {
            sessions.revokeAll(type, subjectId, tenantId, actorId, reason, at);
        }
    }

    private void record(AuthenticatePasswordCommand command, String normalized, String subjectId,
                        String reason, boolean successful, Instant at) {
        attempts.append(new LoginAttempt(UUID.randomUUID().toString(), normalized, subjectId,
                command.requestedTenantId(), successful, reason, command.ipAddress(), command.userAgent(),
                command.correlationId(), at));
    }

    private void publish(String type, CredentialSubjectType subjectType, String subjectId, String tenantId,
                         String actorId, String correlationId, String reason, Instant at) {
        events.publish(new AuthenticationSecurityEvent(UUID.randomUUID().toString(), type, subjectType.name(),
                subjectId, tenantId, actorId, correlationId, reason, Map.of(), at));
    }

    private AuthenticationDomainException invalid() {
        return new AuthenticationDomainException(AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS,
                "Invalid credentials");
    }
    private Instant time(Instant at) { return at == null ? clock.instant() : at; }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
