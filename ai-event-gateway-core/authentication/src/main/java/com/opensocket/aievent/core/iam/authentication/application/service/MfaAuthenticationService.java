package com.opensocket.aievent.core.iam.authentication.application.service;

import com.opensocket.aievent.core.iam.authentication.application.command.BeginTotpEnrollmentCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.ConfirmTotpEnrollmentCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.ResetMfaCommand;
import com.opensocket.aievent.core.iam.authentication.application.command.VerifyMfaCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.in.MfaAuthenticationCommandPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.AuthenticationEventPublisher;
import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.MfaMethodRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.MfaSecretProtectorPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.OneTimeSecretPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.RecoveryCodeRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.TotpVerificationPort;
import com.opensocket.aievent.core.iam.authentication.application.result.TotpEnrollmentResult;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationReasonCode;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.MfaMethod;
import com.opensocket.aievent.core.iam.authentication.domain.RecoveryCode;
import com.opensocket.aievent.core.iam.authentication.event.AuthenticationSecurityEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Application service for TOTP enrollment, verification, recovery codes and reset. */
public final class MfaAuthenticationService implements MfaAuthenticationCommandPort {
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_BYTES = 16;

    private final MfaMethodRepository methods;
    private final RecoveryCodeRepository recoveryCodes;
    private final MfaSecretProtectorPort protector;
    private final TotpVerificationPort totp;
    private final OneTimeSecretPort secrets;
    private final BrowserSessionRepository sessions;
    private final SecurityEpochPort epochs;
    private final AuthenticationEventPublisher events;
    private final Clock clock;

    public MfaAuthenticationService(
            MfaMethodRepository methods,
            RecoveryCodeRepository recoveryCodes,
            MfaSecretProtectorPort protector,
            TotpVerificationPort totp,
            OneTimeSecretPort secrets,
            BrowserSessionRepository sessions,
            SecurityEpochPort epochs,
            AuthenticationEventPublisher events,
            Clock clock) {
        this.methods = Objects.requireNonNull(methods, "methods");
        this.recoveryCodes = Objects.requireNonNull(recoveryCodes, "recoveryCodes");
        this.protector = Objects.requireNonNull(protector, "protector");
        this.totp = Objects.requireNonNull(totp, "totp");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.epochs = Objects.requireNonNull(epochs, "epochs");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TotpEnrollmentResult begin(BeginTotpEnrollmentCommand command) {
        Objects.requireNonNull(command, "command");
        Instant occurredAt = time(command.occurredAt());
        disablePendingEnrollments(command.subjectType(), command.subjectId());
        String plainSecret = totp.generateSecret();
        MfaSecretProtectorPort.ProtectedSecret protectedSecret = protector.protect(plainSecret);

        MfaMethod method = MfaMethod.pendingTotp(
                UUID.randomUUID().toString(),
                command.subjectType(),
                command.subjectId(),
                protectedSecret.protectedValue(),
                protectedSecret.keyId(),
                occurredAt);
        methods.save(method, 0);

        List<String> plainRecoveryCodes = generateRecoveryCodes();
        recoveryCodes.replace(
                command.subjectType(),
                command.subjectId(),
                hashRecoveryCodes(command.subjectType(), command.subjectId(), plainRecoveryCodes, occurredAt));

        publish("USER_MFA_ENROLLMENT_STARTED", command.subjectType(), command.subjectId(),
                command.actorId(), command.correlationId(), occurredAt);

        String uri = "otpauth://totp/" + encode(command.issuer() + ":" + command.accountLabel())
                + "?secret=" + encode(plainSecret)
                + "&issuer=" + encode(command.issuer())
                + "&algorithm=" + encode(otpAuthAlgorithm(method.algorithm()))
                + "&digits=" + method.digits()
                + "&period=" + method.periodSeconds();
        return new TotpEnrollmentResult(
                method.methodId(), plainSecret, uri, plainRecoveryCodes, method.version());
    }

    @Override
    public MfaMethod confirm(ConfirmTotpEnrollmentCommand command) {
        Objects.requireNonNull(command, "command");
        Instant occurredAt = time(command.occurredAt());
        MfaMethod method = methods.findById(command.methodId()).orElseThrow(this::invalidMfa);
        String plainSecret = protector.reveal(method.protectedSecret(), method.keyId());
        if (!totp.verify(plainSecret, command.code(), occurredAt,
                method.digits(), method.periodSeconds(), method.algorithm())) {
            throw invalidMfa();
        }

        MfaMethod active = method.activate(occurredAt);
        methods.save(active, command.expectedVersion());
        // MFA is identity-wide. A global-principal epoch invalidates sessions in every Tenant.
        epochs.increment("", active.subjectId(), command.actorId());
        publish("USER_MFA_ENROLLED", active.subjectType(), active.subjectId(),
                command.actorId(), command.correlationId(), occurredAt);
        return active;
    }

    @Override
    public boolean verify(VerifyMfaCommand command) {
        Objects.requireNonNull(command, "command");
        Instant occurredAt = time(command.occurredAt());
        if (command.recoveryCode()) {
            return verifyRecoveryCode(command, occurredAt);
        }

        MfaMethod method = methods.findActive(command.subjectType(), command.subjectId())
                .orElseThrow(() -> new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_MFA_REQUIRED, "MFA enrollment is required"));
        String plainSecret = protector.reveal(method.protectedSecret(), method.keyId());
        if (!totp.verify(plainSecret, command.code(), occurredAt,
                method.digits(), method.periodSeconds(), method.algorithm())) {
            publish("MFA_CHALLENGE_FAILED", command.subjectType(), command.subjectId(),
                    command.subjectId(), command.correlationId(), occurredAt);
            throw invalidMfa();
        }
        return true;
    }

