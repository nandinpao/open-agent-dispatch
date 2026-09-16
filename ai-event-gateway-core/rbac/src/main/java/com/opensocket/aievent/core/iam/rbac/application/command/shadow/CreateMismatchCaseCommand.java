package com.opensocket.aievent.core.iam.rbac.application.command.shadow;
import java.time.Instant;import java.util.Optional;
public record CreateMismatchCaseCommand(String caseId,String tenantId,String comparisonId,Optional<String> entryPointId,String ownerId,Instant slaDueAt,String title,String actorId,String correlationId,String auditReason,Instant requestedAt){}
