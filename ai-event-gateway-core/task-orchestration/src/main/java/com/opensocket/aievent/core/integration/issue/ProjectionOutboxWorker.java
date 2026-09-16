package com.opensocket.aievent.core.integration.issue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Durable provider projection worker.
 *
 * <p>Phase 3 reliability rule: provider success is not equivalent to canonical completion.
 * ACKNOWLEDGED is emitted only after the provider result has been materialized successfully in
 * OpenDispatch. A provider success followed by a local failure is kept in VERIFYING so retries
 * perform readback/reconciliation instead of blindly sending the provider command again.</p>
 */
@Deprecated(forRemoval = true)
public class ProjectionOutboxWorker {
    private static final Logger log=LoggerFactory.getLogger(ProjectionOutboxWorker.class);
    private static final Pattern RETRY_AFTER=Pattern.compile("(?i)retryAfterSeconds[=:](\\d+)");

    private final ProjectionOutboxReliabilityService reliability;
    private final IssueSyncRepository legacy;
    private final IssueProjectionService service;
    private final List<IssueSyncProviderGateway> gateways;
    private final List<IssueSyncReadbackGateway> readbacks;
    private final String workerId="projection-outbox-worker-"+UUID.randomUUID();

    public ProjectionOutboxWorker(ProjectionOutboxReliabilityService reliability,
                                  IssueSyncRepository legacy,
                                  IssueProjectionService service,
                                  List<IssueSyncProviderGateway> gateways,
                                  List<IssueSyncReadbackGateway> readbacks){
        this.reliability=reliability;
        this.legacy=legacy;
        this.service=service;
        this.gateways=gateways.stream().sorted(Comparator.comparingInt(IssueSyncProviderGateway::priority)).toList();
        this.readbacks=readbacks.stream().sorted(Comparator.comparingInt(IssueSyncReadbackGateway::priority)).toList();
    }

    @Scheduled(fixedDelayString="${integration-sync.projection-worker-interval-ms:5000}", scheduler="projectionOperationalScheduler")
    public void scheduledRun(){runOnce(50);}

    public int runOnce(int limit){
        int processed=0;
        for(ProjectionOutboxReliability work:reliability.claimDue(workerId,limit)){
            try{
                execute(work);
            }catch(RuntimeException failure){
                log.error("projection_outbox_item_unhandled tenantId={} outboxId={} projectionId={} status={} workerId={} lastAttemptId={} errorClass={} safeError={} claimWillExpire=true",
                        work.tenantId(),work.outboxId(),work.projectionId(),work.status(),workerId,work.lastAttemptId(),
                        failure.getClass().getName(),safeFailure(failure),failure);
            }
            if(++processed>=limit)break;
        }
        return processed;
    }

