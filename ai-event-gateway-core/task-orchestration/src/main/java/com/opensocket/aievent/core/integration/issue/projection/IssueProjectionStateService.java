package com.opensocket.aievent.core.integration.issue.projection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.events.A2ADomainModuleEvent;
import com.opensocket.aievent.core.integration.handoff.HandoffDomainEventType;
import com.opensocket.aievent.core.integration.handoff.HandoffDomainModuleEvent;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionAggregateKey;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionFailureClassification;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionLifecycleStatus;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionPurpose;
import com.opensocket.aievent.core.issuetracking.contract.ProjectionRecoveryStrategy;

/**
 * Canonical state authority for one external Issue Projection aggregate.
 * Domain events update an existing aggregate and never create an unrelated Projection.
 */
@Service
public class IssueProjectionStateService {
    private static final Set<String> A2A_EVENTS = Set.of(
            "A2A_APPROVED", "A2A_CHILD_TASK_CREATED", "A2A_COMPLETED", "A2A_FAILED");
    private static final Set<HandoffDomainEventType> HANDOFF_EVENTS = Set.of(
            HandoffDomainEventType.HANDOFF_SNAPSHOT_APPROVED,
            HandoffDomainEventType.HANDOFF_RESULT_SNAPSHOT_CREATED,
            HandoffDomainEventType.HANDOFF_SNAPSHOT_REJECTED,
            HandoffDomainEventType.HANDOFF_SNAPSHOT_SUPERSEDED);

    private final IssueProjectionStateRepository repository;
    private final IssueProjectionProperties properties;

