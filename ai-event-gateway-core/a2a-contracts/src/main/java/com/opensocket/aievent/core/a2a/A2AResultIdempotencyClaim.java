package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Canonical idempotency authority for an accepted A2A Result.
 *
 * <p>Submission attempts are append-only evidence and never own the idempotency key.
 * A claim is created only in the same transaction that creates the canonical Result.</p>
 */
@Getter @Setter @NoArgsConstructor
public class A2AResultIdempotencyClaim {
    private String tenantId;
    private String idempotencyKey;
    private String requestId;
    private String resultId;
    private String acceptanceAttemptId;
    private String resultFingerprint;
    private OffsetDateTime claimedAt;
}
