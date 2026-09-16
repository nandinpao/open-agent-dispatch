package com.opensocket.aievent.core.iam.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opensocket.aievent.core.iam.runtime.security.IamSessionCookieCodec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IamSessionCookieCodecTest {
    private static final Instant NOW = Instant.parse("2026-07-23T03:00:00Z");
    private final IamSessionCookieCodec codec = new IamSessionCookieCodec(
            "01234567890123456789012345678901".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void roundTripsTenantSessionWithoutTreatingTenantAsAuthority() {
        String encoded = codec.encode("tenant-a", "session-a", NOW.plusSeconds(300));
        IamSessionCookieCodec.Locator locator = codec.decode(encoded);
        assertThat(locator.scope()).isEqualTo("TENANT");
        assertThat(locator.tenantId()).isEqualTo("tenant-a");
        assertThat(locator.sessionId()).isEqualTo("session-a");
    }

    @Test
    void rejectsTamperingAndExpiry() {
        String encoded = codec.encode("tenant-a", "session-a", NOW.plusSeconds(1));
        assertThatThrownBy(() -> codec.decode(encoded + "x"))
                .hasMessageContaining("AUTH_SESSION_COOKIE_INVALID");
        IamSessionCookieCodec expired = new IamSessionCookieCodec(
                "01234567890123456789012345678901".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC));
        assertThatThrownBy(() -> expired.decode(encoded))
                .hasMessageContaining("AUTH_SESSION_EXPIRED");
    }
}
