package com.opensocket.aievent.core.iam.authentication.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.security.contract.TenantRef;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BrowserSessionTest {
    @Test
    void absoluteExpiryCapsIdleExtension() {
        var at = Instant.parse("2026-07-23T00:00:00Z");
        var session = BrowserSession.create(
                "s1", CredentialSubjectType.HUMAN_USER, "u1", TenantRef.tenant("t1"),
                Set.of("PASSWORD"), Optional.empty(), at, SessionPolicy.secureDefault(),
                "", "", SecurityEpoch.ZERO);

        var touched = session.touch(at.plusSeconds(60), SessionPolicy.secureDefault());

        assertFalse(touched.idleExpiresAt().isAfter(touched.absoluteExpiresAt()));
    }

    @Test
    void revokedSessionCannotBeUsedAfterRotation() {
        var at = Instant.parse("2026-07-23T00:00:00Z");
        var session = BrowserSession.create(
                "s1", CredentialSubjectType.HUMAN_USER, "u1", TenantRef.tenant("t1"),
                Set.of("PASSWORD", "TOTP"), Optional.of(at), at, SessionPolicy.secureDefault(),
                "127.0.0.1", "test", new SecurityEpoch(3, 3, 3));

        var revoked = session.revoke("u1", "SESSION_ROTATED", at.plusSeconds(1));

        assertEquals(BrowserSession.Status.REVOKED, revoked.status());
        assertEquals("SESSION_ROTATED", revoked.revokeReason());
        assertThrows(AuthenticationDomainException.class, () -> revoked.requireActive(at.plusSeconds(2)));
    }
}
