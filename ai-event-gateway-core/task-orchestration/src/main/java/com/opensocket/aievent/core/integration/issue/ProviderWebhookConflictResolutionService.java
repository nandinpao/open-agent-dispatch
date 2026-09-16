package com.opensocket.aievent.core.integration.issue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionStateService;
import com.opensocket.aievent.core.integration.issue.webhook.*;
import com.opensocket.aievent.core.integration.identity.IntegrationRiskLevel;
import com.opensocket.aievent.core.issuetracking.application.change.ProviderActionCandidateService;
import com.opensocket.aievent.core.issuetracking.change.ProviderActionCandidateType;

/** Governed, optimistic and idempotent conflict decision boundary. */
@Service
public class ProviderWebhookConflictResolutionService {
    private final ProviderWebhookReliabilityRepository repository;
    private final IssueProjectionStateService projectionService;
    private final ExternalIssueConflictLedger ledger;
    private final ProviderActionCandidateService candidates;

    public ProviderWebhookConflictResolutionService(ProviderWebhookReliabilityRepository repository,
            IssueProjectionStateService projectionService,ExternalIssueConflictLedger ledger,
            ProviderActionCandidateService candidates) {
        this.repository=repository; this.projectionService=projectionService; this.ledger=ledger;
        this.candidates=candidates;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ExternalIssueConflict resolve(ConflictResolutionCommand command) {
        String tenant=required(command.tenantId(),"tenantId"),conflictId=required(command.conflictId(),"conflictId");
        String actor=required(command.actorId(),"actorId"),reason=required(command.reason(),"reason"),key=required(command.idempotencyKey(),"idempotencyKey");
        ConflictResolutionPolicy policy=command.policy()==null?ConflictResolutionPolicy.MANUAL_REVIEW:command.policy();
        String requestHash=ExternalObservationNormalizer.sha256(conflictId+"|"+policy+"|"+actor+"|"+reason+"|"+command.expectedVersion());

        var replay=repository.findConflictByResolutionIdempotency(tenant,key);
        if(replay.isPresent()) {
            ExternalIssueConflict existing=replay.get();
            if(!existing.conflictId().equals(conflictId)||!requestHash.equals(existing.resolutionRequestHash()))
                throw new IllegalStateException("CONFLICT_RESOLUTION_IDEMPOTENCY_CONFLICT");
            return existing;
        }

        ExternalIssueConflict current=repository.findConflict(tenant,conflictId)
                .orElseThrow(()->new IllegalArgumentException("Conflict not found: "+conflictId));
        if(current.version()!=command.expectedVersion()) throw new IllegalStateException("CONFLICT_EXPECTED_VERSION_MISMATCH");
        if(isTerminal(current.status())) throw new IllegalStateException("CONFLICT_ALREADY_TERMINAL");
        OffsetDateTime now=now();

        ExternalIssueConflictStatus decisionStatus=switch(policy) {
            case IGNORE -> ExternalIssueConflictStatus.IGNORED;
            case OPENDISPATCH_WINS -> ExternalIssueConflictStatus.RESOLUTION_PENDING;
            case PROVIDER_WINS,MERGE,MANUAL_REVIEW -> ExternalIssueConflictStatus.WAIT_HUMAN;
        };
        boolean terminal=decisionStatus==ExternalIssueConflictStatus.IGNORED;
        ExternalIssueConflict decided=copy(current,policy,decisionStatus,now,terminal?now:null,actor,reason,key,requestHash,current.version()+1);
        saveExpected(decided,current.version());
        ledger.append(decided,ExternalIssueConflictEventType.POLICY_SELECTED,actor,"POLICY_"+policy.name(),"{\"policy\":\""+policy.name()+"\"}");

        if(policy==ConflictResolutionPolicy.IGNORE) {
            ledger.append(decided,ExternalIssueConflictEventType.IGNORED,actor,"CONFLICT_IGNORED",decided.stateDiffJson());
            return decided;
        }
        if(policy==ConflictResolutionPolicy.PROVIDER_WINS || policy==ConflictResolutionPolicy.MERGE) {
            var candidate=candidates.create(tenant,current.conflictId(),current.observationId(),current.connectionId(),
                    current.externalProjectId(),current.externalIssueId(),ProviderActionCandidateType.APPLY_FIELD_UPDATE,
                    IntegrationRiskLevel.HIGH,controlledCommandJson(current,policy),current.evidenceHash(),true,true,
                    "conflict-resolution:"+key,current.correlationId());
            ledger.append(decided,ExternalIssueConflictEventType.WAIT_HUMAN,actor,"CONTROLLED_COMMAND_CANDIDATE_CREATED",
                    "{\"candidateId\":\""+escape(candidate.candidateId())+"\",\"status\":\""+candidate.status().name()+"\"}");
            return decided;
        }
        if(policy!=ConflictResolutionPolicy.OPENDISPATCH_WINS || current.projectionId()==null || current.projectionId().isBlank()) {
            ledger.append(decided,ExternalIssueConflictEventType.WAIT_HUMAN,actor,"CONTROLLED_COMMAND_REQUIRED","{\"nextPhase\":\"3G Human Action Candidate\"}");
            return decided;
        }

        ledger.append(decided,ExternalIssueConflictEventType.RESOLUTION_STARTED,actor,"PROJECTION_RETRY_REQUESTED","{\"projectionId\":\""+escape(current.projectionId())+"\"}");
        try {
            projectionService.retry(tenant,current.projectionId(),"Governed conflict resolution: "+reason);
            ExternalIssueConflict verifying=copy(decided,policy,ExternalIssueConflictStatus.VERIFYING,now,null,actor,reason,key,requestHash,decided.version()+1);
            saveExpected(verifying,decided.version());
            ledger.append(verifying,ExternalIssueConflictEventType.VERIFICATION_STARTED,actor,"PROVIDER_READBACK_REQUIRED","{\"projectionId\":\""+escape(current.projectionId())+"\"}");
            return verifying;
        } catch (RuntimeException ex) {
            ExternalIssueConflict failed=copy(decided,policy,ExternalIssueConflictStatus.WAIT_HUMAN,now,null,actor,reason,key,requestHash,decided.version()+1);
            saveExpected(failed,decided.version());
            ledger.append(failed,ExternalIssueConflictEventType.RESOLUTION_FAILED,actor,"PROJECTION_RETRY_FAILED","{\"safeError\":\""+escape(safe(ex.getMessage()))+"\"}");
            return failed;
        }
    }

    private String controlledCommandJson(ExternalIssueConflict conflict,ConflictResolutionPolicy policy) {
        return "{\"operation\":\"APPLY_EXTERNAL_CONFLICT_DECISION\",\"policy\":\""+policy.name()+
                "\",\"conflictId\":\""+escape(conflict.conflictId())+"\",\"observationId\":\""+
                escape(conflict.observationId())+"\",\"externalIssueId\":\""+escape(conflict.externalIssueId())+
                "\",\"stateDiff\":"+(conflict.stateDiffJson()==null||conflict.stateDiffJson().isBlank()?"{}":conflict.stateDiffJson())+"}";
    }
    private void saveExpected(ExternalIssueConflict next,long expected) {
        if(!repository.saveConflictExpectedVersion(next,expected)) throw new IllegalStateException("CONFLICT_EXPECTED_VERSION_MISMATCH");
    }
    private ExternalIssueConflict copy(ExternalIssueConflict c,ConflictResolutionPolicy policy,ExternalIssueConflictStatus status,
            OffsetDateTime decisionAt,OffsetDateTime resolvedAt,String actor,String reason,String key,String requestHash,long version) {
        return new ExternalIssueConflict(c.tenantId(),c.conflictId(),c.connectionId(),c.externalProjectId(),c.externalIssueId(),c.taskIssueLinkId(),c.projectionId(),c.observationId(),c.classification(),policy,status,c.desiredDocumentHash(),c.observedDocumentHash(),c.stateDiffJson(),c.evidenceHash(),c.reasonCode(),c.detectedAt(),decisionAt,resolvedAt,actor,reason,key,requestHash,version,c.correlationId());
    }
    private boolean isTerminal(ExternalIssueConflictStatus status){return status==ExternalIssueConflictStatus.RESOLVED||status==ExternalIssueConflictStatus.IGNORED||status==ExternalIssueConflictStatus.SUPERSEDED;}
    private String required(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" is required.");return value.trim();}
    private String safe(String value){if(value==null||value.isBlank())return "UNSPECIFIED";return value.length()>240?value.substring(0,240):value;}
    private String escape(String value){return value==null?"":value.replace("\\","\\\\").replace("\"","\\\"");}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
