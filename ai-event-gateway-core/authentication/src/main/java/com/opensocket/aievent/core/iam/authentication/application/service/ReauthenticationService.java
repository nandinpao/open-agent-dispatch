package com.opensocket.aievent.core.iam.authentication.application.service;

import com.opensocket.aievent.core.iam.authentication.application.command.ReauthenticateCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.ReauthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.MfaMethodRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.MfaSecretProtectorPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.OneTimeSecretPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordCredentialRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.PasswordHashingPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.ReauthenticationGrantRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.TotpVerificationPort;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationReasonCode;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.MfaMethod;
import com.opensocket.aievent.core.iam.authentication.domain.PasswordCredential;
import com.opensocket.aievent.core.iam.authentication.domain.ReauthenticationGrant;
import com.opensocket.aievent.core.iam.authentication.domain.SessionPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Issues one-time, purpose-bound grants after password and optional MFA re-verification. */
public final class ReauthenticationService implements ReauthenticationCommandPort {
    private final BrowserSessionRepository sessions;
    private final PasswordCredentialRepository credentials;
    private final PasswordHashingPort hashing;
    private final MfaMethodRepository mfaMethods;
    private final MfaSecretProtectorPort protector;
    private final TotpVerificationPort totp;
    private final ReauthenticationGrantRepository grants;
    private final OneTimeSecretPort secrets;
    private final Clock clock;

    public ReauthenticationService(
            BrowserSessionRepository sessions,
            PasswordCredentialRepository credentials,
            PasswordHashingPort hashing,
            MfaMethodRepository mfaMethods,
            MfaSecretProtectorPort protector,
            TotpVerificationPort totp,
            ReauthenticationGrantRepository grants,
            OneTimeSecretPort secrets,
            Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.mfaMethods = Objects.requireNonNull(mfaMethods, "mfaMethods");
        this.protector = Objects.requireNonNull(protector, "protector");
        this.totp = Objects.requireNonNull(totp, "totp");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ReauthenticationGrant reauthenticate(ReauthenticateCommand command, SessionPolicy policy) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(policy, "policy");
        Instant occurredAt = command.occurredAt() == null ? clock.instant() : command.occurredAt();
        try {
            BrowserSession session = sessions.find(command.sessionId())
                    .orElseThrow(() -> new AuthenticationDomainException(
                            AuthenticationReasonCode.AUTH_SESSION_REVOKED, "Session not found"));
            session.requireActive(occurredAt);

            PasswordCredential credential = credentials.find(session.subjectType(), session.subjectId())
                    .orElseThrow(this::invalidCredentials);
            if (!hashing.matches(command.password(), credential.passwordHash())) {
                throw invalidCredentials();
            }

            if (session.methods().contains("MFA")) {
                verifyMfa(command, session, occurredAt);
            }

            ReauthenticationGrant grant = new ReauthenticationGrant(
                    secrets.generate(24), session.sessionId(), session.subjectId(), command.purposes(),
                    occurredAt, occurredAt.plus(policy.reauthenticationWindow()), false, 1);
            grants.save(grant, 0);
            return grant;
        } finally {
            if (command.password() != null) {
                Arrays.fill(command.password(), '\0');
            }
        }
    }

    private void verifyMfa(ReauthenticateCommand command, BrowserSession session, Instant occurredAt) {
        MfaMethod method = mfaMethods.findActive(session.subjectType(), session.subjectId())
                .orElseThrow(() -> new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_MFA_REQUIRED, "MFA is required"));
        String secret = protector.reveal(method.protectedSecret(), method.keyId());
        if (!totp.verify(secret, command.mfaCode(), occurredAt,
                method.digits(), method.periodSeconds(), method.algorithm())) {
            throw new AuthenticationDomainException(
                    AuthenticationReasonCode.AUTH_MFA_INVALID, "Invalid MFA code");
        }
    }

    private AuthenticationDomainException invalidCredentials() {
        return new AuthenticationDomainException(
                AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
    }
}
