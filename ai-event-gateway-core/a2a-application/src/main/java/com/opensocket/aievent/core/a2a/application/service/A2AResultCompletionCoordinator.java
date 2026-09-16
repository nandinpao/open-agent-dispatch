package com.opensocket.aievent.core.a2a.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.opensocket.aievent.core.a2a.*;
import com.opensocket.aievent.core.a2a.application.port.out.A2ATaskAuthorityOperations;
import com.opensocket.aievent.core.a2a.core.A2AParentAggregationCalculator;
import com.opensocket.aievent.core.a2a.core.A2APolicySnapshotFactory;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.domain.TaskActorType;

/** Deterministic, retryable completion of Child Task and Parent Aggregation after canonical acceptance. */
public class A2AResultCompletionCoordinator {
    private static final int MAX = 1_000;
    private static final int MAX_RECONCILE_ATTEMPTS = 8;
    private final A2AParentAggregationCalculator calculator = new A2AParentAggregationCalculator();
    private final A2APolicySnapshotFactory policySnapshots = new A2APolicySnapshotFactory();
    private final A2ATaskAuthorityOperations taskAuthority;
    private final A2APolicyRepository policies;
    private final A2ARequestRepository requests;
    private final A2AResultRepository results;
    private final A2AResultProcessingRepository processing;
    private final A2AParentAggregationRepository aggregations;
    private final A2AAggregationEvidenceRepository aggregationEvidence;
    private final A2ADomainEventPublisher events;

    public A2AResultCompletionCoordinator(A2ATaskAuthorityOperations taskAuthority, A2APolicyRepository policies,
            A2ARequestRepository requests, A2AResultRepository results, A2AResultProcessingRepository processing,
            A2AParentAggregationRepository aggregations, A2AAggregationEvidenceRepository aggregationEvidence,
            A2ADomainEventPublisher events) {
        this.taskAuthority=taskAuthority;this.policies=policies;this.requests=requests;this.results=results;
        this.processing=processing;this.aggregations=aggregations;this.aggregationEvidence=aggregationEvidence;this.events=events;
    }

    public A2AResultProcessing process(String tenantId, String resultId, String actor) {
        A2AResult result=results.findById(tenantId,resultId).orElseThrow(()->new IllegalArgumentException("A2A Result not found: "+resultId));
        A2ARequest request=requests.findById(tenantId,result.getRequestId()).orElseThrow(()->new IllegalStateException("A2A_REQUEST_NOT_FOUND_FOR_RESULT"));
        A2AResultProcessing state=processing.findByResult(tenantId,resultId).orElseGet(()->processing.save(initial(result)));
        if(state.getProcessingStatus()==A2AResultProcessingStatus.COMPLETED)return state;
        try {
            requirePolicyBinding(request,result);
            if(state.getProcessingStatus()==A2AResultProcessingStatus.CHILD_COMPLETION_PENDING
                    || state.getProcessingStatus()==A2AResultProcessingStatus.FAILED_RETRYABLE){
                completeChild(request,result);
                state=advance(state,A2AResultProcessingStatus.CHILD_COMPLETED,A2AResultReconciliationClassification.NONE,null,null,false);
            }
            if(state.getProcessingStatus()==A2AResultProcessingStatus.CHILD_COMPLETED){
                state=advance(state,A2AResultProcessingStatus.PARENT_AGGREGATION_PENDING,A2AResultReconciliationClassification.NONE,null,null,false);
            }
            if(state.getProcessingStatus()==A2AResultProcessingStatus.PARENT_AGGREGATION_PENDING){
                A2AParentAggregation aggregation=recomputeParent(request,result);
                completeParent(request,aggregation);
                state=advance(state,A2AResultProcessingStatus.COMPLETED,A2AResultReconciliationClassification.NONE,null,null,true);
                publish(request,result,now());
            }
            return state;
        } catch(Exception ex){
            return failure(state,ex);
        }
    }

    private void completeChild(A2ARequest request,A2AResult result){
        TaskRecord child=taskAuthority.requireTask(request.getTenantId(),request.getChildTaskId());
        TaskStatus target=childTarget(result.getResultStatus());
        if(child.getStatus()==target)return;
        if(child.getStatus().isTerminal())throw new IllegalStateException("CHILD_TASK_TERMINAL_CONFLICT:"+child.getStatus());
        taskAuthority.transition(child,target,"A2A_RESULT_"+result.getResultStatus().name(),first(result.getResultSummary(),"Canonical A2A Result accepted"),TaskActorType.AGENT,first(result.getCompletedByAgentId(),"A2A_RESULT_AUTHORITY"),request.getCorrelationId(),"a2a-result-task:"+result.getResultFingerprint());
    }

