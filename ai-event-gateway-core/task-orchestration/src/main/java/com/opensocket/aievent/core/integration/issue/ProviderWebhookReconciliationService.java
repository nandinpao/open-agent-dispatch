package com.opensocket.aievent.core.integration.issue;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.opensocket.aievent.core.integration.identity.*;
import com.opensocket.aievent.core.integration.issue.webhook.*;

/** Orchestrates the three independently committed Phase 3F-R2 transaction stages. */
@Service
public class ProviderWebhookReconciliationService {
    private final ProviderWebhookReliabilityRepository repository;
    private final IntegrationIdentityRepository identities;
    private final ProviderWebhookDurableTransactionService durable;
    private final ProviderWebhookStateApplicationService stateApplication;
    private final ProviderWebhookConflictResolutionService conflictResolution;
    private final ExternalObservationNormalizer normalizer;
    private final long replayWindowSeconds;
    private final int maxAttempts;
    private final long claimLeaseSeconds;
    private final String workerId;

    public ProviderWebhookReconciliationService(ProviderWebhookReliabilityRepository repository,IntegrationIdentityRepository identities,
            ProviderWebhookDurableTransactionService durable,ProviderWebhookStateApplicationService stateApplication,
            ProviderWebhookConflictResolutionService conflictResolution,ExternalObservationNormalizer normalizer,
            @Value("${integration-sync.webhook-replay-window-seconds:300}") long replayWindowSeconds,
            @Value("${integration-sync.webhook-max-attempts:5}") int maxAttempts,
            @Value("${integration-sync.webhook-claim-lease-seconds:60}") long claimLeaseSeconds) {
        this.repository=repository; this.identities=identities; this.durable=durable; this.stateApplication=stateApplication;
        this.conflictResolution=conflictResolution; this.normalizer=normalizer;
        this.replayWindowSeconds=Math.max(30,replayWindowSeconds); this.maxAttempts=Math.max(1,maxAttempts);
        this.claimLeaseSeconds=Math.max(15,claimLeaseSeconds);
        this.workerId=System.getenv().getOrDefault("HOSTNAME","provider-webhook-reconciler-local");
    }

