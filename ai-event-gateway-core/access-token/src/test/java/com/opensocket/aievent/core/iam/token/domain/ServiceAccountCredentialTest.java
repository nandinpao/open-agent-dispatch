package com.opensocket.aievent.core.iam.token.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class ServiceAccountCredentialTest {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Test
    void rotationAllowsOnlyTheConfiguredOverlapAndRevocationIsTerminal() {
        ServiceAccountCredential credential = credential(Duration.ofDays(30));
        ServiceAccountCredential rotating = credential.beginRotation(Duration.ofMinutes(5), "admin", NOW.plusSeconds(10));
        assertDoesNotThrow(() -> rotating.assertUsable(NOW.plus(Duration.ofMinutes(4))));
        TokenDomainException outside = assertThrows(TokenDomainException.class,
                () -> rotating.assertUsable(NOW.plus(Duration.ofMinutes(6))));
        assertEquals(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_REVOKED, outside.reasonCode());

        ServiceAccountCredential revoked = rotating.revoke("compromised", "admin", NOW.plus(Duration.ofMinutes(2)));
        TokenDomainException denied = assertThrows(TokenDomainException.class,
                () -> revoked.assertUsable(NOW.plus(Duration.ofMinutes(3))));
        assertEquals(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_REVOKED, denied.reasonCode());
        assertThrows(TokenDomainException.class,
                () -> revoked.revoke("again", "admin", NOW.plus(Duration.ofMinutes(4))));
    }

    @Test
    void expiryAndTtlAreFailClosed() {
        ServiceAccountCredential credential = credential(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> credential.assertUsable(NOW.plusSeconds(59)));
        TokenDomainException expired = assertThrows(TokenDomainException.class,
                () -> credential.assertUsable(NOW.plusSeconds(60)));
        assertEquals(TokenReasonCode.SERVICE_ACCOUNT_CREDENTIAL_EXPIRED, expired.reasonCode());
        assertThrows(TokenDomainException.class, () -> credential(Duration.ofDays(366)));
    }

    private static ServiceAccountCredential credential(Duration ttl) {
        return ServiceAccountCredential.issue(
                "tenant-a", new ServiceAccountCredentialId("cred-1"), new ServiceAccountId("svc-1"),
                "primary", "odp_sac_0123456789abcdef01", "1234",
                new TokenHash("HMAC-SHA-256", "hash"), NOW, ttl, "", "admin");
    }
}
