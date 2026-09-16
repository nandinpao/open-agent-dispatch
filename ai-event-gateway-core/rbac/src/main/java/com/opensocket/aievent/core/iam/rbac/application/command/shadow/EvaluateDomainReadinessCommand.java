package com.opensocket.aievent.core.iam.rbac.application.command.shadow;
import java.time.Instant;
public record EvaluateDomainReadinessCommand(String tenantId,String domainCode,Instant windowStartedAt,Instant windowEndedAt,String actorId,String correlationId,String auditReason){}
