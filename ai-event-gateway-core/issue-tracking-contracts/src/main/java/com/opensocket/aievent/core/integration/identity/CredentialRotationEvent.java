package com.opensocket.aievent.core.integration.identity;
import java.time.OffsetDateTime;
public record CredentialRotationEvent(String tenantId,String rotationEventId,String principalId,String oldCredentialId,String newCredentialId,String status,String reason,String correlationId,String actorType,String actorId,OffsetDateTime createdAt,OffsetDateTime graceExpiresAt,OffsetDateTime activatedAt,OffsetDateTime completedAt) {}
