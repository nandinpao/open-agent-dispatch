package com.opensocket.aievent.core.iam.authentication.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.opensocket.aievent.core.iam.authentication.application.command.BeginTotpEnrollmentCommand;

import com.opensocket.aievent.core.iam.authentication.application.port.out.BrowserSessionRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.MfaMethodRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.MfaSecretProtectorPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.OneTimeSecretPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.RecoveryCodeRepository;
import com.opensocket.aievent.core.iam.authentication.application.port.out.SecurityEpochPort;
import com.opensocket.aievent.core.iam.authentication.application.port.out.TotpVerificationPort;
import com.opensocket.aievent.core.iam.authentication.domain.BrowserSession;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.authentication.domain.MfaMethod;
import com.opensocket.aievent.core.iam.authentication.domain.RecoveryCode;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MfaAuthenticationServiceTest {
    @Test
    void enrollmentCreatesTenHashOnlyRecoveryCodesUsingAtLeastSixteenRandomBytes() {
        InMemoryMfaMethods methods = new InMemoryMfaMethods();
        InMemoryRecoveryCodes recovery = new InMemoryRecoveryCodes();
        List<Integer> generatedSizes = new ArrayList<>();
        OneTimeSecretPort oneTimeSecrets = new OneTimeSecretPort() {
            public String generate(int bytes) { generatedSizes.add(bytes); return "code-" + generatedSizes.size(); }
            public String hash(String value) { return "hash:" + value; }
            public boolean matches(String value, String hash) { return ("hash:" + value).equals(hash); }
        };
        MfaAuthenticationService service = new MfaAuthenticationService(
                methods, recovery,
                new MfaSecretProtectorPort() {
                    public ProtectedSecret protect(String plaintext) { return new ProtectedSecret("protected", "key-v1"); }
                    public String reveal(String value, String keyId) { return "secret"; }
                },
                new TotpVerificationPort() {
                    public String generateSecret() { return "JBSWY3DPEHPK3PXP"; }
                    public boolean verify(String secret, String code, Instant at, int digits, int period, String algorithm) { return true; }
                },
                oneTimeSecrets, new NoopSessions(), new NoopEpochs(), event -> { },
                Clock.fixed(Instant.parse("2026-07-23T00:00:00Z"), ZoneOffset.UTC));

        var result = service.begin(new BeginTotpEnrollmentCommand(
                CredentialSubjectType.HUMAN_USER, "user-1", "user@example.test", "OpenDispatch",
                "admin", "corr-1", Instant.parse("2026-07-23T00:00:00Z")));

        assertEquals(10, result.recoveryCodes().size());
        assertEquals(1L, result.expectedVersion());
        assertEquals(10, recovery.values.size());
        assertTrue(generatedSizes.stream().allMatch(size -> size >= 16));
        assertTrue(recovery.values.stream().allMatch(code -> code.codeHash().startsWith("hash:")));
        assertTrue(result.otpauthUri().contains("algorithm=SHA1"));
        assertTrue(result.otpauthUri().contains("digits=6"));
        assertTrue(result.otpauthUri().contains("period=30"));
        assertEquals("HmacSHA1", methods.values.getLast().algorithm());

        var rotated = service.begin(new BeginTotpEnrollmentCommand(
                CredentialSubjectType.HUMAN_USER, "user-1", "user@example.test", "OpenDispatch",
                "admin", "corr-2", Instant.parse("2026-07-23T00:01:00Z")));
        assertFalse(methods.values.getFirst().active());
        assertEquals(MfaMethod.Status.DISABLED, methods.values.getFirst().status());
        assertEquals("HmacSHA1", methods.values.getLast().algorithm());
        assertTrue(rotated.otpauthUri().contains("algorithm=SHA1"));
    }

    private static final class InMemoryMfaMethods implements MfaMethodRepository {
        private final List<MfaMethod> values = new ArrayList<>();
        public Optional<MfaMethod> findActive(CredentialSubjectType type, String subjectId) { return values.stream().filter(MfaMethod::active).findFirst(); }
        public Optional<MfaMethod> findById(String methodId) { return values.stream().filter(value -> value.methodId().equals(methodId)).findFirst(); }
        public MfaMethod save(MfaMethod method, long expectedVersion) {
            for (int index = 0; index < values.size(); index++) {
                if (values.get(index).methodId().equals(method.methodId())) {
                    values.set(index, method);
                    return method;
                }
            }
            values.add(method);
            return method;
        }
        public List<MfaMethod> findAll(CredentialSubjectType type, String subjectId) { return List.copyOf(values); }
    }

    private static final class InMemoryRecoveryCodes implements RecoveryCodeRepository {
        private List<RecoveryCode> values = List.of();
        public void replace(CredentialSubjectType type, String subjectId, List<RecoveryCode> codes) { values = List.copyOf(codes); }
        public List<RecoveryCode> findUnused(CredentialSubjectType type, String subjectId) { return values; }
        public boolean markUsed(String recoveryCodeId, Instant usedAt) { return true; }
    }

    private static final class NoopEpochs implements SecurityEpochPort {
        public SecurityEpoch current(String tenantId, String principalId) { return SecurityEpoch.ZERO; }
        public SecurityEpoch increment(String tenantId, String principalId, String actorId) { return SecurityEpoch.ZERO; }
    }

    private static final class NoopSessions implements BrowserSessionRepository {
        public Optional<BrowserSession> find(String sessionId) { return Optional.empty(); }
        public BrowserSession save(BrowserSession session, long expectedVersion) { return session; }
        public List<BrowserSession> findActive(CredentialSubjectType type, String subjectId, String tenantId) { return List.of(); }
        public int revokeAll(CredentialSubjectType type, String subjectId, String tenantId, String actorId, String reason, Instant at) { return 0; }
        public int revokeAllTenants(CredentialSubjectType type, String subjectId, String actorId, String reason, Instant at) { return 0; }
    }
}
