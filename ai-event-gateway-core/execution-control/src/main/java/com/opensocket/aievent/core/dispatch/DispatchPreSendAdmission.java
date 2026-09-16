package com.opensocket.aievent.core.dispatch;

import java.time.OffsetDateTime;

/**
 * Final Core-owned authority gate executed immediately before network delivery.
 *
 * <p>Implementations may use a short database transaction to revalidate and atomically persist the
 * send boundary, but must return before any network I/O starts. Netty remains transport-only.</p>
 */
public interface DispatchPreSendAdmission {
    default int order() { return 0; }
    DispatchPreSendAdmissionDecision admit(DispatchRequest request, OffsetDateTime now);
}
