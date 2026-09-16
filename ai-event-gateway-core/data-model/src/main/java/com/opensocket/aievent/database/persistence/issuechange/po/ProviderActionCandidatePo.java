package com.opensocket.aievent.database.persistence.issuechange.po;
import java.time.OffsetDateTime;

public class ProviderActionCandidatePo {
 private String tenantId;
 private String candidateId;
 private String conflictId;
 private String observationId;
 private String connectionId;
 private String externalProjectId;
 private String externalIssueId;
 private String candidateType;
 private String riskLevel;
 private String status;
 private String requestedCommandJson;
 private String requestHash;
 private String providerIdentityHash;
 private boolean requiresReauthentication;
 private boolean requiresApproval;
 private String reviewedBy;
 private OffsetDateTime reviewedAt;
 private String approvedBy;
 private OffsetDateTime approvedAt;
 private String executedBy;
 private OffsetDateTime executedAt;
 private String executionEvidence;
 private String decisionReason;
 private String idempotencyKey;
 private long version;
 private String correlationId;
 private OffsetDateTime createdAt;
 private OffsetDateTime updatedAt;
 public String getTenantId(){return tenantId;} public void setTenantId(String value){this.tenantId=value;}
 public String getCandidateId(){return candidateId;} public void setCandidateId(String value){this.candidateId=value;}
 public String getConflictId(){return conflictId;} public void setConflictId(String value){this.conflictId=value;}
 public String getObservationId(){return observationId;} public void setObservationId(String value){this.observationId=value;}
 public String getConnectionId(){return connectionId;} public void setConnectionId(String value){this.connectionId=value;}
 public String getExternalProjectId(){return externalProjectId;} public void setExternalProjectId(String value){this.externalProjectId=value;}
 public String getExternalIssueId(){return externalIssueId;} public void setExternalIssueId(String value){this.externalIssueId=value;}
 public String getCandidateType(){return candidateType;} public void setCandidateType(String value){this.candidateType=value;}
 public String getRiskLevel(){return riskLevel;} public void setRiskLevel(String value){this.riskLevel=value;}
 public String getStatus(){return status;} public void setStatus(String value){this.status=value;}
 public String getRequestedCommandJson(){return requestedCommandJson;} public void setRequestedCommandJson(String value){this.requestedCommandJson=value;}
 public String getRequestHash(){return requestHash;} public void setRequestHash(String value){this.requestHash=value;}
 public String getProviderIdentityHash(){return providerIdentityHash;} public void setProviderIdentityHash(String value){this.providerIdentityHash=value;}
 public boolean isRequiresReauthentication(){return requiresReauthentication;} public void setRequiresReauthentication(boolean value){this.requiresReauthentication=value;}
 public boolean isRequiresApproval(){return requiresApproval;} public void setRequiresApproval(boolean value){this.requiresApproval=value;}
 public String getReviewedBy(){return reviewedBy;} public void setReviewedBy(String value){this.reviewedBy=value;}
 public OffsetDateTime getReviewedAt(){return reviewedAt;} public void setReviewedAt(OffsetDateTime value){this.reviewedAt=value;}
 public String getApprovedBy(){return approvedBy;} public void setApprovedBy(String value){this.approvedBy=value;}
 public OffsetDateTime getApprovedAt(){return approvedAt;} public void setApprovedAt(OffsetDateTime value){this.approvedAt=value;}
 public String getExecutedBy(){return executedBy;} public void setExecutedBy(String value){this.executedBy=value;}
 public OffsetDateTime getExecutedAt(){return executedAt;} public void setExecutedAt(OffsetDateTime value){this.executedAt=value;}
 public String getExecutionEvidence(){return executionEvidence;} public void setExecutionEvidence(String value){this.executionEvidence=value;}
 public String getDecisionReason(){return decisionReason;} public void setDecisionReason(String value){this.decisionReason=value;}
 public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String value){this.idempotencyKey=value;}
 public long getVersion(){return version;} public void setVersion(long value){this.version=value;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String value){this.correlationId=value;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime value){this.createdAt=value;}
 public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime value){this.updatedAt=value;}
}
