package com.opensocket.aievent.core.iam.rbac.application.command.shadow;
import java.time.Instant;
public record CreateMismatchWaiverCommand(String waiverId,String caseId,String reason,Instant expiresAt,String actorId,String correlationId,String auditReason,Instant requestedAt){}
