package com.opensocket.aievent.core.iam.rbac.application.command.entrypoint;
import java.time.Instant;
public record CreateEntryPointBypassCommand(String bypassId,String entryPointId,String ownerId,String reason,String replacement,
 Instant expiresAt,String actorId,String correlationId,String auditReason,Instant requestedAt) {}
