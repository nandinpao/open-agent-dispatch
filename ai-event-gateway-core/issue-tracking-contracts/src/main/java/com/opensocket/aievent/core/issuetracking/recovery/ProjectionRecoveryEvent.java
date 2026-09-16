package com.opensocket.aievent.core.issuetracking.recovery;
import java.time.OffsetDateTime;
public record ProjectionRecoveryEvent(String tenantId,String eventId,String aggregateType,String aggregateId,ProjectionRecoveryEventType eventType,String actorId,String reasonCode,String metadataJson,String previousEventHash,String eventHash,OffsetDateTime occurredAt,String correlationId){}
