package com.opensocket.aievent.database.persistence.task.po;

import java.time.OffsetDateTime;

public class TaskStateHistoryPo {
    private String tenantId; private String historyId; private String taskId;
    private String fromStatus; private String toStatus; private String reasonCode; private String reason;
    private String actorType; private String actorId; private String correlationId;
    private OffsetDateTime transitionAt; private long taskVersion; private String idempotencyKey;
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getHistoryId(){return historyId;} public void setHistoryId(String v){historyId=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getFromStatus(){return fromStatus;} public void setFromStatus(String v){fromStatus=v;}
    public String getToStatus(){return toStatus;} public void setToStatus(String v){toStatus=v;}
    public String getReasonCode(){return reasonCode;} public void setReasonCode(String v){reasonCode=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;}
    public String getActorType(){return actorType;} public void setActorType(String v){actorType=v;}
    public String getActorId(){return actorId;} public void setActorId(String v){actorId=v;}
    public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
    public OffsetDateTime getTransitionAt(){return transitionAt;} public void setTransitionAt(OffsetDateTime v){transitionAt=v;}
    public long getTaskVersion(){return taskVersion;} public void setTaskVersion(long v){taskVersion=v;}
    public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;}
}
