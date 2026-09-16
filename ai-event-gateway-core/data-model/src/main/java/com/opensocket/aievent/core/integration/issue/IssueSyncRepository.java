package com.opensocket.aievent.core.integration.issue;
import java.time.OffsetDateTime; import java.util.*;
public interface IssueSyncRepository {
 IssueRelationship saveRelationship(IssueRelationship value); Optional<IssueRelationship> findRelationship(String tenantId,String relationshipId); List<IssueRelationship> listRelationships(String tenantId,String taskIssueLinkId,int limit);
 List<String> listDueTenants(OffsetDateTime now,int limit);
 IntegrationOutboxEntry saveOutbox(IntegrationOutboxEntry value); Optional<IntegrationOutboxEntry> findOutbox(String tenantId,String outboxId); Optional<IntegrationOutboxEntry> findOutboxByIdempotencyKey(String tenantId,String key); List<IntegrationOutboxEntry> claimDue(String tenantId,String workerId,OffsetDateTime now,OffsetDateTime claimUntil,int limit);
 IntegrationInboxEntry saveInbox(IntegrationInboxEntry value); Optional<IntegrationInboxEntry> findInboxByProviderEvent(String tenantId,String connectionId,String providerEventId); List<IntegrationInboxEntry> listInbox(String tenantId,int limit);
 IntegrationSyncAttempt saveAttempt(IntegrationSyncAttempt value); List<IntegrationSyncAttempt> listAttempts(String tenantId,String outboxId,int limit);
 IntegrationDeadLetter saveDeadLetter(IntegrationDeadLetter value); Optional<IntegrationDeadLetter> findDeadLetter(String tenantId,String id); Optional<IntegrationDeadLetter> findDeadLetterByOutbox(String tenantId,String outboxId); List<IntegrationDeadLetter> listDeadLetters(String tenantId,IntegrationDeadLetterStatus status,int limit);
 IntegrationConflict saveConflict(IntegrationConflict value); Optional<IntegrationConflict> findConflict(String tenantId,String id); List<IntegrationConflict> listConflicts(String tenantId,IntegrationConflictStatus status,int limit);
 IntegrationCircuitBreaker saveCircuitBreaker(IntegrationCircuitBreaker value); Optional<IntegrationCircuitBreaker> findCircuitBreaker(String tenantId,String connectionId,String mappingId);
 String mode();
}
