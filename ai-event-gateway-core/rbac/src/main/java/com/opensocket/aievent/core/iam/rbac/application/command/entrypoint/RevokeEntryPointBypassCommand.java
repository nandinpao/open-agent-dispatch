package com.opensocket.aievent.core.iam.rbac.application.command.entrypoint;
import java.time.Instant;
public record RevokeEntryPointBypassCommand(String bypassId,long expectedVersion,String reason,String actorId,String correlationId,String auditReason,Instant requestedAt) {}
