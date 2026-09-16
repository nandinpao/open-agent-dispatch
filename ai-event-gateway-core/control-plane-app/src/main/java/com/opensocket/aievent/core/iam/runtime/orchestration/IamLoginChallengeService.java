package com.opensocket.aievent.core.iam.runtime.orchestration;

import com.opensocket.aievent.core.iam.authentication.domain.CredentialSubjectType;
import com.opensocket.aievent.core.iam.persistence.dao.IamApiRuntimeDao;
import com.opensocket.aievent.core.iam.runtime.config.IamRuntimeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/** Issues and validates one-time password-authenticated login challenges. */
public final class IamLoginChallengeService {
    private final IamApiRuntimeDao dao;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final IamRuntimeProperties properties;

    public IamLoginChallengeService(
            IamApiRuntimeDao dao,
            TransactionTemplate tx,
            Clock clock,
            IamRuntimeProperties properties) {
        this.dao = dao;
        this.tx = tx;
        this.clock = clock;
        this.properties = properties;
    }

    public String issue(
            CredentialSubjectType type,
            String subjectId,
            String tenantId,
            Instant authenticatedAt,
            String correlationId,
            String ipAddress,
            String userAgent) {
        String challengeId = UUID.randomUUID().toString();
        String secret = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        String presented = challengeId + "." + secret;
        Map<String, Object> row = new HashMap<>();
        row.put("challengeId", challengeId);
        row.put("challengeHash", hash(presented));
        row.put("subjectType", type.name());
        row.put("subjectId", subjectId);
        row.put("requestedTenantId", tenantId == null ? "" : tenantId);
        row.put("authenticatedAt", authenticatedAt);
        row.put("expiresAt", authenticatedAt.plus(properties.getLoginChallengeTtl()));
        row.put("correlationId", correlationId);
        row.put("ipAddress", ipAddress);
        row.put("userAgent", userAgent);
        if (tx.execute(status -> dao.insertLoginChallenge(row)) != 1) {
            throw new IllegalStateException("AUTH_LOGIN_CHALLENGE_CREATE_FAILED");
        }
        return presented;
    }

    /**
     * Reads an active challenge without consuming it. Invalid TOTP input must not burn the
     * password-authenticated challenge; consumption happens only after MFA succeeds.
     */
    public Challenge inspect(String presented) {
        Parsed parsed = parse(presented);
        Map<String, Object> row = tx.execute(status ->
                dao.findLoginChallenge(parsed.challengeId(), parsed.challengeHash(), clock.instant()));
        if (row == null) throw invalid();
        return challenge(row);
    }

    /** Atomically consumes the previously inspected challenge after successful MFA. */
    public void consume(String presented, Challenge expected) {
        Parsed parsed = parse(presented);
        Map<String, Object> row = tx.execute(status ->
                dao.consumeLoginChallenge(parsed.challengeId(), parsed.challengeHash(), clock.instant()));
        if (row == null) throw invalid();
        Challenge consumed = challenge(row);
        if (!consumed.equals(expected)) throw invalid();
    }

    private Parsed parse(String presented) {
        String[] parts = presented == null ? new String[0] : presented.split("\\.", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) throw invalid();
        return new Parsed(parts[0], hash(presented));
    }

    private Challenge challenge(Map<String, Object> row) {
        return new Challenge(
                CredentialSubjectType.valueOf(text(row, "subjectType")),
                text(row, "subjectId"),
                text(row, "requestedTenantId"),
                instant(row, "authenticatedAt"));
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("AUTH_LOGIN_CHALLENGE_INVALID");
    }

    private String hash(String value) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return Instant.parse(String.valueOf(value));
    }

    private record Parsed(String challengeId, String challengeHash) {}

    public record Challenge(
            CredentialSubjectType subjectType,
            String subjectId,
            String tenantId,
            Instant authenticatedAt) {}
}