    private void execute(ProjectionOutboxReliability claimed){
        IntegrationOutboxEntry item=legacy.findOutbox(claimed.tenantId(),claimed.outboxId())
                .orElseThrow(()->new IllegalStateException("LEGACY_OUTBOX_NOT_FOUND"));

        // A previously-sent uncertain command must always verify/read back before any resend.
        if(claimed.status()==ProjectionOutboxReliabilityStatus.CLAIMED&&claimed.sentAt()!=null){
            log.info("projection_outbox_resume_verification tenantId={} outboxId={} projectionId={} correlationId={} sentAt={} workerId={}",
                    item.tenantId(),item.outboxId(),claimed.projectionId(),item.correlationId(),claimed.sentAt(),workerId);
            verify(claimed,item);
            return;
        }

        IntegrationOutboxEntry providerItem=withMarker(item,claimed);
        IssueSyncProviderGateway gateway=gateways.stream().filter(g->g.supports(providerItem)).findFirst()
                .orElseThrow(()->new IllegalStateException("NO_PROVIDER_GATEWAY"));
        OffsetDateTime started=now();
        int providerAttemptNo=reliability.nextAttemptNo(item.tenantId(),item.outboxId());
        String attemptId="provider-attempt-"+UUID.randomUUID();
        ProjectionOutboxReliability sending=reliability.transition(claimed,ProjectionOutboxReliabilityStatus.SENDING,
                workerId,null,null,null,null,attemptId);

        ProviderSyncResult result;
        try{
            service.startAttempt(item.tenantId(),item.outboxId(),workerId);
            result=gateway.execute(providerItem);
        }catch(RuntimeException failure){
            log.error("projection_provider_execution_failed tenantId={} taskId={} projectionId={} outboxId={} attemptId={} attemptNo={} connectionId={} projectMappingId={} operation={} correlationId={} causationId={} retryable=true errorClass={} safeError={}",
                    item.tenantId(),item.taskId(),claimed.projectionId(),item.outboxId(),attemptId,providerAttemptNo,
                    item.connectionId(),item.projectMappingId(),item.operationType(),item.correlationId(),item.causationId(),
                    failure.getClass().getName(),safeFailure(failure),failure);
            result=ProviderSyncResult.failure(true,null,"ISSUE_PROVIDER_EXECUTION_FAILED",safeFailure(failure));
        }

        if(result!=null&&result.success()){
            ProjectionOutboxReliability sent=reliability.transition(sending,ProjectionOutboxReliabilityStatus.SENT,
                    workerId,null,null,null,null,attemptId);
            reliability.appendAttempt(sent,providerAttemptNo,workerId,"SENT",result,null,started,now(),item.correlationId());
            log.info("projection_provider_sent tenantId={} taskId={} projectionId={} outboxId={} attemptId={} attemptNo={} providerStatus={} externalIssueId={} correlationId={} workerId={}",
                    item.tenantId(),item.taskId(),claimed.projectionId(),item.outboxId(),attemptId,providerAttemptNo,
                    result.providerStatus(),result.externalIssueId(),item.correlationId(),workerId);
            try{
                service.complete(item.tenantId(),item.outboxId(),workerId,result);
            }catch(RuntimeException failure){
                // Provider side is known successful. Never re-send because local materialization failed.
                OffsetDateTime retryAt=now().plusMinutes(1);
                reliability.transition(sent,ProjectionOutboxReliabilityStatus.VERIFYING,workerId,
                        "LOCAL_PROJECTION_COMPLETION_FAILED",safeFailure(failure),retryAt,null,attemptId);
                log.error("projection_provider_success_local_completion_failed tenantId={} taskId={} projectionId={} outboxId={} attemptId={} externalIssueId={} correlationId={} retryAt={} authoritativeAck=false nextAction=READBACK_RECONCILIATION errorClass={} safeError={}",
                        item.tenantId(),item.taskId(),claimed.projectionId(),item.outboxId(),attemptId,result.externalIssueId(),
                        item.correlationId(),retryAt,failure.getClass().getName(),safeFailure(failure),failure);
                return;
            }
            reliability.transition(sent,ProjectionOutboxReliabilityStatus.ACKNOWLEDGED,workerId,null,null,null,null,attemptId);
            log.info("projection_outbox_acknowledged tenantId={} taskId={} projectionId={} outboxId={} attemptId={} correlationId={} providerAndCanonicalStateConfirmed=true",
                    item.tenantId(),item.taskId(),claimed.projectionId(),item.outboxId(),attemptId,item.correlationId());
            return;
        }

        ProviderSyncResult failed=result==null
                ?ProviderSyncResult.failure(true,null,"EMPTY_PROVIDER_RESPONSE","Provider gateway returned no result.")
                :result;
        if("PROVIDER_RESPONSE_LOST".equals(failed.errorCode())){
            ProjectionOutboxReliability verifying=reliability.transition(sending,ProjectionOutboxReliabilityStatus.VERIFYING,
                    workerId,failed.errorCode(),failed.errorMessage(),now(),null,attemptId);
            reliability.appendAttempt(verifying,providerAttemptNo,workerId,"RESPONSE_UNCERTAIN",failed,now(),started,now(),item.correlationId());
            log.warn("projection_provider_response_uncertain tenantId={} taskId={} projectionId={} outboxId={} attemptId={} correlationId={} nextAction=READBACK",
                    item.tenantId(),item.taskId(),claimed.projectionId(),item.outboxId(),attemptId,item.correlationId());
            verify(verifying,providerItem);
            return;
        }

        OffsetDateTime retryAt=retryAt(failed,providerAttemptNo);
        boolean retry=failed.retryable()&&providerAttemptNo<item.maxAttempts();
        ProjectionOutboxReliabilityStatus target=retry
                ?ProjectionOutboxReliabilityStatus.FAILED_RETRYABLE
                :ProjectionOutboxReliabilityStatus.DEAD_LETTER;
        ProjectionOutboxReliability ended=reliability.transition(sending,target,workerId,
                failed.errorCode(),failed.errorMessage(),retryAt,
                failed.providerStatus()!=null&&failed.providerStatus()==429?retryAt:null,attemptId);
        reliability.appendAttempt(ended,providerAttemptNo,workerId,target.name(),failed,retryAt,started,now(),item.correlationId());
        service.fail(item.tenantId(),item.outboxId(),workerId,failed);
        log.warn("projection_provider_failed tenantId={} taskId={} projectionId={} outboxId={} attemptId={} attemptNo={} providerStatus={} retryable={} targetStatus={} retryAt={} errorCode={} safeError={} correlationId={}",
                item.tenantId(),item.taskId(),claimed.projectionId(),item.outboxId(),attemptId,providerAttemptNo,
                failed.providerStatus(),failed.retryable(),target,retryAt,failed.errorCode(),safe(failed.errorMessage()),item.correlationId());
    }

