package com.opensocket.aievent.core.integration.issue.webhook;
import java.time.OffsetDateTime; import java.util.*;
public interface ProviderWebhookReliabilityRepository {
 ProviderWebhookInboxEntry saveInbox(ProviderWebhookInboxEntry value);
 default boolean insertInbox(ProviderWebhookInboxEntry value){saveInbox(value);return true;}
 default boolean updateInboxExpectedVersion(ProviderWebhookInboxEntry value,long expectedVersion,String claimTokenHash){saveInbox(value);return true;}
 Optional<ProviderWebhookInboxEntry> findInbox(String tenantId,String inboxId);
 Optional<ProviderWebhookInboxEntry> findByProviderEvent(String tenantId,String connectionId,String providerEventId);
 Optional<ProviderWebhookInboxEntry> findByNonce(String tenantId,String connectionId,String nonce);
 List<ProviderWebhookInboxEntry> listInbox(String tenantId,ProviderWebhookInboxStatus status,int limit);
 List<ProviderWebhookInboxEntry> listDue(OffsetDateTime now,int limit);
 default List<ProviderWebhookInboxEntry> claimDue(OffsetDateTime now,OffsetDateTime leaseUntil,String workerId,String claimTokenHash,String processingAttemptId,int limit){return listDue(now,limit);}
 default int recoverStaleClaims(OffsetDateTime now,int limit){return 0;}
 ExternalIssueObservation saveObservation(ExternalIssueObservation value);
 default Optional<ExternalIssueObservation> findObservationByInbox(String tenantId,String inboxId){return Optional.empty();}
 Optional<ExternalIssueObservation> latestObservation(String tenantId,String connectionId,String externalIssueId);
 List<ExternalIssueObservation> listObservations(String tenantId,String connectionId,String externalIssueId,int limit);
 ExternalIssueObservedState saveObservedState(ExternalIssueObservedState value);
 default boolean saveObservedStateIfNewer(ExternalIssueObservedState value){saveObservedState(value);return true;}
 Optional<ExternalIssueObservedState> findObservedState(String tenantId,String connectionId,String externalIssueId);
 ExternalIssueConflict saveConflict(ExternalIssueConflict value);
 default boolean saveConflictExpectedVersion(ExternalIssueConflict value,long expectedVersion){saveConflict(value);return true;}
 Optional<ExternalIssueConflict> findConflict(String tenantId,String conflictId);
 default Optional<ExternalIssueConflict> findConflictByResolutionIdempotency(String tenantId,String idempotencyKey){return Optional.empty();}
 List<ExternalIssueConflict> listConflicts(String tenantId,ExternalIssueConflictStatus status,int limit);
 default ExternalIssueConflictEvent appendConflictEvent(ExternalIssueConflictEvent value){return value;}
 default Optional<ExternalIssueConflictEvent> latestConflictEvent(String tenantId,String conflictId){return Optional.empty();}
 default List<ExternalIssueConflictEvent> listConflictEvents(String tenantId,String conflictId,int limit){return List.of();}
 WebhookReplayEvidence appendReplayEvidence(WebhookReplayEvidence value);
 List<WebhookReplayEvidence> listReplayEvidence(String tenantId,String inboxId,int limit);
 default String mode(){return "CUSTOM";}
}
