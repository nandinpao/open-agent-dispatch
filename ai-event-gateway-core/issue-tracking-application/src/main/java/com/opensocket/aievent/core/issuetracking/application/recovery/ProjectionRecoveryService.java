package com.opensocket.aievent.core.issuetracking.application.recovery;

import com.opensocket.aievent.core.issuetracking.background.IssueBackgroundAuthorizationPort;
import com.opensocket.aievent.core.issuetracking.background.IssueBackgroundExecutionAuthorization;
import com.opensocket.aievent.core.issuetracking.recovery.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionRecoveryService {
    private final ProjectionRecoveryRepository repo;
    private final ProjectionProviderRecoveryGateway gateway;
    private final ProjectionRecoveryEventLedger ledger;
    private final Optional<IssueBackgroundAuthorizationPort> background;
    private final boolean backgroundRequired;

    public ProjectionRecoveryService(ProjectionRecoveryRepository repo,Optional<ProjectionProviderRecoveryGateway> gateway,ProjectionRecoveryEventLedger ledger){this(repo,gateway,ledger,Optional.empty(),false);}
    @Autowired public ProjectionRecoveryService(ProjectionRecoveryRepository repo,Optional<ProjectionProviderRecoveryGateway> gateway,ProjectionRecoveryEventLedger ledger,Optional<IssueBackgroundAuthorizationPort> background,@Value("${resource-access.background-enabled:false}")boolean required){this.repo=repo;this.gateway=gateway.orElseGet(UnavailableProjectionProviderRecoveryGateway::new);this.ledger=ledger;this.background=background;this.backgroundRequired=required;}

    @Transactional
    public ProjectionRecoveryCase recover(String tenant,String workId,String actor,String reason,String correlation){
        var work=repo.findWork(req(tenant),req(workId)).orElseThrow(()->new IllegalArgumentException("Projection work not found."));
        var prior=repo.findOpenCase(tenant,workId,ProjectionRecoveryCaseType.RESPONSE_LOST);if(prior.isPresent())return prior.get();
        var lane=repo.findLane(tenant,work.laneId()).orElseThrow(()->new IllegalStateException("Projection lane not found."));
        IssueBackgroundExecutionAuthorization authorization=authorize(tenant,"ISSUE_PROJECTION_READBACK",lane.connectionId(),lane.projectMappingId(),correlation);
        try {
            authorization=checkpoint(authorization,"BEFORE_SIDE_EFFECT",correlation);
            var result=gateway.readback(new ProviderReadbackCommand(tenant,lane.connectionId(),lane.projectMappingId(),work.projectionId(),work.externalIdempotencyMarker(),work.payloadHash(),null,correlation));
            authorization=checkpoint(authorization,"BEFORE_RESULT_COMMIT",correlation);
            ProjectionRecoveryCaseType type;ProjectionRecoveryCaseStatus status;String code;
            if(result.status()==ProviderReadbackStatus.MATCHED){type=ProjectionRecoveryCaseType.RESPONSE_LOST;status=ProjectionRecoveryCaseStatus.RESOLVED;code="READBACK_MATCHED";}
            else if(result.status()==ProviderReadbackStatus.INDEX_DELAY||result.status()==ProviderReadbackStatus.NOT_FOUND){type=ProjectionRecoveryCaseType.PROVIDER_INDEX_DELAY;status=ProjectionRecoveryCaseStatus.FAILED_RETRYABLE;code="PROVIDER_INDEX_DELAY";}
            else if(result.status()==ProviderReadbackStatus.CONFLICT){type=ProjectionRecoveryCaseType.DUPLICATE_ISSUE;status=ProjectionRecoveryCaseStatus.DECISION_REQUIRED;code="DUPLICATE_OR_CONFLICTING_PROVIDER_OBJECT";}
            else{type=ProjectionRecoveryCaseType.RESPONSE_LOST;status=result.status()==ProviderReadbackStatus.PERMANENT_FAILURE?ProjectionRecoveryCaseStatus.DEAD_LETTER:ProjectionRecoveryCaseStatus.FAILED_RETRYABLE;code=result.failureCode()==null?"READBACK_FAILED":result.failureCode();}
            OffsetDateTime now=now();var c=new ProjectionRecoveryCase(tenant,"prc-"+UUID.randomUUID(),type,status,work.laneId(),work.workId(),work.projectionId(),lane.connectionId(),lane.projectMappingId(),work.externalIdempotencyMarker(),result.externalIssueId(),work.payloadHash(),result.observedHash(),code,result.safeMessage()==null?"Provider readback evaluated.":result.safeMessage(),1,status==ProjectionRecoveryCaseStatus.FAILED_RETRYABLE?now.plusSeconds(30):null,1,now,now,correlation);
            repo.saveCase(c);ledger.append(tenant,"RECOVERY_CASE",c.caseId(),status==ProjectionRecoveryCaseStatus.RESOLVED?ProjectionRecoveryEventType.READBACK_MATCHED:ProjectionRecoveryEventType.RECOVERY_OPENED,actor,reason,"{\"status\":\""+status+"\"}",correlation);complete(authorization,correlation);return c;
        } catch(RuntimeException ex){revoke(authorization,"ISSUE_PROJECTION_READBACK_ABORTED",correlation);throw ex;}
    }

    @Transactional
    public ProjectionRecoveryCase repair(String tenant,String caseId,long expectedVersion,boolean dryRun,String idempotencyKey,String actor,String reason,String correlation){
        var current=repo.findCase(req(tenant),req(caseId)).orElseThrow(()->new IllegalArgumentException("Recovery case not found."));if(current.version()!=expectedVersion)throw new IllegalStateException("RECOVERY_CASE_VERSION_CONFLICT");
        IssueBackgroundExecutionAuthorization authorization=authorize(tenant,"ISSUE_PROJECTION_REPAIR",current.connectionId(),current.projectMappingId(),correlation);
        try {
            authorization=checkpoint(authorization,"BEFORE_SIDE_EFFECT",correlation);
            var result=gateway.repair(new ReconciliationRepairCommand(tenant,current.connectionId(),current.projectMappingId(),current.caseType(),current.projectionId(),current.externalIssueId(),current.expectedHash(),dryRun,req(idempotencyKey),correlation));
            authorization=checkpoint(authorization,"BEFORE_RESULT_COMMIT",correlation);
            OffsetDateTime now=now();var status=result.successful()?ProjectionRecoveryCaseStatus.VERIFYING:result.retryable()?ProjectionRecoveryCaseStatus.FAILED_RETRYABLE:ProjectionRecoveryCaseStatus.DECISION_REQUIRED;
            var next=new ProjectionRecoveryCase(current.tenantId(),current.caseId(),current.caseType(),status,current.laneId(),current.workId(),current.projectionId(),current.connectionId(),current.projectMappingId(),current.externalIdempotencyMarker(),current.externalIssueId(),current.expectedHash(),current.observedHash(),result.failureCode()==null?"REPAIR_REQUESTED":result.failureCode(),result.safeMessage(),current.attemptCount()+1,result.retryable()?now.plusSeconds(60):null,current.version()+1,current.createdAt(),now,correlation);
            if(!repo.saveCaseExpectedVersion(next,current.version()))throw new IllegalStateException("RECOVERY_CASE_VERSION_CONFLICT");
            ledger.append(tenant,"RECOVERY_CASE",caseId,ProjectionRecoveryEventType.REPAIR_REQUESTED,actor,reason,"{\"dryRun\":"+dryRun+"}",correlation);complete(authorization,correlation);return next;
        } catch(RuntimeException ex){revoke(authorization,"ISSUE_PROJECTION_REPAIR_ABORTED",correlation);throw ex;}
    }

    public List<ProjectionRecoveryCase> list(String tenant,ProjectionRecoveryCaseStatus status,int limit){return repo.listCases(req(tenant),status,Math.max(1,Math.min(limit,1000)));}
    private IssueBackgroundExecutionAuthorization authorize(String tenant,String job,String connection,String mapping,String correlation){String type=mapping==null||mapping.isBlank()?"ISSUE_CONNECTION":"ISSUE_PROJECT_MAPPING";String id=mapping==null||mapping.isBlank()?connection:mapping;if(background.isPresent())return background.get().authorize(tenant,job,type,id,"integration.issue.worker.execute","Execute governed Issue projection recovery",safe(correlation));if(backgroundRequired)throw new IllegalStateException("BACKGROUND_RESOURCE_GUARD_UNAVAILABLE");return null;}
    private IssueBackgroundExecutionAuthorization checkpoint(IssueBackgroundExecutionAuthorization a,String phase,String correlation){return a==null?null:background.orElseThrow().checkpoint(a,phase,safe(correlation));}
    private void complete(IssueBackgroundExecutionAuthorization a,String correlation){if(a!=null)background.orElseThrow().complete(a,safe(correlation));}
    private void revoke(IssueBackgroundExecutionAuthorization a,String reason,String correlation){if(a!=null)background.ifPresent(b->b.revoke(a,reason,safe(correlation)));}
    private String req(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("Required value missing.");return v.trim();}
    private static String safe(String v){return v==null||v.isBlank()?"background-worker":v.trim();}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
