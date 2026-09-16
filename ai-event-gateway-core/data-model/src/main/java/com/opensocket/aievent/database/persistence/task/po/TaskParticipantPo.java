package com.opensocket.aievent.database.persistence.task.po;

import java.time.OffsetDateTime;

public class TaskParticipantPo {
    private String tenantId; private String participantId; private String taskId;
    private String participantType; private String participantRefId; private String participantRole;
    private String visibilityLevel; private String operationLevel; private OffsetDateTime createdAt;
    private String createdBy; private long version;
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getParticipantId(){return participantId;} public void setParticipantId(String v){participantId=v;}
    public String getTaskId(){return taskId;} public void setTaskId(String v){taskId=v;}
    public String getParticipantType(){return participantType;} public void setParticipantType(String v){participantType=v;}
    public String getParticipantRefId(){return participantRefId;} public void setParticipantRefId(String v){participantRefId=v;}
    public String getParticipantRole(){return participantRole;} public void setParticipantRole(String v){participantRole=v;}
    public String getVisibilityLevel(){return visibilityLevel;} public void setVisibilityLevel(String v){visibilityLevel=v;}
    public String getOperationLevel(){return operationLevel;} public void setOperationLevel(String v){operationLevel=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;}
    public long getVersion(){return version;} public void setVersion(long v){version=v;}
}
