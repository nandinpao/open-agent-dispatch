package com.opensocket.aievent.core.integration.issue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.integration.issue.webhook.*;

/** Explicit REQUIRES_NEW boundaries for Inbox and immutable Observation durability. */
@Service
public class ProviderWebhookDurableTransactionService {
    private final ProviderWebhookReliabilityRepository repository;
    public ProviderWebhookDurableTransactionService(ProviderWebhookReliabilityRepository repository) { this.repository=repository; }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ProviderWebhookInboxEntry persistVerifiedInbox(ProviderWebhookInboxEntry inbox) {
        if (repository.insertInbox(inbox)) return inbox;
        return repository.findByProviderEvent(inbox.tenantId(),inbox.connectionId(),inbox.providerEventId())
                .orElseThrow(() -> new IllegalStateException("WEBHOOK_INBOX_INSERT_CONFLICT"));
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ExternalIssueObservation persistObservation(ExternalIssueObservation observation) {
        return repository.findObservationByInbox(observation.tenantId(),observation.inboxId())
                .orElseGet(() -> repository.saveObservation(observation));
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ProviderWebhookInboxEntry updateExpected(ProviderWebhookInboxEntry current,ProviderWebhookInboxEntry next) {
        if (!repository.updateInboxExpectedVersion(next,current.version(),current.claimTokenHash()))
            throw new IllegalStateException("WEBHOOK_INBOX_VERSION_OR_CLAIM_CONFLICT");
        return next;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ProviderWebhookInboxEntry recordIdenticalReplay(ProviderWebhookInboxEntry current,String actor) {
        ProviderWebhookInboxEntry next=copy(current,current.status(),current.replayCount()+1,current.retryCount(),current.nextRetryAt(),
                current.claimOwner(),current.claimTokenHash(),current.claimedAt(),current.leaseUntil(),current.processingAttemptId(),
                current.processedAt(),current.lastErrorCode(),current.lastErrorMessage(),current.version()+1);
        updateExpected(current,next);
        repository.appendReplayEvidence(new WebhookReplayEvidence(current.tenantId(),"webhook-evidence-"+java.util.UUID.randomUUID(),current.inboxId(),"REPLAY","ACCEPTED_IDENTICAL","WEBHOOK_IDENTICAL_REPLAY",ExternalObservationNormalizer.sha256(current.payloadHash()+"|REPLAY"),actor,now(),current.correlationId()));
        return next;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recordRejectedReplay(ProviderWebhookInboxEntry current,String incomingPayloadHash,String reason) {
        repository.appendReplayEvidence(new WebhookReplayEvidence(current.tenantId(),"webhook-evidence-"+java.util.UUID.randomUUID(),current.inboxId(),"REPLAY_CONFLICT","REJECTED",reason,ExternalObservationNormalizer.sha256(current.payloadHash()+"|"+incomingPayloadHash+"|"+reason),"SYSTEM",now(),current.correlationId()));
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ProviderWebhookInboxEntry markFailure(ProviderWebhookInboxEntry current,String code,boolean retryable,int maxAttempts) {
        OffsetDateTime now=now(); int nextRetry=current.retryCount()+1; boolean exhausted=!retryable||nextRetry>=Math.max(1,maxAttempts);
        ProviderWebhookInboxStatus status=exhausted?ProviderWebhookInboxStatus.DEAD_LETTER:ProviderWebhookInboxStatus.FAILED_RETRYABLE;
        OffsetDateTime due=exhausted?null:now.plusSeconds(Math.min(900,30L*(1L<<Math.min(5,nextRetry))));
        ProviderWebhookInboxEntry next=copy(current,status,current.replayCount(),nextRetry,due,null,null,null,null,null,now,
                exhausted&&retryable?"WEBHOOK_RETRY_EXHAUSTED":code,"Webhook processing failed; raw Provider details were not persisted.",current.version()+1);
        updateExpected(current,next);
        repository.appendReplayEvidence(new WebhookReplayEvidence(current.tenantId(),"webhook-evidence-"+java.util.UUID.randomUUID(),current.inboxId(),exhausted?"DEAD_LETTER":"RETRY_SCHEDULED",exhausted?"STOPPED":"RETRY",next.lastErrorCode(),ExternalObservationNormalizer.sha256(current.payloadHash()+"|"+next.lastErrorCode()),"RECONCILER",now,current.correlationId()));
        return next;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public List<ProviderWebhookInboxEntry> claimDue(String workerId,int limit,long leaseSeconds) {
        OffsetDateTime now=now(); repository.recoverStaleClaims(now,Math.max(1,limit));
        return repository.claimDue(now,now.plusSeconds(Math.max(15,leaseSeconds)),workerId,"claim-"+java.util.UUID.randomUUID(),"attempt-"+java.util.UUID.randomUUID(),Math.max(1,limit));
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ProviderWebhookInboxEntry scheduleGovernedReplay(ProviderWebhookInboxEntry current,String actor,String reason) {
        if (current.status()!=ProviderWebhookInboxStatus.DEAD_LETTER&&current.status()!=ProviderWebhookInboxStatus.FAILED_RETRYABLE)
            throw new IllegalStateException("WEBHOOK_REPLAY_NOT_ALLOWED");
        OffsetDateTime now=now();
        ProviderWebhookInboxEntry next=copy(current,ProviderWebhookInboxStatus.FAILED_RETRYABLE,current.replayCount()+1,0,now,null,null,null,null,null,null,null,null,current.version()+1);
        updateExpected(current,next);
        repository.appendReplayEvidence(new WebhookReplayEvidence(current.tenantId(),"webhook-evidence-"+java.util.UUID.randomUUID(),current.inboxId(),"GOVERNED_REPLAY","SCHEDULED","OPERATOR_REPLAY",ExternalObservationNormalizer.sha256(current.payloadHash()+"|"+reason),actor,now,current.correlationId()));
        return next;
    }

    static ProviderWebhookInboxEntry complete(ProviderWebhookInboxEntry current,OffsetDateTime at) {
        return copy(current,ProviderWebhookInboxStatus.PROCESSED,current.replayCount(),current.retryCount(),null,null,null,null,null,null,at,null,null,current.version()+1);
    }

    static ProviderWebhookInboxEntry copy(ProviderWebhookInboxEntry c,ProviderWebhookInboxStatus status,int replay,int retry,OffsetDateTime nextRetry,
            String claimOwner,String claimToken,OffsetDateTime claimedAt,OffsetDateTime leaseUntil,String attemptId,OffsetDateTime processed,
            String errorCode,String errorMessage,long version) {
        return new ProviderWebhookInboxEntry(c.tenantId(),c.inboxId(),c.connectionId(),c.providerType(),c.providerEventId(),c.eventType(),
                c.externalProjectId(),c.externalIssueId(),c.externalIssueKey(),c.nonce(),c.providerTimestamp(),c.signatureVerified(),
                c.timestampVerified(),c.nonceAccepted(),c.tenantBound(),c.connectionBound(),c.payloadJson(),c.payloadHash(),status,
                replay,retry,nextRetry,claimOwner,claimToken,claimedAt,leaseUntil,attemptId,c.receivedAt(),processed,errorCode,errorMessage,
                version,c.correlationId());
    }
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
