package com.opensocket.aievent.database.persistence.integrationevent.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class IntegrationEventPo {
    private String integrationEventId; private String eventId; private String eventType; private String aggregateType; private String aggregateId;
    private String envelopeJson; private String status; private int attemptCount; private OffsetDateTime nextAttemptAt; private String lastError;
    private String claimedBy; private OffsetDateTime claimUntil; private OffsetDateTime createdAt; private OffsetDateTime deliveredAt; private OffsetDateTime updatedAt;
}
