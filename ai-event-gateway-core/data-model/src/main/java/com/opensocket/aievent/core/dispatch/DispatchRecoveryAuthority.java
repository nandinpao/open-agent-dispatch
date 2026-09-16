package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

/**
 * Authority-specific reconciliation hook. Implementations may inspect canonical authority state
 * but must never initiate network I/O.
 */
public interface DispatchRecoveryAuthority {
    default int order() { return 100; }
    DispatchRecoveryAuthorityDecision reconcile(DispatchRequest request, OffsetDateTime now);
}