    public ProviderWebhookReceipt receive(String tenantId,String connectionId,String providerType,String providerEventId,String eventType,
            String externalProjectId,String externalIssueId,String externalIssueKey,String externalIssueStatus,String providerTimestamp,
            String nonce,boolean signatureVerified,String payloadJson,String observedDocumentJson,String providerIdentity,String mappingSchemaHash,
            Long providerEventSequence,String providerChangeVersion,String correlationId) {
        String tenant=required(tenantId,"tenantId"),connection=required(connectionId,"connectionId"),event=required(providerEventId,"providerEventId");
        String payload=safeJson(payloadJson),payloadHash=ExternalObservationNormalizer.sha256(payload); OffsetDateTime now=now();
        OffsetDateTime timestamp=parseTimestamp(providerTimestamp);
        boolean timestampVerified=timestamp!=null&&!timestamp.isBefore(now.minusSeconds(replayWindowSeconds))&&!timestamp.isAfter(now.plusSeconds(30));
        Optional<IntegrationConnection> bound=identities.findConnection(tenant,connection);
        boolean connectionBound=bound.filter(v->v.enabled()&&providerMatches(v.providerType(),providerType)).isPresent();
        boolean tenantBound=bound.map(v->tenant.equals(v.tenantId())).orElse(false);
        if(!signatureVerified)throw new IllegalStateException("WEBHOOK_SIGNATURE_INVALID");
        if(!timestampVerified)throw new IllegalStateException("WEBHOOK_TIMESTAMP_INVALID");
        if(!tenantBound)throw new IllegalStateException("WEBHOOK_TENANT_BINDING_INVALID");
        if(!connectionBound)throw new IllegalStateException("WEBHOOK_CONNECTION_BINDING_INVALID");
        normalizer.assertNoSensitiveKeys(payload);
        CanonicalExternalObservation canonical=normalizer.normalize(observedDocumentJson);

        Optional<ProviderWebhookInboxEntry> prior=repository.findByProviderEvent(tenant,connection,event);
        if(prior.isPresent()) {
            if(!Objects.equals(prior.get().payloadHash(),payloadHash)) {
                durable.recordRejectedReplay(prior.get(),payloadHash,"WEBHOOK_REPLAY_PAYLOAD_MISMATCH");
                throw new IllegalStateException("WEBHOOK_REPLAY_CONFLICT");
            }
            return new ProviderWebhookReceipt(durable.recordIdenticalReplay(prior.get(),"SYSTEM"),null,null,null,true);
        }
        boolean nonceAccepted=nonce!=null&&!nonce.isBlank()&&repository.findByNonce(tenant,connection,nonce.trim()).isEmpty();
        if(!nonceAccepted)throw new IllegalStateException("WEBHOOK_NONCE_REPLAYED");

        ProviderWebhookInboxEntry inbox=new ProviderWebhookInboxEntry(tenant,"provider-webhook-inbox-"+UUID.randomUUID(),connection,
                value(providerType,"UNKNOWN"),event,value(eventType,"ISSUE_UPDATED"),externalProjectId,externalIssueId,externalIssueKey,
                trim(nonce),timestamp,true,true,true,true,true,payload,payloadHash,ProviderWebhookInboxStatus.VERIFIED,0,0,null,
                null,null,null,null,null,now,null,null,null,1,value(correlationId,"corr-"+UUID.randomUUID()));
        ProviderWebhookInboxEntry committed=durable.persistVerifiedInbox(inbox); // Transaction A
        if(!committed.inboxId().equals(inbox.inboxId())) {
            if(!Objects.equals(committed.payloadHash(),payloadHash))throw new IllegalStateException("WEBHOOK_REPLAY_CONFLICT");
            return new ProviderWebhookReceipt(durable.recordIdenticalReplay(committed,"SYSTEM"),null,null,null,true);
        }
        try {
            ExternalIssueObservation observation=new ExternalIssueObservation(tenant,
                    "external-observation-"+ExternalObservationNormalizer.sha256(committed.inboxId()).substring(0,32),committed.inboxId(),
                    connection,committed.providerType(),event,providerEventSequence,trim(providerChangeVersion),externalProjectId,
                    required(externalIssueId,"externalIssueId"),externalIssueKey,value(externalIssueStatus,"UNKNOWN"),canonical.rawJson(),
                    canonical.canonicalJson(),canonical.canonicalHash(),canonical.profileVersion(),
                    ExternalObservationNormalizer.sha256(value(providerIdentity,"UNKNOWN")),mappingSchemaHash,timestamp,now(),committed.correlationId());
            ExternalIssueObservation immutable=durable.persistObservation(observation); // Transaction B
            return stateApplication.apply(committed,immutable); // Transaction C
        } catch (RuntimeException ex) {
            ProviderWebhookInboxEntry current=repository.findInbox(tenant,committed.inboxId()).orElse(committed);
            if(current.status()!=ProviderWebhookInboxStatus.PROCESSED&&current.status()!=ProviderWebhookInboxStatus.DEAD_LETTER)
                durable.markFailure(current,safeCode(ex.getMessage()),isRetryable(ex),maxAttempts);
            throw ex;
        }
    }

    /** Compatibility overload for Provider payloads without explicit ordering metadata. */
    public ProviderWebhookReceipt receive(String tenantId,String connectionId,String providerType,String providerEventId,String eventType,
            String externalProjectId,String externalIssueId,String externalIssueKey,String externalIssueStatus,String providerTimestamp,
            String nonce,boolean signatureVerified,String payloadJson,String observedDocumentJson,String providerIdentity,String mappingSchemaHash,
            String correlationId) {
        return receive(tenantId,connectionId,providerType,providerEventId,eventType,externalProjectId,externalIssueId,externalIssueKey,
                externalIssueStatus,providerTimestamp,nonce,signatureVerified,payloadJson,observedDocumentJson,providerIdentity,mappingSchemaHash,
                null,null,correlationId);
    }

    public ExternalIssueConflict resolve(String tenantId,String conflictId,ConflictResolutionPolicy policy,String actorId,String reason,
            long expectedVersion,String idempotencyKey) {
        return conflictResolution.resolve(new ConflictResolutionCommand(tenantId,conflictId,policy,actorId,reason,expectedVersion,idempotencyKey));
    }

