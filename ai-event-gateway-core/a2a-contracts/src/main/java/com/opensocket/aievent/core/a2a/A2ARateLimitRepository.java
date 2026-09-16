package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;

public interface A2ARateLimitRepository {
    boolean tryAcquire(String tenantId, String policyId, int limitPerMinute, OffsetDateTime now);
    String mode();
}
