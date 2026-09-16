package com.opensocket.aievent.core.iam.token.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenPolicyTest {
    @Test
    void oneTimeTokenDefaultsAndHardMaximumsAreShortLived() {
        TokenPolicy policy = TokenPolicy.secureDefault();

        assertEquals(Duration.ofHours(1), policy.defaultFor(AccessTokenType.PASSWORD_RESET_TOKEN));
        assertEquals(Duration.ofHours(24), policy.maximumFor(AccessTokenType.PASSWORD_RESET_TOKEN));
        assertEquals(Duration.ofHours(24), policy.defaultFor(AccessTokenType.INVITATION_TOKEN));
        assertEquals(Duration.ofDays(7), policy.maximumFor(AccessTokenType.INVITATION_TOKEN));
        assertEquals(Duration.ofDays(7), policy.maximumFor(AccessTokenType.EMAIL_VERIFICATION_TOKEN));
    }
}