    private A2AParentAggregation recomputeParent(A2ARequest request,A2AResult result){
        List<A2ARequest> siblings=requests.findBySourceTask(request.getTenantId(),request.getSourceTaskId(),MAX);
        Binding binding=resolveBinding(request.getTenantId(),siblings);
        List<A2AResult> childResults=results.findByParentTask(request.getTenantId(),request.getSourceTaskId(),MAX);
        for(int retry=1;retry<=3;retry++){
            Optional<A2AParentAggregation> current=aggregations.findByParentTask(request.getTenantId(),request.getSourceTaskId());
            A2AParentAggregation calculated=calculator.calculate(request.getTenantId(),request.getSourceTaskId(),binding.policy(),binding.quorum(),binding.policyVersion(),binding.policyHash(),siblings,childResults,result.getResultId());
            calculated.setComputedAt(now());
            if(current.isPresent() && current.get().getComputationHash().equals(calculated.getComputationHash()))return current.get();
            long expected=current.map(A2AParentAggregation::getVersion).orElse(0L);
            try {
                A2AParentAggregation persisted;
                if(current.isEmpty()){calculated.setVersion(1L);persisted=aggregations.save(calculated);}
                else {calculated.setVersion(expected+1);persisted=aggregations.saveExpectedVersion(calculated,expected);}
                saveAggregationEvidence(result,persisted,"AGGREGATION_APPLIED",expected,persisted.getVersion(),retry);
                return persisted;
            }catch(IllegalStateException ex){
                if(!"RESOURCE_VERSION_CONFLICT".equals(ex.getMessage())||retry==3){
                    saveAggregationEvidence(result,calculated,"AGGREGATION_CAS_CONFLICT",expected,expected,retry);
                    throw new IllegalStateException("AGGREGATION_CAS_CONFLICT",ex);
                }
            }
        }
        throw new IllegalStateException("AGGREGATION_CAS_CONFLICT");
    }

    private void completeParent(A2ARequest request,A2AParentAggregation aggregation){
        TaskRecord parent=taskAuthority.requireTask(request.getTenantId(),request.getSourceTaskId());
        Binding binding=resolveBinding(request.getTenantId(),requests.findBySourceTask(request.getTenantId(),request.getSourceTaskId(),MAX));
        TaskStatus target=aggregateTarget(aggregation.getAggregateStatus(),binding.failurePropagationPolicy());
        if(target==null||parent.getStatus()==target)return;
        if(parent.getStatus().isTerminal())return;
        taskAuthority.transition(parent,target,"A2A_PARENT_AGGREGATED",aggregation.getSummary()+"; decision="+aggregation.getDecisionReason()+"; failurePropagation="+binding.failurePropagationPolicy(),TaskActorType.SYSTEM,"A2A_AGGREGATOR",request.getCorrelationId(),"a2a-parent-aggregate:"+aggregation.getComputationHash());
    }

    private Binding resolveBinding(String tenantId,List<A2ARequest> siblings){
        if (siblings == null || siblings.isEmpty()) {
            return new Binding(A2AResultAggregationPolicy.MANUAL_DECISION, 1, A2AFailurePropagationPolicy.WAIT_HUMAN, 0L, null);
        }
        List<Binding> bindings=siblings.stream().map(r->binding(tenantId,r)).toList();
        Set<A2AResultAggregationPolicy> policiesSet=bindings.stream().map(Binding::policy).map(A2AResultAggregationPolicy::canonical).collect(java.util.stream.Collectors.toSet());
        Set<Integer> quorumSet=bindings.stream().map(Binding::quorum).collect(java.util.stream.Collectors.toSet());
        Set<A2AFailurePropagationPolicy> failurePolicies=bindings.stream().map(Binding::failurePropagationPolicy).collect(java.util.stream.Collectors.toSet());
        if(policiesSet.size()!=1||quorumSet.size()!=1||failurePolicies.size()!=1){
            return new Binding(A2AResultAggregationPolicy.MANUAL_DECISION,1,A2AFailurePropagationPolicy.WAIT_HUMAN,
                    bindings.stream().mapToLong(Binding::policyVersion).max().orElse(0L),combinedPolicyHash(bindings));
        }
        return new Binding(policiesSet.iterator().next(),quorumSet.iterator().next(),failurePolicies.iterator().next(),
                bindings.stream().mapToLong(Binding::policyVersion).max().orElse(0L),combinedPolicyHash(bindings));
    }

