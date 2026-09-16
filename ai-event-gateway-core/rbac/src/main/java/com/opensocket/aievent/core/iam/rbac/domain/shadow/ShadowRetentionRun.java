package com.opensocket.aievent.core.iam.rbac.domain.shadow;
import java.time.Instant;import java.util.Optional;
public record ShadowRetentionRun(String runId,String status,long metricsDeleted,long receiptsDeleted,long deadLettersDeleted,int partitionsArchived,int partitionsPurged,String detailsJson,Instant startedAt,Optional<Instant> completedAt,String actorId,String auditReason,Optional<String> correlationId){}
