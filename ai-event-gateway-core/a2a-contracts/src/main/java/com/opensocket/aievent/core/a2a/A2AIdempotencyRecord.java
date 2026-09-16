package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class A2AIdempotencyRecord {
    private String tenantId;
    private String idempotencyKey;
    private String operationType;
    private String requestHash;
    private String resourceType;
    private String resourceId;
    private A2AIdempotencyStatus resultStatus = A2AIdempotencyStatus.IN_PROGRESS;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime expiresAt;
}
