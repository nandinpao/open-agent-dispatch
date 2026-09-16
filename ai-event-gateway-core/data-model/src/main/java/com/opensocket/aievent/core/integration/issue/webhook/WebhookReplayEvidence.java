package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.OffsetDateTime;
public record WebhookReplayEvidence(String tenantId,String evidenceId,String inboxId,String eventType,String decision,
 String reasonCode,String evidenceHash,String actorId,OffsetDateTime occurredAt,String correlationId) {}