    private void verify(ProjectionOutboxReliability work,IntegrationOutboxEntry item){
        IssueSyncReadbackGateway gateway=readbacks.stream().filter(g->g.supportsReadback(item)).findFirst().orElse(null);
        if(gateway==null){
            reliability.transition(work,ProjectionOutboxReliabilityStatus.DEAD_LETTER,workerId,
                    "PROVIDER_READBACK_GATEWAY_REQUIRED","Provider command may already have been accepted; automatic resend is blocked because readback is unavailable.",null,null,work.lastAttemptId());
            log.error("projection_readback_gateway_missing tenantId={} taskId={} projectionId={} outboxId={} correlationId={} authoritativeAck=false automaticResendBlocked=true nextAction=MANUAL_RECONCILIATION reasonCode=PROVIDER_READBACK_GATEWAY_REQUIRED",
                    item.tenantId(),item.taskId(),work.projectionId(),item.outboxId(),item.correlationId());
            return;
        }
        ProjectionProviderReadbackResult result;
        try{
            result=gateway.readback(item,work.externalIdempotencyMarker(),work.providerRequestFingerprint());
        }catch(RuntimeException failure){
            OffsetDateTime retryAt=now().plusMinutes(1);
            reliability.transition(work,ProjectionOutboxReliabilityStatus.VERIFYING,workerId,
                    "PROVIDER_READBACK_EXECUTION_FAILED",safeFailure(failure),retryAt,null,work.lastAttemptId());
            log.error("projection_readback_execution_failed tenantId={} taskId={} projectionId={} outboxId={} correlationId={} retryAt={} authoritativeAck=false errorClass={} safeError={}",
                    item.tenantId(),item.taskId(),work.projectionId(),item.outboxId(),item.correlationId(),retryAt,
                    failure.getClass().getName(),safeFailure(failure),failure);
            return;
        }

        reliability.appendReadback(work,result,item.correlationId());
        if(result.status()==ProjectionReadbackStatus.MATCHED
                &&Objects.equals(work.providerRequestFingerprint(),result.observedFingerprint())){
            ProviderSyncResult success=ProviderSyncResult.success(200,result.externalIssueId(),result.externalIssueKey(),
                    result.externalIssueUrl(),result.externalIssueStatus(),null,result.safeSummary());
            try{
                service.complete(item.tenantId(),item.outboxId(),workerId,success);
            }catch(RuntimeException failure){
                OffsetDateTime retryAt=now().plusMinutes(1);
                reliability.transition(work,ProjectionOutboxReliabilityStatus.VERIFYING,workerId,
                        "LOCAL_PROJECTION_COMPLETION_FAILED",safeFailure(failure),retryAt,null,work.lastAttemptId());
                log.error("projection_readback_matched_local_completion_failed tenantId={} taskId={} projectionId={} outboxId={} externalIssueId={} correlationId={} retryAt={} authoritativeAck=false nextAction=READBACK_RECONCILIATION errorClass={} safeError={}",
                        item.tenantId(),item.taskId(),work.projectionId(),item.outboxId(),result.externalIssueId(),item.correlationId(),
                        retryAt,failure.getClass().getName(),safeFailure(failure),failure);
                return;
            }
            reliability.transition(work,ProjectionOutboxReliabilityStatus.ACKNOWLEDGED,workerId,null,null,null,null,work.lastAttemptId());
            log.info("projection_readback_acknowledged tenantId={} taskId={} projectionId={} outboxId={} externalIssueId={} correlationId={} providerAndCanonicalStateConfirmed=true",
                    item.tenantId(),item.taskId(),work.projectionId(),item.outboxId(),result.externalIssueId(),item.correlationId());
        }else if(result.status()==ProjectionReadbackStatus.CONFLICT){
            reliability.transition(work,ProjectionOutboxReliabilityStatus.DEAD_LETTER,workerId,
                    "PROVIDER_READBACK_CONFLICT",result.safeSummary(),null,null,work.lastAttemptId());
            log.error("projection_readback_conflict tenantId={} taskId={} projectionId={} outboxId={} correlationId={} authoritativeAck=false reasonCode=PROVIDER_READBACK_CONFLICT safeSummary={}",
                    item.tenantId(),item.taskId(),work.projectionId(),item.outboxId(),item.correlationId(),safe(result.safeSummary()));
        }else{
            OffsetDateTime retryAt=now().plusMinutes(1);
            reliability.transition(work,ProjectionOutboxReliabilityStatus.FAILED_RETRYABLE,workerId,
                    result.reasonCode(),result.safeSummary(),retryAt,null,work.lastAttemptId());
            log.warn("projection_readback_not_confirmed tenantId={} taskId={} projectionId={} outboxId={} correlationId={} readbackStatus={} reasonCode={} retryAt={} authoritativeAck=false",
                    item.tenantId(),item.taskId(),work.projectionId(),item.outboxId(),item.correlationId(),result.status(),result.reasonCode(),retryAt);
        }
    }

