package com.opensocket.aievent.core.iam.rbac.application.command.shadow;
import java.time.Instant;import java.util.Optional;
public record UpdateMismatchCaseCommand(String caseId,String status,String severity,String ownerId,Instant slaDueAt,Optional<String> resolution,long expectedVersion,String actorId,String correlationId,String auditReason,Instant requestedAt){}