    /**
     * Parent aggregation binds to the deterministic set of directional policy snapshots. Different
     * ERP->MES and ERP->HR snapshot hashes are expected; equality of the whole policy documents is
     * not an aggregation requirement.
     */
    private String combinedPolicyHash(List<Binding> bindings){
        List<String> hashes=bindings.stream().map(Binding::policyHash).filter(v->v!=null&&!v.isBlank()).sorted().toList();
        if(hashes.isEmpty())return null;
        String material=String.join("|",hashes);
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private Binding binding(String tenantId,A2ARequest request){
        A2ADirectionalPolicySnapshot snapshot=request.getPolicySnapshot();
        if(snapshot!=null){requirePolicyBinding(request,null);return new Binding(snapshot.aggregationPolicy().canonical(),Math.max(1,snapshot.aggregationQuorum()),snapshot.failurePropagationPolicy(),snapshot.policyVersion(),snapshot.snapshotHash());}
        A2APolicy policy=policies.findById(tenantId,request.getPolicyId()).orElseThrow(()->new IllegalStateException("A2A_POLICY_NOT_FOUND"));
        return new Binding(policy.getResultAggregationPolicy().canonical(),Math.max(1,policy.getAggregationQuorum()),policy.getFailurePropagationPolicy(),policy.getVersion(),null);
    }

    private void requirePolicyBinding(A2ARequest request,A2AResult result){
        A2ADirectionalPolicySnapshot snapshot=request.getPolicySnapshot();
        if(snapshot==null)return;
        if(!policySnapshots.verify(snapshot)||request.getPolicyVersion()!=snapshot.policyVersion()||!same(request.getPolicySnapshotHash(),snapshot.snapshotHash()))throw new IllegalStateException("A2A_POLICY_BINDING_INVALID");
        if(result!=null&&(result.getPolicyVersion()!=snapshot.policyVersion()||!same(result.getPolicySnapshotHash(),snapshot.snapshotHash())))throw new IllegalStateException("A2A_RESULT_POLICY_BINDING_INVALID");
    }

    private A2AResultProcessing initial(A2AResult result){A2AResultProcessing v=new A2AResultProcessing();v.setTenantId(result.getTenantId());v.setResultId(result.getResultId());v.setRequestId(result.getRequestId());v.setParentTaskId(result.getParentTaskId());v.setChildTaskId(result.getChildTaskId());v.setProcessingStatus(A2AResultProcessingStatus.CHILD_COMPLETION_PENDING);v.setCreatedAt(now());v.setUpdatedAt(now());v.setNextReconcileAt(now());return v;}
    private A2AResultProcessing advance(A2AResultProcessing v,A2AResultProcessingStatus status,A2AResultReconciliationClassification classification,String code,String error,boolean complete){long expected=v.getRowVersion();v.setProcessingStatus(status);v.setReconciliationClassification(classification);v.setLastErrorCode(code);v.setLastError(error);v.setNextReconcileAt(complete?null:now());v.setLastReconciledAt(now());v.setUpdatedAt(now());v.setCompletedAt(complete?now():null);v.setRowVersion(expected+1);return processing.saveExpectedVersion(v,expected);}
    private A2AResultProcessing failure(A2AResultProcessing v,Exception ex){int attempt=v.getAttemptCount()+1;A2AResultReconciliationClassification classification=classification(ex);long expected=v.getRowVersion();v.setAttemptCount(attempt);v.setLastReconciledAt(now());v.setUpdatedAt(now());v.setLastErrorCode(code(ex));v.setLastError(trim(ex.getMessage()));if(attempt>=MAX_RECONCILE_ATTEMPTS){v.setProcessingStatus(A2AResultProcessingStatus.WAIT_HUMAN);v.setReconciliationClassification(A2AResultReconciliationClassification.RETRY_EXHAUSTED);v.setNextReconcileAt(null);}else{v.setProcessingStatus(A2AResultProcessingStatus.FAILED_RETRYABLE);v.setReconciliationClassification(classification);v.setNextReconcileAt(now().plusSeconds(Math.min(900L,15L*(1L<<Math.min(6,attempt-1)))));}v.setRowVersion(expected+1);try{return processing.saveExpectedVersion(v,expected);}catch(IllegalStateException conflict){return processing.findByResult(v.getTenantId(),v.getResultId()).orElse(v);}}
    private A2AResultReconciliationClassification classification(Exception ex){String c=code(ex);if(c.contains("POLICY"))return A2AResultReconciliationClassification.POLICY_BINDING_INVALID;if(c.contains("AGGREGATION_CAS"))return A2AResultReconciliationClassification.AGGREGATION_CAS_CONFLICT;if(c.contains("CHILD_TASK"))return A2AResultReconciliationClassification.CHILD_COMPLETION_MISSING;if(c.contains("PARENT")||c.contains("AGGREGATION"))return A2AResultReconciliationClassification.PARENT_AGGREGATION_MISSING;return A2AResultReconciliationClassification.PROCESSING_ERROR;}
    private String code(Exception ex){String m=ex.getMessage();if(m==null||m.isBlank())return ex.getClass().getSimpleName().toUpperCase();int i=m.indexOf(':');return (i>0?m.substring(0,i):m).replace(' ','_').toUpperCase();}
    private void saveAggregationEvidence(A2AResult result,A2AParentAggregation aggregation,String type,long expected,long resulting,int attempt){A2AAggregationEvidence e=new A2AAggregationEvidence();e.setTenantId(result.getTenantId());e.setEvidenceId("a2a-agg-ev-"+UUID.randomUUID());e.setParentTaskId(result.getParentTaskId());e.setResultId(result.getResultId());e.setEvidenceType(type);e.setComputationHash(aggregation.getComputationHash());e.setAggregateStatus(aggregation.getAggregateStatus());e.setDecisionReason(aggregation.getDecisionReason());e.setExpectedVersion(expected);e.setResultingVersion(resulting);e.setAttemptNo(attempt);e.setOccurredAt(now());aggregationEvidence.save(e);}
    private void publish(A2ARequest request,A2AResult result,OffsetDateTime at){A2ADomainEventType type=switch(result.getResultStatus()){case SUCCEEDED,PARTIAL->A2ADomainEventType.A2A_COMPLETED;case FAILED->A2ADomainEventType.A2A_FAILED;case CANCELLED->A2ADomainEventType.A2A_CANCELLED_CONFIRMED;};events.publish(new A2ADomainEvent("evt-"+UUID.randomUUID(),type,request.getTenantId(),request.getRequestId(),request.getRootTaskId(),request.getCorrelationId(),result.getCallbackInboxId(),"AGENT",result.getCompletedByAgentId(),at,2,Map.of("resultId",result.getResultId(),"status",result.getResultStatus().name(),"resultFingerprint",result.getResultFingerprint(),"policyVersion",result.getPolicyVersion())));}
    private TaskStatus childTarget(A2AResultStatus s){return switch(s){case SUCCEEDED->TaskStatus.COMPLETED;case PARTIAL->TaskStatus.PARTIALLY_COMPLETED;case FAILED->TaskStatus.FAILED;case CANCELLED->TaskStatus.CANCELLED;};}
    private TaskStatus aggregateTarget(String s,A2AFailurePropagationPolicy failurePolicy){return switch(s==null?"":s){
        case "SUCCESS"->TaskStatus.COMPLETED;
        case "PARTIAL"->TaskStatus.PARTIALLY_COMPLETED;
        case "FAILURE"->switch(failurePolicy==null?A2AFailurePropagationPolicy.WAIT_HUMAN:failurePolicy){
            case FAIL_PARENT->TaskStatus.FAILED;
            case BLOCK_PARENT->TaskStatus.BLOCKED;
            case MARK_PARTIAL,CONTINUE->TaskStatus.PARTIALLY_COMPLETED;
            case WAIT_HUMAN->TaskStatus.WAITING_HUMAN;
        };
        case "WAIT_HUMAN"->TaskStatus.WAITING_HUMAN;
        case "PENDING"->TaskStatus.WAITING_DEPENDENCY;
        default->null;};}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
    private String first(String...v){for(String x:v)if(x!=null&&!x.isBlank())return x;return null;} private String trim(String v){return v==null?null:v.substring(0,Math.min(v.length(),2000));} private boolean same(Object a,Object b){return java.util.Objects.equals(a,b);}
    private record Binding(A2AResultAggregationPolicy policy,int quorum,A2AFailurePropagationPolicy failurePropagationPolicy,long policyVersion,String policyHash){}
}
