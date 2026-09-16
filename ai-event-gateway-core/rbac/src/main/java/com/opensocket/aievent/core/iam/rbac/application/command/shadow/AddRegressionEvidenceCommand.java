package com.opensocket.aievent.core.iam.rbac.application.command.shadow;
import java.time.Instant;
public record AddRegressionEvidenceCommand(String evidenceId,String caseId,String testReference,String result,String detailsJson,String actorId,String correlationId,String auditReason,Instant executedAt){}
