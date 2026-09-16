package com.opensocket.aievent.core.a2a;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!prod")
@ConditionalOnProperty(prefix="task",name="store",havingValue="MEMORY",matchIfMissing=true)
public class InMemoryA2ACancellationRepository implements A2ACancellationRepository {
    private static final Set<A2ACancellationProcessingStatus> DUE=Set.of(
            A2ACancellationProcessingStatus.RUNTIME_DELIVERY_PENDING,
            A2ACancellationProcessingStatus.RUNTIME_ACK_PENDING,
            A2ACancellationProcessingStatus.FAILED_RETRYABLE);
    private final Map<String,A2ACancellationRecord> values=new ConcurrentHashMap<>();

    @Override public A2ACancellationRecord save(A2ACancellationRecord value){values.put(key(value.getTenantId(),value.getCancellationId()),copy(value));return copy(value);}
    @Override public A2ACancellationRecord saveExpectedVersion(A2ACancellationRecord value,long expectedVersion){
        synchronized(values){String key=key(value.getTenantId(),value.getCancellationId());A2ACancellationRecord current=values.get(key);if(current==null||current.getVersion()!=expectedVersion)throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");values.put(key,copy(value));return copy(value);}
    }
    @Override public Optional<A2ACancellationRecord> findById(String tenantId,String cancellationId){return Optional.ofNullable(values.get(key(tenantId,cancellationId))).map(this::copy);}
    @Override public Optional<A2ACancellationRecord> findByRequest(String tenantId,String requestId){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())&&requestId.equals(v.getRequestId())).findFirst().map(this::copy);}
    @Override public Optional<A2ACancellationRecord> findByIdempotencyKey(String tenantId,String value){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())&&Objects.equals(value,v.getIdempotencyKey())).findFirst().map(this::copy);}
    @Override public List<A2ACancellationRecord> findDue(OffsetDateTime now,int limit){return values.values().stream().filter(v->DUE.contains(v.getProcessingStatus())).filter(v->{OffsetDateTime due=v.getNextReconcileAt()==null?v.getDeadlineAt():v.getNextReconcileAt();return due!=null&&!due.isAfter(now);}).sorted(Comparator.comparing(v->v.getNextReconcileAt()==null?v.getDeadlineAt():v.getNextReconcileAt())).limit(cap(limit)).map(this::copy).toList();}
    @Override public List<A2ACancellationRecord> findRecent(String tenantId,int limit){return values.values().stream().filter(v->tenantId.equals(v.getTenantId())).sorted(Comparator.comparing(A2ACancellationRecord::getRequestedAt,Comparator.nullsFirst(Comparator.naturalOrder())).reversed()).limit(cap(limit)).map(this::copy).toList();}
    @Override public String mode(){return "MEMORY";}

    private A2ACancellationRecord copy(A2ACancellationRecord s){A2ACancellationRecord d=new A2ACancellationRecord();d.setTenantId(s.getTenantId());d.setCancellationId(s.getCancellationId());d.setRequestId(s.getRequestId());d.setChildTaskId(s.getChildTaskId());d.setAssignmentId(s.getAssignmentId());d.setExecutionAttemptId(s.getExecutionAttemptId());d.setAttemptNo(s.getAttemptNo());d.setDispatchRequestId(s.getDispatchRequestId());d.setAgentId(s.getAgentId());d.setAgentSessionId(s.getAgentSessionId());d.setOwnerGatewayNodeId(s.getOwnerGatewayNodeId());d.setRevokedFencingTokenHash(s.getRevokedFencingTokenHash());d.setActiveFencingTokenHash(s.getActiveFencingTokenHash());d.setCancellationFingerprint(s.getCancellationFingerprint());d.setIdempotencyKey(s.getIdempotencyKey());d.setStatus(s.getStatus());d.setOutcome(s.getOutcome());d.setProcessingStatus(s.getProcessingStatus());d.setReconciliationClassification(s.getReconciliationClassification());d.setReason(s.getReason());d.setRequestedByType(s.getRequestedByType());d.setRequestedById(s.getRequestedById());d.setDeliveryStatus(s.getDeliveryStatus());d.setLastError(s.getLastError());d.setRetryCount(s.getRetryCount());d.setReconciliationCount(s.getReconciliationCount());d.setRequestedAt(s.getRequestedAt());d.setResultCutoffAt(s.getResultCutoffAt());d.setDeliveryAt(s.getDeliveryAt());d.setAcknowledgedAt(s.getAcknowledgedAt());d.setDeadlineAt(s.getDeadlineAt());d.setNextReconcileAt(s.getNextReconcileAt());d.setLastReconciledAt(s.getLastReconciledAt());d.setCompletedAt(s.getCompletedAt());d.setUpdatedAt(s.getUpdatedAt());d.setVersion(s.getVersion());return d;}
    private String key(String tenantId,String id){return tenantId+"|"+id;}private int cap(int limit){return Math.max(1,Math.min(limit,1000));}
}
