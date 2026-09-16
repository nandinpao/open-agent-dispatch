package com.opensocket.aievent.core.iam.token.application.result;

import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import com.opensocket.aievent.core.iam.security.contract.SecurityEpoch;
import com.opensocket.aievent.core.iam.token.domain.AccessTokenType;
import com.opensocket.aievent.core.iam.token.domain.TokenScope;
import java.time.Instant;

/** Validated opaque-token result used by runtime adapters; clear-text token material is never returned. */
public record ValidatedTokenResult(
        String tokenId,
        AccessTokenType type,
        PrincipalRef principal,
        String tenantId,
        TokenScope effectiveScope,
        SecurityEpoch securityEpoch,
        Instant issuedAt,
        Instant expiresAt
) {}
