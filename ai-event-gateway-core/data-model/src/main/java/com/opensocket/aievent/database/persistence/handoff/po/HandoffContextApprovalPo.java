package com.opensocket.aievent.database.persistence.handoff.po;
import java.time.OffsetDateTime;
public class HandoffContextApprovalPo {
 private String tenantId;
 private String approvalId;
 private String snapshotId;
 private String decision;
 private String actorType;
 private String actorId;
 private String reason;
 private String idempotencyKey;
 private OffsetDateTime decidedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String tenantId){this.tenantId=tenantId;}
 public String getApprovalId(){return approvalId;} public void setApprovalId(String approvalId){this.approvalId=approvalId;}
 public String getSnapshotId(){return snapshotId;} public void setSnapshotId(String snapshotId){this.snapshotId=snapshotId;}
 public String getDecision(){return decision;} public void setDecision(String decision){this.decision=decision;}
 public String getActorType(){return actorType;} public void setActorType(String actorType){this.actorType=actorType;}
 public String getActorId(){return actorId;} public void setActorId(String actorId){this.actorId=actorId;}
 public String getReason(){return reason;} public void setReason(String reason){this.reason=reason;}
 public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String idempotencyKey){this.idempotencyKey=idempotencyKey;}
 public OffsetDateTime getDecidedAt(){return decidedAt;} public void setDecidedAt(OffsetDateTime decidedAt){this.decidedAt=decidedAt;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String correlationId){this.correlationId=correlationId;}
}