    @Override
    public void reset(ResetMfaCommand command) {
        Objects.requireNonNull(command, "command");
        Instant occurredAt = time(command.occurredAt());
        for (MfaMethod method : methods.findAll(command.subjectType(), command.subjectId())) {
            if (method.status() != MfaMethod.Status.DISABLED) {
                methods.save(method.disable(), method.version());
            }
        }
        recoveryCodes.replace(command.subjectType(), command.subjectId(), List.of());

        if (command.subjectType() == CredentialSubjectType.INSTANCE_ROOT) {
            sessions.revokeAll(command.subjectType(), command.subjectId(), "",
                    command.actorId(), "MFA_RESET", occurredAt);
        }
        // Human-user sessions are rejected on their next request by the identity-wide epoch.
        epochs.increment("", command.subjectId(), command.actorId());
        publish("USER_MFA_RESET", command.subjectType(), command.subjectId(),
                command.actorId(), command.correlationId(), occurredAt);
    }

    private boolean verifyRecoveryCode(VerifyMfaCommand command, Instant occurredAt) {
        for (RecoveryCode recoveryCode : recoveryCodes.findUnused(command.subjectType(), command.subjectId())) {
            if (secrets.matches(command.code(), recoveryCode.codeHash())) {
                if (!recoveryCodes.markUsed(recoveryCode.recoveryCodeId(), occurredAt)) {
                    throw invalidMfa();
                }
                publish("MFA_RECOVERY_CODE_USED", command.subjectType(), command.subjectId(),
                        command.subjectId(), command.correlationId(), occurredAt);
                return true;
            }
        }
        throw new AuthenticationDomainException(
                AuthenticationReasonCode.AUTH_RECOVERY_CODE_INVALID, "Invalid recovery code");
    }

    private List<String> generateRecoveryCodes() {
        List<String> values = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            values.add(secrets.generate(RECOVERY_CODE_BYTES));
        }
        return List.copyOf(values);
    }

    private List<RecoveryCode> hashRecoveryCodes(
            CredentialSubjectType subjectType, String subjectId, List<String> plainCodes, Instant occurredAt) {
        List<RecoveryCode> values = new ArrayList<>(plainCodes.size());
        for (String plainCode : plainCodes) {
            values.add(new RecoveryCode(UUID.randomUUID().toString(), subjectType, subjectId,
                    secrets.hash(plainCode), occurredAt, Optional.empty()));
        }
        return values;
    }

    private void publish(String eventType, CredentialSubjectType subjectType, String subjectId,
                         String actorId, String correlationId, Instant occurredAt) {
        events.publish(new AuthenticationSecurityEvent(UUID.randomUUID().toString(), eventType,
                subjectType.name(), subjectId, "", actorId, correlationId, eventType, Map.of(), occurredAt));
    }

    private AuthenticationDomainException invalidMfa() {
        return new AuthenticationDomainException(AuthenticationReasonCode.AUTH_MFA_INVALID, "Invalid MFA code");
    }

    private Instant time(Instant value) {
        return value == null ? clock.instant() : value;
    }


    private void disablePendingEnrollments(CredentialSubjectType subjectType, String subjectId) {
        for (MfaMethod existing : methods.findAll(subjectType, subjectId)) {
            if (existing.status() == MfaMethod.Status.PENDING) {
                methods.save(existing.disable(), existing.version());
            }
        }
    }

    private String otpAuthAlgorithm(String algorithm) {
        return switch (algorithm) {
            case "HmacSHA1" -> "SHA1";
            case "HmacSHA256" -> "SHA256";
            case "HmacSHA512" -> "SHA512";
            default -> throw new IllegalArgumentException("Unsupported TOTP algorithm: " + algorithm);
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
