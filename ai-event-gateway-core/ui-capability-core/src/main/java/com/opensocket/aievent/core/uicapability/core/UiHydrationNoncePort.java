package com.opensocket.aievent.core.uicapability.core;

import java.time.Instant;

/** Issues a non-authoritative, session-bound hydration correlation nonce. */
public interface UiHydrationNoncePort {
    String issue(String tenantId, String principalId, String routeContext, Instant expiresAt);
}
