package com.opensocket.aievent.database.persistence.handoff.po;
import java.time.OffsetDateTime;
public class AgentContextAccessEventPo {
 private String tenantId;
 private String accessEventId;
 private String taskId;
 private String assignmentId;
 private String dispatchRequestId;
 private String agentId;
 private String agentSessionId;
 private String snapshotId;
 private int snapshotVersion;
 private String accessDecision;
 private String reasonCode;
 private String dispatchTokenHash;
 private String clientAddress;
 private String correlationId;
 private OffsetDateTime accessedAt;
 public String getTenantId(){return tenantId;} public void setTenantId(String tenantId){this.tenantId=tenantId;}
 public String getAccessEventId(){return accessEventId;} public void setAccessEventId(String accessEventId){this.accessEventId=accessEventId;}
 public String getTaskId(){return taskId;} public void setTaskId(String taskId){this.taskId=taskId;}
 public String getAssignmentId(){return assignmentId;} public void setAssignmentId(String assignmentId){this.assignmentId=assignmentId;}
 public String getDispatchRequestId(){return dispatchRequestId;} public void setDispatchRequestId(String dispatchRequestId){this.dispatchRequestId=dispatchRequestId;}
 public String getAgentId(){return agentId;} public void setAgentId(String agentId){this.agentId=agentId;}
 public String getAgentSessionId(){return agentSessionId;} public void setAgentSessionId(String agentSessionId){this.agentSessionId=agentSessionId;}
 public String getSnapshotId(){return snapshotId;} public void setSnapshotId(String snapshotId){this.snapshotId=snapshotId;}
 public int getSnapshotVersion(){return snapshotVersion;} public void setSnapshotVersion(int snapshotVersion){this.snapshotVersion=snapshotVersion;}
 public String getAccessDecision(){return accessDecision;} public void setAccessDecision(String accessDecision){this.accessDecision=accessDecision;}
 public String getReasonCode(){return reasonCode;} public void setReasonCode(String reasonCode){this.reasonCode=reasonCode;}
 public String getDispatchTokenHash(){return dispatchTokenHash;} public void setDispatchTokenHash(String dispatchTokenHash){this.dispatchTokenHash=dispatchTokenHash;}
 public String getClientAddress(){return clientAddress;} public void setClientAddress(String clientAddress){this.clientAddress=clientAddress;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String correlationId){this.correlationId=correlationId;}
 public OffsetDateTime getAccessedAt(){return accessedAt;} public void setAccessedAt(OffsetDateTime accessedAt){this.accessedAt=accessedAt;}
}
