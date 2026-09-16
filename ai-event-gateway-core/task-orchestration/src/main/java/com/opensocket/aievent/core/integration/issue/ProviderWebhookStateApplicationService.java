package com.opensocket.aievent.core.integration.issue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.opensocket.aievent.core.integration.issue.projection.*;
import com.opensocket.aievent.core.integration.issue.webhook.*;
import com.opensocket.aievent.core.issue.*;
import com.opensocket.aievent.core.issuetracking.application.change.ExternalChangeIngestionCommand;
import com.opensocket.aievent.core.issuetracking.application.change.ExternalChangeIngestionService;

/** Transaction C: applies one immutable Observation to versioned external state and conflict governance. */
@Service
public class ProviderWebhookStateApplicationService {
    private final ProviderWebhookReliabilityRepository repository;
    private final TaskIssueLinkRepository links;
    private final IssueProjectionStateRepository projections;
    private final IssueProjectionStateService projectionService;
    private final ExternalStateSemanticDiffService diffService;
    private final ProviderWebhookEventOrdering ordering;
    private final ExternalIssueConflictLedger ledger;
    private final ExternalChangeIngestionService externalChanges;

    public ProviderWebhookStateApplicationService(ProviderWebhookReliabilityRepository repository,TaskIssueLinkRepository links,
            IssueProjectionStateRepository projections,IssueProjectionStateService projectionService,
            ExternalStateSemanticDiffService diffService,ProviderWebhookEventOrdering ordering,ExternalIssueConflictLedger ledger,
            ExternalChangeIngestionService externalChanges) {
        this.repository=repository; this.links=links; this.projections=projections; this.projectionService=projectionService;
        this.diffService=diffService; this.ordering=ordering; this.ledger=ledger; this.externalChanges=externalChanges;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public ProviderWebhookReceipt apply(ProviderWebhookInboxEntry claimed,ExternalIssueObservation observation) {
        ProviderWebhookInboxEntry inbox=repository.findInbox(claimed.tenantId(),claimed.inboxId()).orElse(claimed);
        String issueId=required(observation.externalIssueId(),"externalIssueId");
        Optional<ExternalIssueObservedState> previous=repository.findObservedState(inbox.tenantId(),inbox.connectionId(),issueId);

        ProviderWebhookOrderingDecision orderingDecision=previous.map(value->ordering.compare(value,observation)).orElse(ProviderWebhookOrderingDecision.NEWER);
        if (orderingDecision==ProviderWebhookOrderingDecision.DUPLICATE) {
            ProviderWebhookInboxEntry completed=complete(inbox);
            return new ProviderWebhookReceipt(completed,observation,previous.orElseThrow(),null,false);
        }
        if (orderingDecision==ProviderWebhookOrderingDecision.STALE_OR_AMBIGUOUS) {
            ProviderWebhookInboxEntry completed=complete(inbox);
            repository.appendReplayEvidence(evidence(inbox,"OUT_OF_ORDER","OBSERVED_ONLY",
                    "LEGACY_CONFLICT_ARBITRATION_RETIRED_STALE_OR_OUT_OF_ORDER_EVENT",observation.observedDocumentHash()));
            return new ProviderWebhookReceipt(completed,observation,previous.get(),null,false);
        }

        Optional<TaskIssueLink> link=links.findByExternalIssue(inbox.tenantId(),inbox.connectionId(),inbox.externalProjectId(),issueId);
        Optional<IssueProjectionState> projection=link.flatMap(v->projections.findByTaskIssueLink(inbox.tenantId(),v.getLinkId()));
        String desiredHash=projection.map(IssueProjectionState::canonicalDocumentHash).filter(this::notBlank)
                .orElseGet(() -> projection.map(IssueProjectionState::desiredPayloadHash).orElse(null));
        ExternalIssueConflictClassification classification=classify(inbox.eventType(),observation,projection.orElse(null),previous.orElse(null),desiredHash,link.isEmpty());
        ExternalStateSemanticDiff semantic=diffService.compare(null,desiredHash,previous.map(ExternalIssueObservedState::normalizedObservationJson).orElse(null),
                observation.normalizedObservationJson(),observation.observedDocumentHash(),classification);
        OffsetDateTime now=now();
        ExternalIssueObservedState state=new ExternalIssueObservedState(inbox.tenantId(),previous.map(ExternalIssueObservedState::stateId).orElse("external-state-"+UUID.randomUUID()),
                inbox.connectionId(),inbox.externalProjectId(),issueId,inbox.externalIssueKey(),link.map(TaskIssueLink::getLinkId).orElse(null),
                projection.map(IssueProjectionState::projectionId).orElse(null),observation.observationId(),observation.providerEventId(),
                observation.providerEventSequence(),observation.providerChangeVersion(),observation.providerObservedAt(),desiredHash,
                observation.observedDocumentHash(),observation.normalizedObservationJson(),semantic.diffJson(),semantic.diffHash(),
                observation.providerIdentityHash(),observation.mappingSchemaHash(),classification,observation.providerObservedAt(),
                previous.map(v->v.version()+1).orElse(1L),now);

        if (!repository.saveObservedStateIfNewer(state)) {
            ExternalIssueObservedState winner=repository.findObservedState(inbox.tenantId(),inbox.connectionId(),issueId).orElse(state);
            if (Objects.equals(winner.lastAppliedProviderEventId(),observation.providerEventId())) {
                ProviderWebhookInboxEntry completed=complete(inbox);
                return new ProviderWebhookReceipt(completed,observation,winner,null,false);
            }
            ProviderWebhookInboxEntry completed=complete(inbox);
            repository.appendReplayEvidence(evidence(inbox,"CONCURRENT_NEWER_EVENT","OBSERVED_ONLY",
                    "LEGACY_CONFLICT_ARBITRATION_RETIRED_CONCURRENT_NEWER_PROVIDER_EVENT_WON",observation.observedDocumentHash()));
            return new ProviderWebhookReceipt(completed,observation,winner,null,false);
        }

        if (classification!=ExternalIssueConflictClassification.NONE) {
            repository.appendReplayEvidence(evidence(inbox,"EXTERNAL_OBSERVATION","OBSERVED_ONLY",
                    "LEGACY_CONFLICT_ARBITRATION_RETIRED_"+classification.name(),observation.observedDocumentHash()));
        }
        // I0-H: Redmine owns external Issue state. Webhooks remain immutable observations only;
        // no new OpenDispatch conflict arbitration, bidirectional action candidate, or projection retry is created.
        ProviderWebhookInboxEntry completed=complete(inbox);
        return new ProviderWebhookReceipt(completed,observation,state,null,false);
    }

    private ProviderWebhookInboxEntry complete(ProviderWebhookInboxEntry current) {
        ProviderWebhookInboxEntry completed=ProviderWebhookDurableTransactionService.complete(current,now());
        if (!repository.updateInboxExpectedVersion(completed,current.version(),current.claimTokenHash()))
            throw new IllegalStateException("WEBHOOK_INBOX_VERSION_OR_CLAIM_CONFLICT");
        return completed;
    }


    private ExternalIssueConflictClassification classify(String eventType,ExternalIssueObservation observation,IssueProjectionState projection,
            ExternalIssueObservedState previous,String desiredHash,boolean noLink) {
        String event=value(eventType).toUpperCase(Locale.ROOT),status=value(observation.externalIssueStatus()).toUpperCase(Locale.ROOT);
        if(event.contains("PERMISSION")||event.contains("ACCESS_REVOKED"))return ExternalIssueConflictClassification.PERMISSION_REVOKED;
        if(event.contains("DELETE")||"DELETED".equals(status))return ExternalIssueConflictClassification.EXTERNAL_ISSUE_DELETED;
        if(projection!=null&&notBlank(observation.mappingSchemaHash())&&notBlank(projection.projectMappingSchemaHash())&&!observation.mappingSchemaHash().equals(projection.projectMappingSchemaHash()))return ExternalIssueConflictClassification.MAPPING_SCHEMA_DRIFT;
        if(previous!=null&&notBlank(previous.providerIdentityHash())&&!previous.providerIdentityHash().equals(observation.providerIdentityHash()))return ExternalIssueConflictClassification.PROVIDER_IDENTITY_CHANGED;
        if(noLink&&event.contains("CREATED"))return ExternalIssueConflictClassification.DUPLICATE_EXTERNAL_ISSUE;
        if(notBlank(desiredHash)&&!desiredHash.equals(observation.observedDocumentHash()))return ExternalIssueConflictClassification.EXTERNAL_FIELD_CHANGED;
        return ExternalIssueConflictClassification.NONE;
    }

    private ConflictResolutionPolicy defaultPolicy(ExternalIssueConflictClassification classification) {
        return switch(classification) {
            case EXTERNAL_FIELD_CHANGED,MAPPING_SCHEMA_DRIFT,EXTERNAL_ISSUE_DELETED -> ConflictResolutionPolicy.OPENDISPATCH_WINS;
            case DUPLICATE_EXTERNAL_ISSUE,PROVIDER_IDENTITY_CHANGED,PERMISSION_REVOKED,STALE_OR_OUT_OF_ORDER_EVENT -> ConflictResolutionPolicy.MANUAL_REVIEW;
            default -> ConflictResolutionPolicy.IGNORE;
        };
    }

    private ExternalIssueConflict staleConflict(ProviderWebhookInboxEntry inbox,ExternalIssueObservation observation,ExternalIssueObservedState state,String reason) {
        ExternalStateSemanticDiff diff=diffService.compare(null,state.desiredDocumentHash(),state.normalizedObservationJson(),observation.normalizedObservationJson(),observation.observedDocumentHash(),ExternalIssueConflictClassification.STALE_OR_OUT_OF_ORDER_EVENT);
        return createConflict(inbox,observation,state,ExternalIssueConflictClassification.STALE_OR_OUT_OF_ORDER_EVENT,ConflictResolutionPolicy.MANUAL_REVIEW,diff.diffJson(),reason);
    }

    private ExternalIssueConflict createConflict(ProviderWebhookInboxEntry inbox,ExternalIssueObservation observation,ExternalIssueObservedState state,
            ExternalIssueConflictClassification classification,ConflictResolutionPolicy policy,String diff) {
        return createConflict(inbox,observation,state,classification,policy,diff,classification.name());
    }

    private ExternalIssueConflict createConflict(ProviderWebhookInboxEntry inbox,ExternalIssueObservation observation,ExternalIssueObservedState state,
            ExternalIssueConflictClassification classification,ConflictResolutionPolicy policy,String diff,String reason) {
        String id="external-conflict-"+ExternalObservationNormalizer.sha256(inbox.tenantId()+"|"+inbox.inboxId()+"|"+classification).substring(0,32);
        String evidence=ExternalObservationNormalizer.sha256(value(state.desiredDocumentHash())+"|"+observation.observedDocumentHash()+"|"+diff+"|"+classification);
        return new ExternalIssueConflict(inbox.tenantId(),id,inbox.connectionId(),inbox.externalProjectId(),observation.externalIssueId(),state.taskIssueLinkId(),state.projectionId(),observation.observationId(),classification,policy,policy==ConflictResolutionPolicy.MANUAL_REVIEW?ExternalIssueConflictStatus.WAIT_HUMAN:ExternalIssueConflictStatus.OPEN,state.desiredDocumentHash(),observation.observedDocumentHash(),diff,evidence,reason,now(),null,null,null,null,null,null,1,inbox.correlationId());
    }

    private void saveNewConflict(ExternalIssueConflict conflict,String actor) {
        boolean existed=repository.findConflict(conflict.tenantId(),conflict.conflictId()).isPresent();
        repository.saveConflict(conflict);
        if(!existed) ledger.append(conflict,ExternalIssueConflictEventType.CONFLICT_CREATED,actor,conflict.reasonCode(),conflict.stateDiffJson());
    }

    private WebhookReplayEvidence evidence(ProviderWebhookInboxEntry inbox,String type,String decision,String reason,String source) {
        return new WebhookReplayEvidence(inbox.tenantId(),"webhook-evidence-"+UUID.randomUUID(),inbox.inboxId(),type,decision,reason,ExternalObservationNormalizer.sha256(value(source)+"|"+type+"|"+reason),"RECONCILER",now(),inbox.correlationId());
    }
    private String required(String value,String name){if(!notBlank(value))throw new IllegalArgumentException(name+" is required.");return value.trim();}
    private boolean notBlank(String value){return value!=null&&!value.isBlank();}
    private String value(String value){return value==null?"":value;}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
