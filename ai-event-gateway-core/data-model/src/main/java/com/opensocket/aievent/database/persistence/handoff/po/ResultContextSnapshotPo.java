package com.opensocket.aievent.database.persistence.handoff.po;
import java.time.OffsetDateTime;
public class ResultContextSnapshotPo {
 private String tenantId;
 private String resultSnapshotId;
 private String rootTaskId;
 private String sourceTaskId;
 private String targetTaskId;
 private String policyId;
 private int snapshotVersion;
 private String resultSummary;
 private String sharedEvidenceRefsJson;
 private String maskedOutputJson;
 private String omittedOutputReasonsJson;
 private String resultContentHash;
 private String createdByAgentId;
 private OffsetDateTime createdAt;
 private String status;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String tenantId){this.tenantId=tenantId;}
 public String getResultSnapshotId(){return resultSnapshotId;} public void setResultSnapshotId(String resultSnapshotId){this.resultSnapshotId=resultSnapshotId;}
 public String getRootTaskId(){return rootTaskId;} public void setRootTaskId(String rootTaskId){this.rootTaskId=rootTaskId;}
 public String getSourceTaskId(){return sourceTaskId;} public void setSourceTaskId(String sourceTaskId){this.sourceTaskId=sourceTaskId;}
 public String getTargetTaskId(){return targetTaskId;} public void setTargetTaskId(String targetTaskId){this.targetTaskId=targetTaskId;}
 public String getPolicyId(){return policyId;} public void setPolicyId(String policyId){this.policyId=policyId;}
 public int getSnapshotVersion(){return snapshotVersion;} public void setSnapshotVersion(int snapshotVersion){this.snapshotVersion=snapshotVersion;}
 public String getResultSummary(){return resultSummary;} public void setResultSummary(String resultSummary){this.resultSummary=resultSummary;}
 public String getSharedEvidenceRefsJson(){return sharedEvidenceRefsJson;} public void setSharedEvidenceRefsJson(String sharedEvidenceRefsJson){this.sharedEvidenceRefsJson=sharedEvidenceRefsJson;}
 public String getMaskedOutputJson(){return maskedOutputJson;} public void setMaskedOutputJson(String maskedOutputJson){this.maskedOutputJson=maskedOutputJson;}
 public String getOmittedOutputReasonsJson(){return omittedOutputReasonsJson;} public void setOmittedOutputReasonsJson(String omittedOutputReasonsJson){this.omittedOutputReasonsJson=omittedOutputReasonsJson;}
 public String getResultContentHash(){return resultContentHash;} public void setResultContentHash(String resultContentHash){this.resultContentHash=resultContentHash;}
 public String getCreatedByAgentId(){return createdByAgentId;} public void setCreatedByAgentId(String createdByAgentId){this.createdByAgentId=createdByAgentId;}
 public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime createdAt){this.createdAt=createdAt;}
 public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String correlationId){this.correlationId=correlationId;}
}
