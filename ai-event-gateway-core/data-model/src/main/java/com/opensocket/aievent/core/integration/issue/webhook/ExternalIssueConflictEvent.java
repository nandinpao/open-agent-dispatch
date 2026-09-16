package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.OffsetDateTime;
public record ExternalIssueConflictEvent(
 String tenantId,String eventId,String conflictId,ExternalIssueConflictEventType eventType,String actorId,
 String reasonCode,String metadataJson,String previousEventHash,String eventHash,OffsetDateTime occurredAt,
 String correlationId) {}
