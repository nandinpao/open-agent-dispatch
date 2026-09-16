package com.opensocket.aievent.core.outbox;

import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public class OutboxEventRecord {
    @ToString.Include
    private String outboxId;
    @ToString.Include
    private String eventId;
    private String eventType;
    private String aggregateType;
    @ToString.Include
    private String aggregateId;
    private String payloadJson;
    private String payloadVersion;
    private String tenantId;
    private String rootTaskId;
    private String taskId;
    private String correlationId;
    private String causationId;
    private String traceId;
    private String spanId;
    private String actorType;
    private String actorId;
    private String lineageStatus;
    private OutboxEventStatus status;
    private int attemptCount;
    private OffsetDateTime nextAttemptAt;
    private String lastError;
    private String claimedBy;
    private OffsetDateTime claimUntil;
    private OffsetDateTime createdAt;
    private OffsetDateTime publishedAt;
    private OffsetDateTime updatedAt;
    public void setOutboxId(String v){outboxId=v;}
    public void setEventId(String v){eventId=v;}
    public void setEventType(String v){eventType=v;}
    public void setAggregateType(String v){aggregateType=v;}
    public void setAggregateId(String v){aggregateId=v;}
    public void setPayloadJson(String v){payloadJson=v;}
    public void setPayloadVersion(String v){payloadVersion=v;}
    public void setTenantId(String v){tenantId=v;}
    public void setRootTaskId(String v){rootTaskId=v;}
    public void setTaskId(String v){taskId=v;}
    public void setCorrelationId(String v){correlationId=v;}
    public void setCausationId(String v){causationId=v;}
    public void setTraceId(String v){traceId=v;}
    public void setSpanId(String v){spanId=v;}
    public void setActorType(String v){actorType=v;}
    public void setActorId(String v){actorId=v;}
    public void setLineageStatus(String v){lineageStatus=v;}
    public void setStatus(OutboxEventStatus v){status=v;}
    public void setAttemptCount(int v){attemptCount=Math.max(0,v);}
    public void setNextAttemptAt(OffsetDateTime v){nextAttemptAt=v;}
    public void setLastError(String v){lastError=v;}
    public void setClaimedBy(String v){claimedBy=v;}
    public void setClaimUntil(OffsetDateTime v){claimUntil=v;}
    public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public void setPublishedAt(OffsetDateTime v){publishedAt=v;}
    public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
}