    private IntegrationOutboxEntry withMarker(IntegrationOutboxEntry item,ProjectionOutboxReliability work){
        String payload=item.payloadJson()==null?"{}":item.payloadJson().trim();
        if(payload.startsWith("{")&&payload.endsWith("}")){
            String body=payload.substring(1,payload.length()-1).trim();
            String metadata="\"openDispatchIdempotencyMarker\":\""+escape(work.externalIdempotencyMarker())
                    +"\",\"providerRequestFingerprint\":\""+escape(work.providerRequestFingerprint())+"\"";
            payload="{"+(body.isBlank()?metadata:body+","+metadata)+"}";
        }
        return new IntegrationOutboxEntry(item.tenantId(),item.outboxId(),item.aggregateType(),item.aggregateId(),item.taskId(),
                item.taskIssueLinkId(),item.issueRelationshipId(),item.connectionId(),item.projectMappingId(),item.operationType(),
                item.eventType(),payload,item.payloadHash(),item.idempotencyKey(),item.status(),item.priority(),item.attemptCount(),
                item.maxAttempts(),item.nextAttemptAt(),item.claimedBy(),item.claimUntil(),item.lastErrorCode(),item.lastErrorMessage(),
                item.correlationId(),item.causationId(),item.createdAt(),item.updatedAt(),item.completedAt());
    }

    private String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
    private OffsetDateTime retryAt(ProviderSyncResult result,int attempt){
        long seconds=Math.min(3600L,(1L<<Math.min(attempt,10))*5L);
        if(result.providerStatus()!=null&&result.providerStatus()==429){
            Matcher matcher=RETRY_AFTER.matcher(String.valueOf(result.responseSummary()));
            if(matcher.find())seconds=Math.max(seconds,Long.parseLong(matcher.group(1)));else seconds=Math.max(seconds,60);
        }
        return now().plusSeconds(seconds);
    }
    private String safeFailure(RuntimeException failure){
        if(failure==null)return "RuntimeException";
        String message=safe(failure.getMessage());
        return failure.getClass().getSimpleName()+(message==null||message.isBlank()?"":": "+message);
    }
    private String safe(String value){
        if(value==null)return null;
        String redacted=value.replaceAll("(?i)(authorization|password|secret|token|credential|cookie)\\s*[:=]\\s*[^,;\\s]+","$1=[REDACTED]");
        return redacted.length()>1000?redacted.substring(0,1000):redacted;
    }
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
