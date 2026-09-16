package com.opensocket.aievent.database.persistence.a2a.po;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AStateHistoryPo {
    private String tenantId;
    private String historyId;
    private String requestId;
    private String fromStatus;
    private String fromOperationalStage;
    private String toStatus;
    private String toOperationalStage;
    private String blockerCode;
    private String transitionCommand;
    private String requiredPermission;
    private String evidenceType;
    private String evidenceReference;
    private String domainEventCode;
    private String failureHandling;
    private String timeoutPolicy;
    private long expectedVersion;
    private long resultingVersion;
    private boolean recovery;
    private String reasonCode;
    private String reason;
    private String actorType;
    private String actorId;
    private String correlationId;
    private OffsetDateTime transitionAt;
    private long requestVersion;
    private String idempotencyKey;
}
