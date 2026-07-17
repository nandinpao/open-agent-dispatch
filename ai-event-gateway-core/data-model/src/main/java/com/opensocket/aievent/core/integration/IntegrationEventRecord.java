package com.opensocket.aievent.core.integration;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class IntegrationEventRecord {
    @ToString.Include
    private String integrationEventId;
    @ToString.Include
    private String eventId;
    private String eventType;
    private String aggregateType;
    @ToString.Include
    private String aggregateId;
    private String envelopeJson;
    private IntegrationEventStatus status;
    private int attemptCount;
    private OffsetDateTime nextAttemptAt;
    private String lastError;
    private String claimedBy;
    private OffsetDateTime claimUntil;
    private OffsetDateTime createdAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime updatedAt;
    public void setIntegrationEventId(String value) { integrationEventId = value; }
    public void setEventId(String value) { eventId = value; }
    public void setEventType(String value) { eventType = value; }
    public void setAggregateType(String value) { aggregateType = value; }
    public void setAggregateId(String value) { aggregateId = value; }
    public void setEnvelopeJson(String value) { envelopeJson = value; }
    public void setStatus(IntegrationEventStatus value) { status = value; }
    public void setAttemptCount(int value) { attemptCount = Math.max(0, value); }
    public void setNextAttemptAt(OffsetDateTime value) { nextAttemptAt = value; }
    public void setLastError(String value) { lastError = value; }
    public void setClaimedBy(String value) { claimedBy = value; }
    public void setClaimUntil(OffsetDateTime value) { claimUntil = value; }
    public void setCreatedAt(OffsetDateTime value) { createdAt = value; }
    public void setDeliveredAt(OffsetDateTime value) { deliveredAt = value; }
    public void setUpdatedAt(OffsetDateTime value) { updatedAt = value; }
}