    public IssueProjectionStateService(IssueProjectionStateRepository repository, IssueProjectionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public IssueProjectionState requestIntent(String tenantId, String taskId, String connectionId,
            String projectMappingId, int projectMappingVersion, String projectMappingSchemaHash,
            ProjectionPurpose purpose, String domainEventId, String correlationId) {
        String tenant=required(tenantId,"tenantId"), task=required(taskId,"taskId"),
                connection=required(connectionId,"connectionId"), mapping=required(projectMappingId,"projectMappingId");
        ProjectionPurpose resolvedPurpose=purpose==null?ProjectionPurpose.PRIMARY_ISSUE:purpose;
        ProjectionAggregateKey aggregateKey=new ProjectionAggregateKey(tenant,task,connection,mapping,resolvedPurpose);
        var existing=repository.findByAggregateKey(tenant,aggregateKey.canonicalValue());
        if(existing.isPresent()) return existing.get();
        OffsetDateTime now=now();
        String event=required(domainEventId,"domainEventId");
        IssueProjectionState created=new IssueProjectionState(
                tenant,aggregateKey.stableProjectionId(),aggregateKey.canonicalValue(),resolvedPurpose,
                event,"PROJECTION_INTENT_REQUESTED","TASK",task,task,null,null,null,connection,mapping,
                Math.max(1,projectMappingVersion),required(projectMappingSchemaHash,"projectMappingSchemaHash"),
                2,null,IssueProjectionDesiredState.ACTIVE,IssueProjectionObservedState.UNKNOWN,
                ProjectionLifecycleStatus.READY,ProjectionFailureClassification.NONE,
                ProjectionRecoveryStrategy.NONE,IssueProjectionConflictPolicy.MANUAL_REVIEW,
                null,null,event,1,1,null,0,properties.getMaxAttempts(),null,null,null,1,now,now,null,
                correlationId==null||correlationId.isBlank()?"corr-"+UUID.randomUUID():correlationId.trim());
        repository.save(created);
        repository.tryRecordDomainEvent(tenant,created.projectionId(),event,1,null,now);
        return created;
    }

    @Transactional
    public void onA2AEvent(A2ADomainModuleEvent event) {
        if (!properties.isEnabled() || event == null || !A2A_EVENTS.contains(event.domainEventType())) return;
        String taskId=text(event.payload().get("childTaskId"));
        if(taskId==null||taskId.isBlank()) return;
        IssueProjectionDesiredState desired="A2A_FAILED".equals(event.domainEventType())
                ? IssueProjectionDesiredState.SUSPENDED : IssueProjectionDesiredState.ACTIVE;
        applyDomainEvent(event.tenantId(),taskId,event.eventId(),desired,hash(event.payload()),event.correlationId());
    }

    @Transactional
    public void onHandoffEvent(HandoffDomainModuleEvent event) {
        if (!properties.isEnabled() || event == null || !HANDOFF_EVENTS.contains(event.domainEventType())) return;
        IssueProjectionDesiredState desired=switch(event.domainEventType()) {
            case HANDOFF_SNAPSHOT_REJECTED,HANDOFF_SNAPSHOT_SUPERSEDED -> IssueProjectionDesiredState.SUSPENDED;
            default -> IssueProjectionDesiredState.ACTIVE;
        };
        applyDomainEvent(event.tenantId(),event.targetTaskId(),event.eventId(),desired,hash(event.payload()),event.correlationId());
    }

    private void applyDomainEvent(String tenantId,String taskId,String eventId,
            IssueProjectionDesiredState desired,String payloadHash,String correlationId) {
        var currentOpt=repository.findLatestByTask(required(tenantId,"tenantId"),required(taskId,"taskId"));
        if(currentOpt.isEmpty()) return; // Phase 3D: events cannot create unrelated Projection aggregates.
        IssueProjectionState current=currentOpt.get();
        if(current.lifecycleStatus()==ProjectionLifecycleStatus.SUPERSEDED||current.lifecycleStatus()==ProjectionLifecycleStatus.DISABLED) return;
        long nextSequence=current.operationSequence()+1;
        OffsetDateTime now=now();
        if(!repository.tryRecordDomainEvent(current.tenantId(),current.projectionId(),required(eventId,"eventId"),nextSequence,payloadHash,now)) return;
        ProjectionLifecycleStatus lifecycle=desired==IssueProjectionDesiredState.SUSPENDED
                ? ProjectionLifecycleStatus.DISABLED
                : current.lifecycleStatus()==ProjectionLifecycleStatus.SYNCED
                    ? ProjectionLifecycleStatus.READY : current.lifecycleStatus();
        ProjectionFailureClassification failure=desired==IssueProjectionDesiredState.SUSPENDED
                ? ProjectionFailureClassification.DISABLED_BY_OPERATOR : ProjectionFailureClassification.NONE;
        ProjectionRecoveryStrategy recovery=desired==IssueProjectionDesiredState.SUSPENDED
                ? ProjectionRecoveryStrategy.DISABLE : ProjectionRecoveryStrategy.NONE;
        repository.save(copy(current,desired,current.observedState(),lifecycle,failure,recovery,
                current.retryCount(),current.nextRetryAt(),null,null,current.version()+1,
                current.projectionVersion()+1,now,current.completedAt(),eventId,nextSequence,
                current.supersededBy(),payloadHash,current.observedPayloadHash(),current.canonicalDocumentHash()));
    }

    @Transactional
    public IssueProjectionState retry(String tenantId,String projectionId,String reason) {
        IssueProjectionState current=get(tenantId,projectionId); OffsetDateTime now=now();
        return repository.save(copy(current,current.desiredState(),IssueProjectionObservedState.UNKNOWN,
                ProjectionLifecycleStatus.READY,ProjectionFailureClassification.NONE,
                ProjectionRecoveryStrategy.RETRY,current.retryCount(),now,null,required(reason,"reason"),
                current.version()+1,current.projectionVersion()+1,now,null,current.lastAppliedDomainEventId(),
                current.operationSequence(),current.supersededBy(),current.desiredPayloadHash(),
                current.observedPayloadHash(),current.canonicalDocumentHash()));
    }

    @Transactional
    public IssueProjectionState markInProgress(String tenantId,String projectionId,String canonicalDocumentHash,int documentSchemaVersion) {
        IssueProjectionState current=get(tenantId,projectionId);
        if(current.lifecycleStatus()!=ProjectionLifecycleStatus.READY && current.lifecycleStatus()!=ProjectionLifecycleStatus.FAILED)
            throw new IllegalStateException("ISSUE_PROJECTION_NOT_READY");
        OffsetDateTime now=now();
        return repository.save(new IssueProjectionState(current.tenantId(),current.projectionId(),current.projectionAggregateKey(),
                current.projectionPurpose(),current.sourceEventId(),current.sourceEventType(),current.sourceAggregateType(),
                current.sourceAggregateId(),current.taskId(),current.a2aRequestId(),current.handoffSnapshotId(),
                current.taskIssueLinkId(),current.connectionId(),current.projectMappingId(),current.projectMappingVersion(),
                current.projectMappingSchemaHash(),Math.max(1,documentSchemaVersion),required(canonicalDocumentHash,"canonicalDocumentHash"),
                current.desiredState(),IssueProjectionObservedState.CREATING,ProjectionLifecycleStatus.IN_PROGRESS,
                ProjectionFailureClassification.NONE,ProjectionRecoveryStrategy.NONE,current.conflictPolicy(),canonicalDocumentHash,
                current.observedPayloadHash(),current.lastAppliedDomainEventId(),current.operationSequence(),current.projectionVersion()+1,
                current.supersededBy(),current.retryCount(),current.maxAttempts(),null,null,null,current.version()+1,
                current.createdAt(),now,null,current.correlationId()));
    }

    @Transactional
    public IssueProjectionState suspend(String tenantId,String projectionId,String reason) {
        IssueProjectionState current=get(tenantId,projectionId); OffsetDateTime now=now();
        return repository.save(copy(current,IssueProjectionDesiredState.SUSPENDED,current.observedState(),
                ProjectionLifecycleStatus.DISABLED,ProjectionFailureClassification.DISABLED_BY_OPERATOR,
                ProjectionRecoveryStrategy.DISABLE,current.retryCount(),null,
                IssueProjectionReasonCode.ISSUE_PROJECTION_DISABLED.name(),required(reason,"reason"),
                current.version()+1,current.projectionVersion()+1,now,now,current.lastAppliedDomainEventId(),
                current.operationSequence(),current.supersededBy(),current.desiredPayloadHash(),
                current.observedPayloadHash(),current.canonicalDocumentHash()));
    }

    @Transactional
    public IssueProjectionState supersede(String tenantId,String projectionId,String replacementProjectionId,String reason) {
        IssueProjectionState current=get(tenantId,projectionId); String replacement=required(replacementProjectionId,"replacementProjectionId");
        if(current.projectionId().equals(replacement)) throw new IllegalArgumentException("replacementProjectionId must differ");
        OffsetDateTime now=now();
        return repository.save(copy(current,current.desiredState(),current.observedState(),ProjectionLifecycleStatus.SUPERSEDED,
                ProjectionFailureClassification.NONE,ProjectionRecoveryStrategy.SUPERSEDE,current.retryCount(),null,
                null,required(reason,"reason"),current.version()+1,current.projectionVersion()+1,now,now,
                current.lastAppliedDomainEventId(),current.operationSequence(),replacement,current.desiredPayloadHash(),
                current.observedPayloadHash(),current.canonicalDocumentHash()));
    }

    @Transactional
    public void bindTaskIssueLink(String tenantId,String taskId,String taskIssueLinkId,String connectionId,String projectMappingId) {
        bindTaskIssueLink(tenantId,taskId,taskIssueLinkId,connectionId,projectMappingId,1,"legacy-unbound");
    }

    @Transactional
    public void bindTaskIssueLink(String tenantId,String taskId,String taskIssueLinkId,String connectionId,
            String projectMappingId,int projectMappingVersion,String projectMappingSchemaHash) {
        String eventId="task-issue-link:"+required(taskIssueLinkId,"taskIssueLinkId");
        IssueProjectionState current=requestIntent(tenantId,taskId,connectionId,projectMappingId,projectMappingVersion,
                projectMappingSchemaHash,ProjectionPurpose.PRIMARY_ISSUE,eventId,null);
        if(current.taskIssueLinkId()!=null&&!current.taskIssueLinkId().isBlank()) {
            if(!current.taskIssueLinkId().equals(taskIssueLinkId)) throw new IllegalStateException("PROJECTION_AGGREGATE_ALREADY_BOUND");
            return;
        }
        OffsetDateTime now=now();
        repository.save(new IssueProjectionState(current.tenantId(),current.projectionId(),current.projectionAggregateKey(),
                current.projectionPurpose(),current.sourceEventId(),current.sourceEventType(),current.sourceAggregateType(),
                current.sourceAggregateId(),current.taskId(),current.a2aRequestId(),current.handoffSnapshotId(),
                taskIssueLinkId,current.connectionId(),current.projectMappingId(),current.projectMappingVersion(),
                current.projectMappingSchemaHash(),current.canonicalDocumentSchemaVersion(),current.canonicalDocumentHash(),
                current.desiredState(),IssueProjectionObservedState.UNKNOWN,
                current.lifecycleStatus()==ProjectionLifecycleStatus.IN_PROGRESS?ProjectionLifecycleStatus.IN_PROGRESS:ProjectionLifecycleStatus.READY,
                ProjectionFailureClassification.NONE,ProjectionRecoveryStrategy.NONE,current.conflictPolicy(),
                current.desiredPayloadHash(),current.observedPayloadHash(),current.lastAppliedDomainEventId(),
                current.operationSequence(),current.projectionVersion()+1,current.supersededBy(),current.retryCount(),
                current.maxAttempts(),null,null,null,current.version()+1,current.createdAt(),now,null,current.correlationId()));
    }

    @Transactional public void recordObservedSuccessByTaskIssueLink(String tenantId,String taskIssueLinkId,String observedPayloadHash){repository.findByTaskIssueLink(required(tenantId,"tenantId"),required(taskIssueLinkId,"taskIssueLinkId")).ifPresent(s->recordObservedSuccess(tenantId,s.projectionId(),taskIssueLinkId,observedPayloadHash));}
    @Transactional public void recordFailureByTaskIssueLink(String tenantId,String taskIssueLinkId,String code,String message,boolean retryable){repository.findByTaskIssueLink(required(tenantId,"tenantId"),required(taskIssueLinkId,"taskIssueLinkId")).ifPresent(s->recordFailure(tenantId,s.projectionId(),code,message,retryable));}
    @Transactional public void recordConflictByTaskIssueLink(String tenantId,String taskIssueLinkId,String observedPayloadHash,String message){repository.findByTaskIssueLink(required(tenantId,"tenantId"),required(taskIssueLinkId,"taskIssueLinkId")).ifPresent(s->recordConflict(tenantId,s.projectionId(),observedPayloadHash,message));}

    @Transactional
    public IssueProjectionState recordObservedSuccess(String tenantId,String projectionId,String taskIssueLinkId,String observedPayloadHash) {
        IssueProjectionState current=get(tenantId,projectionId); OffsetDateTime now=now();
        return repository.save(copy(current,current.desiredState(),IssueProjectionObservedState.ACTIVE,
                ProjectionLifecycleStatus.SYNCED,ProjectionFailureClassification.NONE,ProjectionRecoveryStrategy.NONE,
                current.retryCount(),null,null,null,current.version()+1,current.projectionVersion()+1,now,now,
                current.lastAppliedDomainEventId(),current.operationSequence(),current.supersededBy(),
                current.desiredPayloadHash(),observedPayloadHash,current.canonicalDocumentHash(),taskIssueLinkId));
    }

    @Transactional
    public IssueProjectionState recordFailure(String tenantId,String projectionId,String code,String message,boolean retryable) {
        IssueProjectionState current=get(tenantId,projectionId); OffsetDateTime now=now(); int attempts=Math.min(current.maxAttempts(),current.retryCount()+1);
        boolean exhausted=!retryable||attempts>=current.maxAttempts();
        ProjectionFailureClassification classification=classify(code,retryable,exhausted);
        ProjectionRecoveryStrategy recovery=exhausted?ProjectionRecoveryStrategy.DEAD_LETTER:ProjectionRecoveryStrategy.RETRY;
        ProjectionLifecycleStatus lifecycle=exhausted?ProjectionLifecycleStatus.DEAD_LETTER:ProjectionLifecycleStatus.FAILED;
        IssueProjectionState updated=repository.save(copy(current,current.desiredState(),IssueProjectionObservedState.FAILED,
                lifecycle,classification,recovery,attempts,exhausted?null:now.plusSeconds(properties.getRetryDelaySeconds()),
                code,message,current.version()+1,current.projectionVersion()+1,now,exhausted?now:null,
                current.lastAppliedDomainEventId(),current.operationSequence(),current.supersededBy(),
                current.desiredPayloadHash(),current.observedPayloadHash(),current.canonicalDocumentHash()));
        if(exhausted) openCase(updated,IssueProjectionReasonCode.ISSUE_PROJECTION_RETRY_EXHAUSTED.name(),message);
        return updated;
    }

    @Transactional
    public IssueProjectionState recordConflict(String tenantId,String projectionId,String observedPayloadHash,String message) {
        IssueProjectionState current=get(tenantId,projectionId); OffsetDateTime now=now();
        ProjectionLifecycleStatus lifecycle=current.conflictPolicy()==IssueProjectionConflictPolicy.SUSPEND_ON_CONFLICT
                ?ProjectionLifecycleStatus.DISABLED:ProjectionLifecycleStatus.CONFLICT;
        ProjectionRecoveryStrategy recovery=lifecycle==ProjectionLifecycleStatus.DISABLED
                ?ProjectionRecoveryStrategy.DISABLE:ProjectionRecoveryStrategy.MANUAL_REVIEW;
        IssueProjectionState updated=repository.save(copy(current,current.desiredState(),IssueProjectionObservedState.CONFLICT,
                lifecycle,ProjectionFailureClassification.EXTERNAL_STATE_CONFLICT,recovery,current.retryCount(),null,
                IssueProjectionReasonCode.ISSUE_PROJECTION_OBSERVED_DRIFT.name(),message,current.version()+1,
                current.projectionVersion()+1,now,null,current.lastAppliedDomainEventId(),current.operationSequence(),
                current.supersededBy(),current.desiredPayloadHash(),observedPayloadHash,current.canonicalDocumentHash()));
        openCase(updated,IssueProjectionReasonCode.ISSUE_PROJECTION_CONFLICT.name(),message); return updated;
    }

    public IssueProjectionState get(String tenantId,String projectionId){return repository.find(required(tenantId,"tenantId"),required(projectionId,"projectionId")).orElseThrow(()->new IllegalArgumentException("Issue Projection not found in Tenant: "+projectionId));}
    public List<IssueProjectionState> list(String tenantId,ProjectionLifecycleStatus state,int limit){return repository.list(required(tenantId,"tenantId"),state,bounded(limit));}
    public List<IssueProjectionReconciliationCase> cases(String tenantId,IssueProjectionReconciliationStatus status,int limit){return repository.listCases(required(tenantId,"tenantId"),status,bounded(limit));}

    @Transactional public IssueProjectionReconciliationCase resolveCase(String tenantId,String caseId,String actorId,String reason){IssueProjectionReconciliationCase c=repository.findCase(required(tenantId,"tenantId"),required(caseId,"caseId")).orElseThrow(()->new IllegalArgumentException("Issue Projection reconciliation case not found: "+caseId));OffsetDateTime n=now();return repository.saveCase(new IssueProjectionReconciliationCase(c.tenantId(),c.caseId(),c.projectionId(),c.reasonCode(),c.evidenceReference(),IssueProjectionReconciliationStatus.RESOLVED,null,c.attemptCount(),c.lastError(),required(actorId,"actorId"),required(reason,"reason"),c.createdAt(),n,n,c.correlationId()));}
    @Transactional public void reconcileDue(){if(!properties.isEnabled())return;for(IssueProjectionState s:repository.listDue(now(),properties.getReconcileBatchSize())){if(s.retryCount()>=s.maxAttempts())recordFailure(s.tenantId(),s.projectionId(),IssueProjectionReasonCode.ISSUE_PROJECTION_RETRY_EXHAUSTED.name(),"Projection retry budget is exhausted.",false);else retry(s.tenantId(),s.projectionId(),"Reconciler scheduled another projection evaluation.");}}

    private void openCase(IssueProjectionState state,String reason,String message){if(repository.findOpenCaseByProjection(state.tenantId(),state.projectionId()).isPresent())return;OffsetDateTime n=now();repository.saveCase(new IssueProjectionReconciliationCase(state.tenantId(),"issue-projection-case-"+UUID.randomUUID(),state.projectionId(),reason,"projection:"+state.projectionId(),IssueProjectionReconciliationStatus.WAIT_HUMAN,null,state.retryCount(),message,null,null,n,n,null,state.correlationId()));}

    private ProjectionFailureClassification classify(String code,boolean retryable,boolean exhausted){if(exhausted)return ProjectionFailureClassification.RETRY_EXHAUSTED;if(code==null)return retryable?ProjectionFailureClassification.PROVIDER_TRANSIENT_FAILURE:ProjectionFailureClassification.PROVIDER_REJECTED;String v=code.toUpperCase();if(v.contains("MAPPING"))return ProjectionFailureClassification.MAPPING_SCHEMA_DRIFT;if(v.contains("PRINCIPAL")||v.contains("PERMISSION"))return ProjectionFailureClassification.PRINCIPAL_PERMISSION_DENIED;if(v.contains("SENSITIVE")||v.contains("SECRET"))return ProjectionFailureClassification.SENSITIVE_FIELD_BLOCKED;if(v.contains("IDEMPOTENCY"))return ProjectionFailureClassification.IDEMPOTENCY_CONFLICT;if(v.contains("CONFLICT"))return ProjectionFailureClassification.EXTERNAL_STATE_CONFLICT;return retryable?ProjectionFailureClassification.PROVIDER_TRANSIENT_FAILURE:ProjectionFailureClassification.PROVIDER_REJECTED;}

    private IssueProjectionState copy(IssueProjectionState c,IssueProjectionDesiredState desired,IssueProjectionObservedState observed,
            ProjectionLifecycleStatus lifecycle,ProjectionFailureClassification failure,ProjectionRecoveryStrategy recovery,
            int retryCount,OffsetDateTime nextRetry,String errorCode,String errorMessage,long version,long projectionVersion,
            OffsetDateTime updatedAt,OffsetDateTime completedAt,String lastEvent,long operationSequence,String supersededBy,
            String desiredHash,String observedHash,String documentHash){return copy(c,desired,observed,lifecycle,failure,recovery,retryCount,nextRetry,errorCode,errorMessage,version,projectionVersion,updatedAt,completedAt,lastEvent,operationSequence,supersededBy,desiredHash,observedHash,documentHash,c.taskIssueLinkId());}
    private IssueProjectionState copy(IssueProjectionState c,IssueProjectionDesiredState desired,IssueProjectionObservedState observed,
            ProjectionLifecycleStatus lifecycle,ProjectionFailureClassification failure,ProjectionRecoveryStrategy recovery,
            int retryCount,OffsetDateTime nextRetry,String errorCode,String errorMessage,long version,long projectionVersion,
            OffsetDateTime updatedAt,OffsetDateTime completedAt,String lastEvent,long operationSequence,String supersededBy,
            String desiredHash,String observedHash,String documentHash,String taskIssueLinkId){return new IssueProjectionState(c.tenantId(),c.projectionId(),c.projectionAggregateKey(),c.projectionPurpose(),c.sourceEventId(),c.sourceEventType(),c.sourceAggregateType(),c.sourceAggregateId(),c.taskId(),c.a2aRequestId(),c.handoffSnapshotId(),taskIssueLinkId,c.connectionId(),c.projectMappingId(),c.projectMappingVersion(),c.projectMappingSchemaHash(),c.canonicalDocumentSchemaVersion(),documentHash,desired,observed,lifecycle,failure,recovery,c.conflictPolicy(),desiredHash,observedHash,lastEvent,operationSequence,projectionVersion,supersededBy,retryCount,c.maxAttempts(),nextRetry,errorCode,errorMessage,version,c.createdAt(),updatedAt,completedAt,c.correlationId());}

    private String hash(Map<String,Object> payload){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.valueOf(payload).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String text(Object value){return value==null?null:String.valueOf(value);} private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);} private int bounded(int v){return Math.max(1,Math.min(v<=0?200:v,1000));} private String required(String v,String name){if(v==null||v.isBlank())throw new IllegalArgumentException(name+" is required");return v.trim();}
}
