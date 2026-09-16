package com.opensocket.aievent.database.persistence.a2a.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class A2AIdempotencyPo {
    private String tenantId;
    private String idempotencyKey;
    private String operationType;
    private String requestHash;
    private String resourceType;
    private String resourceId;
    private String resultStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime expiresAt;
}
