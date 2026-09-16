package com.opensocket.aievent.core.iam.rbac.domain.shadow;
import java.time.Instant;import java.util.Optional;
public record Phase5RuntimeCertificationEvidence(String evidenceId,String status,String sourceVersion,String catalogRevisionId,Optional<String> manifestId,String postgresqlCleanStatus,String postgresqlUpgradeStatus,String applicationContextStatus,String adminUiBuildStatus,String playwrightStatus,String loadTestStatus,String pipelineStatus,String evidenceJson,String actorId,String auditReason,Optional<String> correlationId,Instant generatedAt){}
