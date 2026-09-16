package com.opensocket.aievent.core.iam.runtime.credential;

import com.opensocket.aievent.core.iam.authentication.application.command.AuthenticatePasswordCommand;
import com.opensocket.aievent.core.iam.authentication.application.port.out.AuthenticationSubjectPort;
import com.opensocket.aievent.core.iam.authentication.application.result.PasswordAuthenticationResult;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationDomainException;
import com.opensocket.aievent.core.iam.authentication.domain.AuthenticationReasonCode;
import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.identity.AdminAccount;
import com.opensocket.aievent.core.identity.AdminIdentityRepository;
import java.nio.CharBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bounded R2 credential adapter. It verifies an old password, resolves the linked
 * canonical IAM subject and forces replacement with an IAM password. It never
 * creates a Legacy Principal, Browser Session, Role or Permission.
 */
public final class LegacyPasswordCredentialAdapter {
    public static final String METHOD = "LEGACY_PASSWORD";

    private final AdminIdentityRepository legacyIdentities;
    private final PasswordEncoder encoder;
    private final AuthenticationSubjectPort canonicalSubjects;
    private final IamApiRuntimeDao dao;
    private final TransactionTemplate transactions;
    private final LegacyCredentialAdapterProperties properties;
    private final Clock clock;

    public LegacyPasswordCredentialAdapter(
            AdminIdentityRepository legacyIdentities,
            PasswordEncoder encoder,
            AuthenticationSubjectPort canonicalSubjects,
            IamApiRuntimeDao dao,
            TransactionTemplate transactions,
            LegacyCredentialAdapterProperties properties,
            Clock clock) {
        this.legacyIdentities = legacyIdentities;
        this.encoder = encoder;
        this.canonicalSubjects = Objects.requireNonNull(canonicalSubjects);
        this.dao = Objects.requireNonNull(dao);
        this.transactions = Objects.requireNonNull(transactions);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
        if (properties.isLegacyPasswordAdapterEnabled() && (legacyIdentities == null || encoder == null)) {
            throw new IllegalStateException("R2 legacy credential adapter requires the bounded legacy credential repository and password encoder");
        }
    }

    public boolean hasActiveLink(String username) {
        if (!properties.isLegacyPasswordAdapterEnabled()) return false;
        String normalized = normalize(username);
        if (normalized.isBlank()) return false;
        Map<String, Object> link = transactions.execute(status ->
                dao.findCredentialLink("LEGACY_PASSWORD", normalized));
        return link != null && "ACTIVE".equals(text(link, "status"));
    }

    public Optional<PasswordAuthenticationResult> authenticate(AuthenticatePasswordCommand command) {
        if (!properties.isLegacyPasswordAdapterEnabled()) return Optional.empty();
        char[] password = Objects.requireNonNull(command.password(), "password");
        try {
            String normalized = normalize(command.username());
            AdminAccount legacy = legacyIdentities.findByUsername(normalized).orElse(null);
            if (legacy == null || !legacy.enabled() || !encoder.matches(CharBuffer.wrap(password), legacy.passwordHash())) {
                return Optional.empty();
            }
            Map<String, Object> link = transactions.execute(status ->
                    dao.findCredentialLink("LEGACY_PASSWORD", normalized));
            if (link == null || !"ACTIVE".equals(text(link, "status"))) {
                throw new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS,
                        "Legacy credential is not linked to a canonical IAM subject");
            }
            if (!legacy.userId().equals(text(link, "providerExternalId"))
                    && !text(link, "providerExternalId").isBlank()) {
                throw new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS,
                        "Legacy credential link does not match the configured identity");
            }
            String canonicalUsername = text(link, "canonicalUsername");
            AuthenticationSubjectPort.Subject subject = canonicalSubjects
                    .findByNormalizedUsername(normalize(canonicalUsername))
                    .orElseThrow(() -> new AuthenticationDomainException(
                            AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS,
                            "Canonical IAM subject is unavailable"));
            if (!text(link, "subjectId").equals(subject.subjectId())) {
                throw new AuthenticationDomainException(
                        AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS,
                        "Credential link subject mismatch");
            }
            String tenantId = command.requestedTenantId() == null ? "" : command.requestedTenantId().trim();
            if (!tenantId.isBlank()) canonicalSubjects.requireTenantMembership(subject.subjectId(), tenantId);
            Instant at = command.occurredAt() == null ? clock.instant() : command.occurredAt();
            transactions.executeWithoutResult(status -> {
                int updated = dao.touchCredentialLink(
                        text(link, "credentialLinkId"), at, subject.subjectId(), number(link, "version"));
                if (updated != 1) throw new IllegalStateException("AUTH_CREDENTIAL_LINK_VERSION_CONFLICT");
            });
            return Optional.of(new PasswordAuthenticationResult(
                    CredentialSubjectType.HUMAN_USER,
                    subject.subjectId(),
                    subject.username(),
                    tenantId,
                    subject.mfaRequired(),
                    true,
                    0L,
                    at));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void requireCurrentPassword(String canonicalSubjectId, String legacyUsername, char[] password) {
        if (!properties.isLegacyPasswordAdapterEnabled()) throw invalid();
        try {
            String normalized = normalize(legacyUsername);
            Map<String, Object> link = transactions.execute(status ->
                    dao.findCredentialLinkBySubject("LEGACY_PASSWORD", canonicalSubjectId));
            if (link == null || !"ACTIVE".equals(text(link, "status"))) throw invalid();
            String providerSubject = text(link, "providerSubject");
            AdminAccount legacy = legacyIdentities.findByUsername(providerSubject).orElse(null);
            if (legacy == null || !legacy.enabled() || !encoder.matches(CharBuffer.wrap(password), legacy.passwordHash())) {
                throw invalid();
            }
            if (!normalized.isBlank() && !normalize(providerSubject).equals(normalized)
                    && !normalize(text(link, "canonicalUsername")).equals(normalized)) {
                throw invalid();
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void markReplaced(String canonicalSubjectId, String actorId, Instant occurredAt) {
        Map<String, Object> link = transactions.execute(status ->
                dao.findCredentialLinkBySubject("LEGACY_PASSWORD", canonicalSubjectId));
        if (link == null || !"ACTIVE".equals(text(link, "status"))) return;
        Instant at = occurredAt == null ? clock.instant() : occurredAt;
        transactions.executeWithoutResult(status -> {
            int updated = dao.replaceCredentialLink(
                    text(link, "credentialLinkId"), at, actorId, number(link, "version"));
            if (updated != 1) throw new IllegalStateException("AUTH_CREDENTIAL_LINK_VERSION_CONFLICT");
        });
    }

    private AuthenticationDomainException invalid() {
        return new AuthenticationDomainException(AuthenticationReasonCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
    }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }
    private static long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
