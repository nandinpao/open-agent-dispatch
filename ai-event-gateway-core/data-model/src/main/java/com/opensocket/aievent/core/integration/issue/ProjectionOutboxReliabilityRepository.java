package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime;import java.util.*;
public interface ProjectionOutboxReliabilityRepository {
 ProjectionOutboxReliability save(ProjectionOutboxReliability value);
 ProjectionOutboxReliability saveExpectedVersion(ProjectionOutboxReliability value,long expectedVersion);
 Optional<ProjectionOutboxReliability> find(String tenantId,String outboxId);
 List<ProjectionOutboxReliability> claimDue(String workerId,OffsetDateTime now,OffsetDateTime claimUntil,int limit);
 boolean heartbeat(String tenantId,String outboxId,String workerId,String claimTokenHash,long expectedVersion,OffsetDateTime heartbeatAt,OffsetDateTime claimUntil);
 List<ProjectionOutboxReliability> list(String tenantId,ProjectionOutboxReliabilityStatus status,int limit);
 ProjectionProviderAttemptEvidence appendAttempt(ProjectionProviderAttemptEvidence value);
 List<ProjectionProviderAttemptEvidence> listAttempts(String tenantId,String outboxId,int limit);
 ProjectionReadbackEvidence appendReadback(ProjectionReadbackEvidence value);
 List<ProjectionReadbackEvidence> listReadbacks(String tenantId,String outboxId,int limit);
 default String mode(){return "CUSTOM";}
}