    public ProviderWebhookInboxEntry replay(String tenantId,String inboxId,String actorId,String reason) {
        ProviderWebhookInboxEntry current=repository.findInbox(required(tenantId,"tenantId"),required(inboxId,"inboxId"))
                .orElseThrow(()->new IllegalArgumentException("Webhook Inbox not found: "+inboxId));
        return durable.scheduleGovernedReplay(current,required(actorId,"actorId"),required(reason,"reason"));
    }

    public void reconcileDue() {
        for(ProviderWebhookInboxEntry claimed:durable.claimDue(workerId,100,claimLeaseSeconds)) {
            try {
                ExternalIssueObservation observation=repository.findObservationByInbox(claimed.tenantId(),claimed.inboxId())
                        .orElseThrow(()->new IllegalStateException("WEBHOOK_OBSERVATION_MISSING"));
                stateApplication.apply(claimed,observation);
                repository.appendReplayEvidence(new WebhookReplayEvidence(claimed.tenantId(),"webhook-evidence-"+UUID.randomUUID(),claimed.inboxId(),
                        "RECONCILED","APPLIED","OBSERVATION_REAPPLIED",ExternalObservationNormalizer.sha256(observation.observedDocumentHash()+"|RECONCILED"),
                        workerId,now(),claimed.correlationId()));
            } catch (RuntimeException ex) {
                ProviderWebhookInboxEntry current=repository.findInbox(claimed.tenantId(),claimed.inboxId()).orElse(claimed);
                if(current.status()==ProviderWebhookInboxStatus.PROCESSING||current.status()==ProviderWebhookInboxStatus.STALE_CLAIM)
                    durable.markFailure(current,safeCode(ex.getMessage()),true,maxAttempts);
            }
        }
    }

    public List<ProviderWebhookInboxEntry> inbox(String tenantId,ProviderWebhookInboxStatus status,int limit){return repository.listInbox(required(tenantId,"tenantId"),status,limit);}
    public List<ExternalIssueObservation> observations(String tenantId,String connectionId,String externalIssueId,int limit){return repository.listObservations(required(tenantId,"tenantId"),required(connectionId,"connectionId"),required(externalIssueId,"externalIssueId"),limit);}
    public List<ExternalIssueConflict> conflicts(String tenantId,ExternalIssueConflictStatus status,int limit){return repository.listConflicts(required(tenantId,"tenantId"),status,limit);}
    public List<ExternalIssueConflictEvent> conflictEvents(String tenantId,String conflictId,int limit){return repository.listConflictEvents(required(tenantId,"tenantId"),required(conflictId,"conflictId"),limit);}
    public List<WebhookReplayEvidence> replayEvidence(String tenantId,String inboxId,int limit){return repository.listReplayEvidence(required(tenantId,"tenantId"),required(inboxId,"inboxId"),limit);}

    private boolean isRetryable(RuntimeException ex){String code=safeCode(ex.getMessage());return !(code.contains("SENSITIVE_FIELD")||code.contains("INVALID")||code.contains("REPLAY_CONFLICT"));}
    private String safeCode(String value){if(value==null||value.isBlank())return "WEBHOOK_PROCESSING_FAILED";String code=value.split(":",2)[0].trim();return code.length()>128?code.substring(0,128):code;}
    private boolean providerMatches(IntegrationProviderType expected,String actual){return expected!=null&&actual!=null&&expected.name().replace("_ISSUES","").equalsIgnoreCase(actual.replace("_ISSUES",""));}
    private OffsetDateTime parseTimestamp(String value){if(value==null||value.isBlank())return null;try{return OffsetDateTime.parse(value.trim());}catch(Exception ignored){}try{return OffsetDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(value.trim())),ZoneOffset.UTC);}catch(Exception ignored){return null;}}
    private String safeJson(String value){String json=value==null||value.isBlank()?"{}":value.trim();if(!(json.startsWith("{")||json.startsWith("[")))throw new IllegalArgumentException("Webhook payload must be JSON.");return json;}
    private String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required.");return value.trim();}
    private String value(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    private String trim(String value){return value==null?null:value.trim();}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
