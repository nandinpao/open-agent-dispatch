package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class A2AStateHistoryEntry {
    private String tenantId;
    private String historyId;
    private String requestId;
    private A2ARequestStatus fromStatus;
    private A2AOperationalStage fromOperationalStage;
    private A2ARequestStatus toStatus;
    private A2AOperationalStage toOperationalStage;
    private A2ABlockerCode blockerCode = A2ABlockerCode.NONE;
    private A2ATransitionCommand transitionCommand;
    private A2ATransitionPermission requiredPermission;
    private A2ATransitionEvidenceType evidenceType;
    private String evidenceReference;
    private String domainEventCode;
    private A2ATransitionFailureHandling failureHandling;
    private A2ATransitionTimeoutPolicy timeoutPolicy;
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
