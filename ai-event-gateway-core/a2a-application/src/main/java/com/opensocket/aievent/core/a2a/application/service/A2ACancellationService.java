package com.opensocket.aievent.core.a2a.application.service;

import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACancellationReliabilityUseCase;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACancellationRuntimeUseCase;
import com.opensocket.aievent.core.a2a.application.port.in.A2ACancellationUseCase;
import com.opensocket.aievent.core.a2a.application.port.out.A2ACancellationAuthorityOperations;
import com.opensocket.aievent.core.a2a.application.port.out.A2ATaskAuthorityOperations;
import com.opensocket.aievent.core.a2a.core.A2AStateMachine;
import com.opensocket.aievent.core.a2a.core.A2ACancellationBindingGuard;
import com.opensocket.aievent.core.events.A2ARuntimeCancelDeliveryEvent;
import com.opensocket.aievent.core.events.A2ARuntimeCancelRequestedEvent;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.domain.TaskActorType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Sole cancellation authority: rotates fencing, records immutable evidence and reconciles ambiguity. */
public class A2ACancellationService implements A2ACancellationUseCase,
        A2ACancellationRuntimeUseCase, A2ACancellationReliabilityUseCase {
    private static final int MAX_RETRY_ATTEMPTS=3;
    private static final Set<A2ACancellationProcessingStatus> FINAL_PROCESSING=Set.of(
            A2ACancellationProcessingStatus.CONFIRMED,
            A2ACancellationProcessingStatus.WAIT_HUMAN);

    private final A2AStateMachine stateMachine=new A2AStateMachine();
    private final A2ACancellationBindingGuard bindingGuard=new A2ACancellationBindingGuard();
    private final A2ARequestRepository requests;
    private final A2APolicyRepository policies;
    private final A2AStateHistoryRepository history;
    private final A2ACancellationRepository cancellations;
    private final A2ACancellationEvidenceRepository evidenceRepository;
    private final A2AReconciliationCaseRepository reconciliationCases;
    private final A2AResultRepository results;
    private final A2AResultQuarantineRepository quarantine;
    private final A2ATaskAuthorityOperations tasks;
    private final A2ACancellationAuthorityOperations authority;
    private final ModuleEventPublisher events;

    public A2ACancellationService(A2ARequestRepository requests,A2APolicyRepository policies,
            A2AStateHistoryRepository history,A2ACancellationRepository cancellations,
            A2ACancellationEvidenceRepository evidenceRepository,
            A2AReconciliationCaseRepository reconciliationCases,A2AResultRepository results,
            A2AResultQuarantineRepository quarantine,A2ATaskAuthorityOperations tasks,
            A2ACancellationAuthorityOperations authority,ModuleEventPublisher events){
        this.requests=requests;this.policies=policies;this.history=history;
        this.cancellations=cancellations;this.evidenceRepository=evidenceRepository;
        this.reconciliationCases=reconciliationCases;this.results=results;
        this.quarantine=quarantine;this.tasks=tasks;this.authority=authority;this.events=events;
    }

    @Override @Transactional
    public A2ARequest request(String tenantId,String requestId,String actorType,String actorId,
            String reason,String idempotencyKey){
        requireText(idempotencyKey,"Idempotency-Key");
        A2ACancellationRecord replay=cancellations.findByIdempotencyKey(tenantId,idempotencyKey)
                .orElse(null);
        if(replay!=null)return requireRequest(tenantId,replay.getRequestId());
        A2ARequest request=requireRequest(tenantId,requestId);
        A2ACancellationRecord existing=cancellations.findByRequest(tenantId,requestId).orElse(null);
        if(existing!=null)return request;
        OffsetDateTime now=now();
        String policyId=request.getPolicyId();
        A2APolicy policy=policies.findById(tenantId,policyId).orElseThrow(
                ()->new IllegalArgumentException("A2A Policy not found: "+policyId));

        if(request.getChildTaskId()==null){
            return transition(request,A2ARequestStatus.CANCELLED_CONFIRMED,
                    "A2A_CANCELLED_BEFORE_CHILD","Cancellation confirmed before Child Task creation",
                    actorType,actorId,idempotencyKey);
        }
        TaskRecord child=tasks.requireTask(tenantId,request.getChildTaskId());
        if(child.getStatus().isSucceeded()||child.getStatus()==TaskStatus.PARTIALLY_COMPLETED){
            createCompletedOutcome(request,child,actorType,actorId,reason,idempotencyKey,now);
            return request;
        }
        if(child.getStatus().isTerminal()){
            createStaleOutcome(request,child,actorType,actorId,reason,idempotencyKey,now);
            return request;
        }

        request=transition(request,A2ARequestStatus.CANCEL_REQUESTED,"A2A_CANCEL_REQUESTED",
                first(reason,"A2A cancellation requested"),actorType,actorId,
                "cancel-request:"+idempotencyKey);
        if(child.getStatus()!=TaskStatus.CANCEL_REQUESTED){
            child=tasks.transition(child,TaskStatus.CANCEL_REQUESTED,"A2A_CANCEL_REQUESTED",
                    first(reason,"Cancellation requested by A2A parent"),TaskActorType.SYSTEM,
                    first(actorId,"A2A_CORE"),request.getCorrelationId(),
                    "a2a-task-cancel-request:"+idempotencyKey);
        }
        String cancellationId="a2a-cancel-"+UUID.randomUUID();
        var receipt=authority.revoke(request,child,cancellationId,
                first(reason,"A2A cancellation requested"));
        A2ACancellationRecord cancellation=newRecord(request,child,cancellationId,actorType,
                actorId,reason,idempotencyKey,now,policy,receipt);
        cancellation=cancellations.save(cancellation);
        append(cancellation,"request:"+idempotencyKey,"CANCELLATION_REQUEST",
                "request:"+requestId,cancellation.getCancellationFingerprint(),"ACCEPTED",
                "A2A_CANCEL_REQUESTED",cancellation.getReason(),now);
        append(cancellation,"fencing:"+cancellationId,"AUTHORITY_REVOCATION",
                receipt.assignmentId(),receipt.activeFencingTokenHash(),"ACCEPTED",
                receipt.authorityDecision(),"Assignment and execution-attempt fencing rotated",now);

        if(receipt.agentAlreadyCompleted()){
            completeAsAgentAlreadyCompleted(cancellation,now,
                    "Execution completed before fencing rotation could win");
            return requireRequest(tenantId,requestId);
        }
        if(receipt.runtimeAcknowledgementRequired()){
            publishRuntimeCancel(cancellation,request.getCorrelationId(),now);
            return request;
        }
        confirm(cancellation,"LOCAL_AUTHORITY_CONFIRMED",
                "No active runtime execution required acknowledgement",actorType,actorId,now);
        return requireRequest(tenantId,requestId);
    }

    @Override @Transactional
    public A2ACancellationRecord recordDelivery(A2ARuntimeCancelDeliveryEvent event){
        A2ACancellationRecord cancellation=requireCancellation(event.tenantId(),event.cancellationId());
        OffsetDateTime occurredAt=event.occurredAt()==null?now():event.occurredAt();
        String eventKey="delivery:"+event.eventId();
        if(evidenceRepository.findByEventKey(event.tenantId(),eventKey).isPresent())return cancellation;
        if(FINAL_PROCESSING.contains(cancellation.getProcessingStatus())){
            append(cancellation,eventKey,"RUNTIME_DELIVERY","http:"+event.httpStatus(),null,
                    "OBSERVED","LATE_DELIVERY_OUTCOME_IGNORED",
                    first(event.errorCode(),event.message(),event.deliveryStatus()),occurredAt);
            return cancellation;
        }
        long expected=cancellation.getVersion();
        cancellation.setDeliveryAt(occurredAt);cancellation.setDeliveryStatus(event.deliveryStatus());
        cancellation.setUpdatedAt(occurredAt);cancellation.setVersion(expected+1);
        if(event.accepted()){
            cancellation.setStatus(A2ACancellationStatus.DELIVERED);
            cancellation.setProcessingStatus(A2ACancellationProcessingStatus.RUNTIME_ACK_PENDING);
            cancellation.setReconciliationClassification(
                    A2ACancellationReconciliationClassification.RUNTIME_ACK_MISSING);
            cancellation.setNextReconcileAt(cancellation.getDeadlineAt());cancellation.setLastError(null);
        }else{
            cancellation.setStatus(A2ACancellationStatus.FAILED);
            cancellation.setProcessingStatus(A2ACancellationProcessingStatus.FAILED_RETRYABLE);
            cancellation.setReconciliationClassification(
                    A2ACancellationReconciliationClassification.DELIVERY_FAILED);
            cancellation.setLastError(first(event.errorCode(),event.message(),
                    "Runtime cancellation delivery failed"));
            cancellation.setNextReconcileAt(backoff(occurredAt,cancellation.getRetryCount()+1));
        }
        cancellation=cancellations.saveExpectedVersion(cancellation,expected);
        append(cancellation,eventKey,"RUNTIME_DELIVERY","http:"+event.httpStatus(),null,
                event.accepted()?"ACCEPTED":"FAILED",
                first(event.errorCode(),event.deliveryStatus()),event.message(),occurredAt);
        return cancellation;
    }

    @Override @Transactional
    public A2ACancellationRecord acknowledge(A2ACancellationAckCommand command){
        if(command==null)throw new IllegalArgumentException("Cancellation acknowledgement is required");
        A2ACancellationRecord cancellation=requireCancellation(command.tenantId(),command.cancellationId());
        OffsetDateTime occurredAt=command.occurredAt()==null?now():command.occurredAt();
        String eventKey="ack:"+first(command.callbackId(),command.cancellationId());
        if(evidenceRepository.findByEventKey(command.tenantId(),eventKey).isPresent()
                && cancellation.getProcessingStatus()==A2ACancellationProcessingStatus.CONFIRMED){
            return cancellation;
        }
        String conflict=ackBindingConflict(cancellation,command);
        if(conflict!=null){
            append(cancellation,eventKey,"RUNTIME_ACK",command.callbackId(),
                    command.activeFencingTokenHash(),"REJECTED","STALE_CANCELLATION",conflict,occurredAt);
            return markStaleCancellation(cancellation,conflict,occurredAt);
        }
        A2AResult canonical=results.findByRequest(command.tenantId(),cancellation.getRequestId()).orElse(null);
        if(canonical!=null&&notAfter(canonical.getAcceptedAt(),cancellation.getResultCutoffAt())){
            append(cancellation,eventKey,"RUNTIME_ACK",command.callbackId(),
                    command.activeFencingTokenHash(),"OBSERVED","AGENT_ALREADY_COMPLETED",
                    "Canonical Result was accepted before cancellation cutoff",occurredAt);
            return completeAsAgentAlreadyCompleted(cancellation,occurredAt,
                    "Canonical Result won the Cancel/Complete race");
        }
        if(!"ACKNOWLEDGED".equalsIgnoreCase(command.decision())){
            append(cancellation,eventKey,"RUNTIME_ACK",command.callbackId(),
                    command.activeFencingTokenHash(),"FAILED",command.decision(),command.reason(),occurredAt);
            return scheduleRetry(cancellation,A2ACancellationReconciliationClassification.RUNTIME_ACK_MISSING,
                    first(command.reason(),command.decision()),occurredAt);
        }
        // Evidence is persisted before state mutation so a reconciler can recover a process crash.
        append(cancellation,eventKey,"RUNTIME_ACK",command.callbackId(),
                command.activeFencingTokenHash(),"ACCEPTED","CANCEL_ACKNOWLEDGED",
                command.reason(),occurredAt);
        long expected=cancellation.getVersion();
        cancellation.setStatus(A2ACancellationStatus.ACKNOWLEDGED);
        cancellation.setOutcome(A2ACancellationOutcome.CANCELLED_CONFIRMED);
        cancellation.setProcessingStatus(A2ACancellationProcessingStatus.CONFIRMED);
        cancellation.setReconciliationClassification(A2ACancellationReconciliationClassification.NONE);
        cancellation.setAcknowledgedAt(occurredAt);cancellation.setCompletedAt(occurredAt);
        cancellation.setNextReconcileAt(null);cancellation.setLastError(null);
        cancellation.setUpdatedAt(occurredAt);cancellation.setVersion(expected+1);
        cancellation=cancellations.saveExpectedVersion(cancellation,expected);
        confirm(cancellation,"RUNTIME_CANCEL_ACKNOWLEDGED",
                first(command.reason(),"Agent acknowledged cancellation"),"RUNTIME",
                first(command.agentSessionId(),"unknown-session"),occurredAt);
        return cancellation;
    }

    @Override @Transactional
    public A2ACancellationRecord reconcile(A2ACancellationRecord due,OffsetDateTime occurredAt){
        A2ACancellationRecord cancellation=requireCancellation(due.getTenantId(),due.getCancellationId());
        if(FINAL_PROCESSING.contains(cancellation.getProcessingStatus()))return cancellation;
        A2ACancellationEvidence acceptedAck=evidenceRepository.findByCancellation(
                cancellation.getTenantId(),cancellation.getCancellationId(),1000).stream()
                .filter(e->"RUNTIME_ACK".equals(e.getEvidenceType())&&"ACCEPTED".equals(e.getDecision()))
                .reduce((a,b)->b).orElse(null);
        if(acceptedAck!=null){
            return recoverAcceptedAck(cancellation,acceptedAck,occurredAt);
        }
        A2AResult canonical=results.findByRequest(cancellation.getTenantId(),
                cancellation.getRequestId()).orElse(null);
        if(canonical!=null&&notAfter(canonical.getAcceptedAt(),cancellation.getResultCutoffAt())){
            return completeAsAgentAlreadyCompleted(cancellation,occurredAt,
                    "Canonical Result predates cancellation cutoff");
        }
        if(cancellation.getRetryCount()<MAX_RETRY_ATTEMPTS){
            long expected=cancellation.getVersion();
            cancellation.setRetryCount(cancellation.getRetryCount()+1);
            cancellation.setReconciliationCount(cancellation.getReconciliationCount()+1);
            cancellation.setProcessingStatus(A2ACancellationProcessingStatus.RUNTIME_DELIVERY_PENDING);
            cancellation.setReconciliationClassification(
                    A2ACancellationReconciliationClassification.RUNTIME_ACK_MISSING);
            cancellation.setDeadlineAt(occurredAt.plusSeconds(30));
            cancellation.setNextReconcileAt(cancellation.getDeadlineAt());
            cancellation.setLastReconciledAt(occurredAt);cancellation.setUpdatedAt(occurredAt);
            cancellation.setVersion(expected+1);
            cancellation=cancellations.saveExpectedVersion(cancellation,expected);
            publishRuntimeCancel(cancellation,requireRequest(cancellation.getTenantId(),
                    cancellation.getRequestId()).getCorrelationId(),occurredAt);
            append(cancellation,"reconcile:"+cancellation.getRetryCount(),"RECONCILIATION",
                    "retry:"+cancellation.getRetryCount(),null,"RETRY","RUNTIME_ACK_MISSING",
                    "Runtime cancellation command republished",occurredAt);
            return cancellation;
        }
        return timeoutFinal(cancellation,occurredAt);
    }

    @Override @Transactional
    public void timeout(A2ACancellationRecord due,OffsetDateTime occurredAt){reconcile(due,occurredAt);}

    @Override @Transactional(readOnly=true)
    public A2ACancellationReliabilitySnapshot reliability(String tenantId,String cancellationId,int limit){
        A2ACancellationRecord cancellation=requireCancellation(tenantId,cancellationId);
        return new A2ACancellationReliabilitySnapshot(cancellation,
                evidenceRepository.findByCancellation(tenantId,cancellationId,cap(limit)),
                quarantine.findByRequest(tenantId,cancellation.getRequestId(),cap(limit)));
    }

    @Override @Transactional
    public A2ACancellationRecord reconcile(String tenantId,String cancellationId,String actorId,
            String reason,String idempotencyKey){
        requireText(idempotencyKey,"Idempotency-Key");requireText(reason,"Reconciliation reason");
        A2ACancellationRecord value=requireCancellation(tenantId,cancellationId);
        append(value,"operator-reconcile:"+idempotencyKey,"OPERATOR_RECONCILIATION",
                "operator:"+actorId,null,"ACCEPTED","OPERATOR_RECONCILIATION_REQUESTED",
                reason,now());
        return reconcile(value,now());
    }

    @Override @Transactional(readOnly=true) public A2ACancellationRecord get(String tenantId,String id){return requireCancellation(tenantId,id);}
    @Override @Transactional(readOnly=true) public List<A2ACancellationEvidence> evidence(String tenantId,String id,int limit){requireCancellation(tenantId,id);return evidenceRepository.findByCancellation(tenantId,id,cap(limit));}
    @Transactional(readOnly=true) public List<A2ACancellationRecord> recent(String tenantId,int limit){return cancellations.findRecent(tenantId,cap(limit));}
    @Override @Transactional(readOnly=true) public List<A2AReconciliationCase> openCases(String tenantId,int limit){return reconciliationCases.findOpen(tenantId,cap(limit));}

    @Override @Transactional
    public A2AReconciliationCase resolveCase(String tenantId,String caseId,A2AReconciliationStatus status,String actorId,String reason){
        if(status!=A2AReconciliationStatus.RESOLVED&&status!=A2AReconciliationStatus.IGNORED)throw new IllegalArgumentException("Reconciliation status must be RESOLVED or IGNORED");
        A2AReconciliationCase value=reconciliationCases.findById(tenantId,caseId).orElseThrow(()->new IllegalArgumentException("A2A Reconciliation Case not found: "+caseId));
        if(value.getStatus()!=A2AReconciliationStatus.OPEN)return value;
        return resolveCase(value,status,actorId,reason,now());
    }

    private A2ACancellationRecord newRecord(A2ARequest request,TaskRecord child,String cancellationId,
            String actorType,String actorId,String reason,String idempotencyKey,OffsetDateTime now,
            A2APolicy policy,A2ACancellationAuthorityOperations.CancellationAuthorityReceipt receipt){
        A2ACancellationRecord c=new A2ACancellationRecord();c.setTenantId(request.getTenantId());
        c.setCancellationId(cancellationId);c.setRequestId(request.getRequestId());
        c.setChildTaskId(child.getTaskId());c.setAssignmentId(receipt.assignmentId());
        c.setExecutionAttemptId(receipt.executionAttemptId());c.setAttemptNo(receipt.attemptNo());
        c.setDispatchRequestId(receipt.dispatchRequestId());c.setAgentId(receipt.agentId());
        c.setAgentSessionId(receipt.agentSessionId());c.setOwnerGatewayNodeId(receipt.ownerGatewayNodeId());
        c.setRevokedFencingTokenHash(receipt.revokedFencingTokenHash());
        c.setActiveFencingTokenHash(receipt.activeFencingTokenHash());c.setIdempotencyKey(idempotencyKey);
        c.setStatus(receipt.runtimeAcknowledgementRequired()?A2ACancellationStatus.DELIVERY_PENDING:A2ACancellationStatus.ACKNOWLEDGED);
        c.setOutcome(A2ACancellationOutcome.PENDING);
        c.setProcessingStatus(receipt.runtimeAcknowledgementRequired()?A2ACancellationProcessingStatus.RUNTIME_DELIVERY_PENDING:A2ACancellationProcessingStatus.FENCING_ROTATED);
        c.setReconciliationClassification(receipt.runtimeAcknowledgementRequired()?A2ACancellationReconciliationClassification.RUNTIME_ACK_MISSING:A2ACancellationReconciliationClassification.NONE);
        c.setReason(first(reason,"A2A cancellation requested"));c.setRequestedByType(actorType);c.setRequestedById(actorId);
        c.setRequestedAt(now);c.setResultCutoffAt(now);c.setDeadlineAt(now.plus(Duration.ofSeconds(Math.max(30,policy.getTimeoutSeconds()))));
        c.setNextReconcileAt(receipt.runtimeAcknowledgementRequired()?c.getDeadlineAt():null);
        c.setUpdatedAt(now);c.setVersion(1);c.setCancellationFingerprint(A2ACancellationFingerprint.of(c));return c;
    }

    private void createCompletedOutcome(A2ARequest request,TaskRecord child,String actorType,
            String actorId,String reason,String key,OffsetDateTime now){
        A2ACancellationRecord c=terminalRecord(request,child,actorType,actorId,reason,key,now,
                A2ACancellationOutcome.AGENT_ALREADY_COMPLETED,
                A2ACancellationReconciliationClassification.AGENT_ALREADY_COMPLETED);
        cancellations.save(c);append(c,"completed:"+key,"CANCELLATION_DECISION","task:"+child.getTaskId(),null,"OBSERVED","AGENT_ALREADY_COMPLETED","Child Task completed before cancellation authority",now);
    }
    private void createStaleOutcome(A2ARequest request,TaskRecord child,String actorType,
            String actorId,String reason,String key,OffsetDateTime now){
        A2ACancellationRecord c=terminalRecord(request,child,actorType,actorId,reason,key,now,
                A2ACancellationOutcome.STALE_CANCELLATION,
                A2ACancellationReconciliationClassification.STALE_CANCELLATION);
        cancellations.save(c);append(c,"stale:"+key,"CANCELLATION_DECISION","task:"+child.getTaskId(),null,"REJECTED","STALE_CANCELLATION","Child Task was already terminal",now);
    }
    private A2ACancellationRecord terminalRecord(A2ARequest request,TaskRecord child,String actorType,
            String actorId,String reason,String key,OffsetDateTime now,A2ACancellationOutcome outcome,
            A2ACancellationReconciliationClassification classification){
        A2ACancellationRecord c=new A2ACancellationRecord();c.setTenantId(request.getTenantId());c.setCancellationId("a2a-cancel-"+UUID.randomUUID());c.setRequestId(request.getRequestId());c.setChildTaskId(child.getTaskId());c.setIdempotencyKey(key);c.setStatus(A2ACancellationStatus.ACKNOWLEDGED);c.setOutcome(outcome);c.setProcessingStatus(A2ACancellationProcessingStatus.CONFIRMED);c.setReconciliationClassification(classification);c.setReason(first(reason,outcome.name()));c.setRequestedByType(actorType);c.setRequestedById(actorId);c.setRequestedAt(now);c.setResultCutoffAt(now);c.setAcknowledgedAt(now);c.setCompletedAt(now);c.setUpdatedAt(now);c.setVersion(1);c.setCancellationFingerprint(A2ACancellationFingerprint.of(c));return c;
    }

    private A2ACancellationRecord completeAsAgentAlreadyCompleted(A2ACancellationRecord c,
            OffsetDateTime at,String reason){
        long expected=c.getVersion();c.setStatus(A2ACancellationStatus.ACKNOWLEDGED);
        c.setOutcome(A2ACancellationOutcome.AGENT_ALREADY_COMPLETED);
        c.setProcessingStatus(A2ACancellationProcessingStatus.CONFIRMED);
        c.setReconciliationClassification(A2ACancellationReconciliationClassification.AGENT_ALREADY_COMPLETED);
        c.setAcknowledgedAt(at);c.setCompletedAt(at);c.setNextReconcileAt(null);c.setLastError(null);
        c.setUpdatedAt(at);c.setVersion(expected+1);c=cancellations.saveExpectedVersion(c,expected);
        append(c,"agent-completed:"+c.getCancellationId(),"CANCELLATION_DECISION","result:"+c.getRequestId(),null,"OBSERVED","AGENT_ALREADY_COMPLETED",reason,at);
        return c;
    }
    private A2ACancellationRecord markStaleCancellation(A2ACancellationRecord c,String reason,OffsetDateTime at){
        long expected=c.getVersion();c.setStatus(A2ACancellationStatus.WAIT_HUMAN);
        c.setOutcome(A2ACancellationOutcome.STALE_CANCELLATION);
        c.setProcessingStatus(A2ACancellationProcessingStatus.WAIT_HUMAN);
        c.setReconciliationClassification(A2ACancellationReconciliationClassification.STALE_CANCELLATION);
        c.setLastError(reason);c.setCompletedAt(at);c.setNextReconcileAt(null);c.setUpdatedAt(at);c.setVersion(expected+1);
        c=cancellations.saveExpectedVersion(c,expected);markUnconfirmed(c,"STALE_CANCELLATION",reason,true,at);openCase(c,"STALE_CANCELLATION","STALE_CANCELLATION",reason,"Review the current Assignment and never apply this stale cancellation acknowledgement",at);return c;
    }
    private A2ACancellationRecord scheduleRetry(A2ACancellationRecord c,A2ACancellationReconciliationClassification classification,String reason,OffsetDateTime at){
        long expected=c.getVersion();c.setStatus(A2ACancellationStatus.FAILED);c.setOutcome(A2ACancellationOutcome.CANCELLED_UNCONFIRMED);c.setProcessingStatus(A2ACancellationProcessingStatus.FAILED_RETRYABLE);c.setReconciliationClassification(classification);c.setLastError(reason);c.setNextReconcileAt(backoff(at,c.getRetryCount()+1));c.setUpdatedAt(at);c.setVersion(expected+1);c=cancellations.saveExpectedVersion(c,expected);markUnconfirmed(c,"RUNTIME_CANCEL_UNCONFIRMED",reason,false,at);return c;
    }
    private A2ACancellationRecord recoverAcceptedAck(A2ACancellationRecord c,A2ACancellationEvidence ack,OffsetDateTime at){
        long expected=c.getVersion();c.setStatus(A2ACancellationStatus.ACKNOWLEDGED);c.setOutcome(A2ACancellationOutcome.CANCELLED_CONFIRMED);c.setProcessingStatus(A2ACancellationProcessingStatus.CONFIRMED);c.setReconciliationClassification(A2ACancellationReconciliationClassification.PROCESS_CRASH);c.setAcknowledgedAt(ack.getOccurredAt());c.setCompletedAt(at);c.setLastReconciledAt(at);c.setReconciliationCount(c.getReconciliationCount()+1);c.setNextReconcileAt(null);c.setLastError(null);c.setUpdatedAt(at);c.setVersion(expected+1);c=cancellations.saveExpectedVersion(c,expected);append(c,"recover-ack:"+ack.getEventKey(),"RECONCILIATION",ack.getEvidenceId(),ack.getEvidenceHash(),"RECOVERED","PROCESS_CRASH","Recovered cancellation state from append-only ACK evidence",at);confirm(c,"RUNTIME_CANCEL_ACK_RECOVERED","Recovered accepted runtime cancellation ACK","SYSTEM","A2A_RECONCILER",at);return c;
    }
    private A2ACancellationRecord timeoutFinal(A2ACancellationRecord c,OffsetDateTime at){
        long expected=c.getVersion();c.setStatus(A2ACancellationStatus.TIMED_OUT);c.setOutcome(A2ACancellationOutcome.CANCELLATION_TIMEOUT);c.setProcessingStatus(A2ACancellationProcessingStatus.WAIT_HUMAN);c.setReconciliationClassification(A2ACancellationReconciliationClassification.RETRY_EXHAUSTED);c.setLastError("Cancellation acknowledgement retry budget exhausted");c.setLastReconciledAt(at);c.setReconciliationCount(c.getReconciliationCount()+1);c.setCompletedAt(at);c.setNextReconcileAt(null);c.setUpdatedAt(at);c.setVersion(expected+1);c=cancellations.saveExpectedVersion(c,expected);append(c,"timeout-final:"+c.getCancellationId(),"TIMEOUT",String.valueOf(c.getDeadlineAt()),null,"FAILED","CANCELLATION_TIMEOUT",c.getLastError(),at);markUnconfirmed(c,"CANCELLATION_TIMEOUT",c.getLastError(),true,at);openCase(c,"CANCELLATION_TIMEOUT","CANCELLATION_TIMEOUT",c.getLastError(),"Inspect Runtime ACK and fencing evidence, then make a governed decision",at);return c;
    }

    private void publishRuntimeCancel(A2ACancellationRecord c,String correlationId,OffsetDateTime at){
        events.publish(new A2ARuntimeCancelRequestedEvent("evt-"+UUID.randomUUID(),c.getTenantId(),c.getCancellationId(),c.getRequestId(),c.getChildTaskId(),c.getAssignmentId(),c.getExecutionAttemptId(),c.getDispatchRequestId(),c.getAgentId(),c.getAgentSessionId(),c.getOwnerGatewayNodeId(),correlationId,at));
        append(c,"outbox:"+c.getCancellationId()+":"+c.getRetryCount(),"OUTBOX","event:"+A2ARuntimeCancelRequestedEvent.TYPE,null,"PENDING","RUNTIME_CANCEL_OUTBOXED","Runtime cancellation command queued",at);
    }
    private String ackBindingConflict(A2ACancellationRecord c,A2ACancellationAckCommand command){
        String acknowledgedFence=command.activeFencingTokenHash();
        if(Objects.equals(c.getRevokedFencingTokenHash(),acknowledgedFence)){
            acknowledgedFence=c.getActiveFencingTokenHash();
        }
        var decision=bindingGuard.validateAcknowledgement(
                new A2ACancellationBindingGuard.Binding(c.getAssignmentId(),c.getExecutionAttemptId(),
                        c.getAttemptNo(),c.getAgentSessionId(),c.getActiveFencingTokenHash()),
                new A2ACancellationBindingGuard.Binding(command.assignmentId(),command.executionAttemptId(),
                        command.attemptNo(),command.agentSessionId(),acknowledgedFence));
        return decision.accepted()?null:decision.reasonCode();
    }

    private A2ARequest confirm(A2ACancellationRecord c,String code,String reason,String actorType,String actorId,OffsetDateTime at){
        A2ARequest request=requireRequest(c.getTenantId(),c.getRequestId());
        if(request.getRequestStatus()!=A2ARequestStatus.CANCELLED_CONFIRMED){request=transition(request,A2ARequestStatus.CANCELLED_CONFIRMED,code,reason,actorType,actorId,"cancel-confirm:"+c.getCancellationId());}
        TaskRecord child=tasks.requireTask(c.getTenantId(),c.getChildTaskId());
        if(!child.getStatus().isTerminal())tasks.transition(child,TaskStatus.CANCELLED,code,reason,TaskActorType.SYSTEM,first(actorId,"A2A_CORE"),request.getCorrelationId(),"a2a-task-cancel-confirm:"+c.getCancellationId());
        reconciliationCases.findOpenByCancellation(c.getTenantId(),c.getCancellationId()).ifPresent(v->resolveCase(v,A2AReconciliationStatus.RESOLVED,first(actorId,"A2A_CORE"),first(reason,code),at));return request;
    }
    private void markUnconfirmed(A2ACancellationRecord c,String code,String reason,boolean waitHuman,OffsetDateTime at){
        A2ARequest request=requireRequest(c.getTenantId(),c.getRequestId());
        if(request.getRequestStatus()==A2ARequestStatus.CANCEL_REQUESTED)request=transition(request,A2ARequestStatus.CANCELLED_UNCONFIRMED,code,reason,"SYSTEM","A2A_RECONCILER","cancel-unconfirmed:"+c.getCancellationId());
        if(waitHuman&&request.getRequestStatus()==A2ARequestStatus.CANCELLED_UNCONFIRMED)request=transition(request,A2ARequestStatus.WAIT_HUMAN,"A2A_WAIT_HUMAN",reason,"SYSTEM","A2A_RECONCILER","cancel-wait-human:"+c.getCancellationId());
        TaskRecord child=tasks.requireTask(c.getTenantId(),c.getChildTaskId());
        if(waitHuman&&child.getStatus()==TaskStatus.CANCEL_REQUESTED)tasks.transition(child,TaskStatus.WAITING_HUMAN,code,reason,TaskActorType.SYSTEM,"A2A_RECONCILER",request.getCorrelationId(),"a2a-task-wait-human:"+c.getCancellationId());
    }

    private A2ARequest transition(A2ARequest request,A2ARequestStatus target,String code,String reason,String actorType,String actorId,String key){
        long expected=request.getVersion();A2ATransitionCommand command=transitionCommand(request.getRequestStatus(),target);A2ABlockerCode blocker=target==A2ARequestStatus.CANCELLED_UNCONFIRMED?A2ABlockerCode.CANCELLATION_UNCONFIRMED:target==A2ARequestStatus.WAIT_HUMAN?A2ABlockerCode.MANUAL_REVIEW_REQUIRED:A2ABlockerCode.NONE;var decision=stateMachine.decide(request,command,target,blocker,expected,reason,"cancellation:"+key,now());request.setRequestStatus(decision.toStatus());request.setOperationalStage(decision.toStage());request.setBlockerCode(decision.blockerCode());request.setBlockerReason(decision.blockerCode()==A2ABlockerCode.NONE?null:reason);request.setUpdatedAt(now());request.setVersion(decision.resultingVersion());request=requests.saveExpectedVersion(request,expected);record(request,decision,code,actorType,actorId,key);return request;
    }
    private A2ATransitionCommand transitionCommand(A2ARequestStatus source,A2ARequestStatus target){if(target==A2ARequestStatus.CANCELLED_UNCONFIRMED||target==A2ARequestStatus.WAIT_HUMAN)return A2ATransitionCommand.MARK_BLOCKED;if(target==A2ARequestStatus.CANCELLED_CONFIRMED)return A2ATransitionCommand.CONFIRM_CANCEL;return A2ATransitionCommand.CANCEL;}
    private void record(A2ARequest request,com.opensocket.aievent.core.a2a.core.A2ATransitionDecision decision,String code,String actorType,String actorId,String key){A2AStateHistoryEntry e=new A2AStateHistoryEntry();e.setTenantId(request.getTenantId());e.setHistoryId("a2ash-"+UUID.randomUUID());e.setRequestId(request.getRequestId());e.setFromStatus(decision.fromStatus());e.setFromOperationalStage(decision.fromStage());e.setToStatus(decision.toStatus());e.setToOperationalStage(decision.toStage());e.setBlockerCode(decision.blockerCode());e.setTransitionCommand(decision.command());e.setRequiredPermission(decision.requiredPermission());e.setEvidenceType(decision.evidenceType());e.setEvidenceReference(decision.evidenceReference());e.setDomainEventCode(decision.domainEventCode());e.setFailureHandling(decision.failureHandling());e.setTimeoutPolicy(decision.timeoutPolicy());e.setExpectedVersion(decision.expectedVersion());e.setResultingVersion(decision.resultingVersion());e.setRecovery(decision.recovery());e.setReasonCode(code);e.setReason(decision.auditReason());e.setActorType(actorType);e.setActorId(actorId);e.setCorrelationId(request.getCorrelationId());e.setTransitionAt(now());e.setRequestVersion(request.getVersion());e.setIdempotencyKey(key);history.save(e);}
    private void append(A2ACancellationRecord c,String eventKey,String type,String reference,String hash,String decision,String code,String details,OffsetDateTime at){A2ACancellationEvidence e=new A2ACancellationEvidence();e.setTenantId(c.getTenantId());e.setEvidenceId("a2ace-"+UUID.randomUUID());e.setCancellationId(c.getCancellationId());e.setRequestId(c.getRequestId());e.setEventKey(eventKey);e.setAttemptNo(c.getAttemptNo());e.setAssignmentId(c.getAssignmentId());e.setExecutionAttemptId(c.getExecutionAttemptId());e.setEvidenceType(type);e.setEvidenceReference(reference);e.setEvidenceHash(hash);e.setDecision(decision);e.setReasonCode(code);e.setDetails(details);e.setOccurredAt(at);evidenceRepository.append(e);}
    private A2AReconciliationCase resolveCase(A2AReconciliationCase v,A2AReconciliationStatus status,String actorId,String reason,OffsetDateTime at){long expected=v.getVersion();v.setStatus(status);v.setResolvedBy(first(actorId,"unknown-operator"));v.setResolutionReason(first(reason,"Reconciliation case resolved"));v.setResolvedAt(at);v.setUpdatedAt(at);v.setVersion(expected+1);return reconciliationCases.saveExpectedVersion(v,expected);}
    private void openCase(A2ACancellationRecord c,String type,String code,String reason,String action,OffsetDateTime at){if(reconciliationCases.findOpenByCancellation(c.getTenantId(),c.getCancellationId()).isPresent())return;A2AReconciliationCase v=new A2AReconciliationCase();v.setTenantId(c.getTenantId());v.setCaseId("a2arc-"+UUID.randomUUID());v.setRequestId(c.getRequestId());v.setCancellationId(c.getCancellationId());v.setCaseType(type);v.setReasonCode(code);v.setReason(reason);v.setEvidenceSummary("assignment="+c.getAssignmentId()+", attempt="+c.getExecutionAttemptId()+", attemptNo="+c.getAttemptNo()+", dispatch="+c.getDispatchRequestId());v.setRecommendedAction(action);v.setAttemptCount(c.getRetryCount());v.setCreatedAt(at);v.setUpdatedAt(at);v.setVersion(1);reconciliationCases.save(v);}

    private A2ARequest requireRequest(String tenantId,String id){return requests.findById(tenantId,id).orElseThrow(()->new IllegalArgumentException("A2A Request not found: "+id));}
    private A2ACancellationRecord requireCancellation(String tenantId,String id){return cancellations.findById(tenantId,id).orElseThrow(()->new IllegalArgumentException("A2A Cancellation not found: "+id));}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}private OffsetDateTime backoff(OffsetDateTime at,int attempt){return at.plusSeconds(Math.min(300,Math.max(15,15L*(1L<<Math.min(4,Math.max(0,attempt-1))))));}private int cap(int limit){return Math.max(1,Math.min(limit,1000));}private boolean notAfter(OffsetDateTime value,OffsetDateTime cutoff){return value!=null&&cutoff!=null&&!value.isAfter(cutoff);}private void requireText(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required");}private String first(String...values){if(values!=null)for(String value:values)if(value!=null&&!value.isBlank())return value;return null;}
}
