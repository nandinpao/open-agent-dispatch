package com.opensocket.aievent.database.persistence.issueprojection.po;
import java.time.OffsetDateTime;
public class IssueRelayAttemptPo {
 private String tenantId;
 private String attemptId;
 private String relayId;
 private String operationCode;
 private String operationSide;
 private String providerType;
 private String projectMappingId;
 private String principalId;
 private int attemptNo;
 private String status;
 private Integer providerStatus;
 private String externalIssueId;
 private String externalIssueUrl;
 private String responseSummary;
 private String errorCode;
 private boolean retryable;
 private OffsetDateTime startedAt;
 private OffsetDateTime completedAt;
 private String correlationId;
 public String getTenantId(){return tenantId;} public void setTenantId(String tenantId){this.tenantId=tenantId;}
 public String getAttemptId(){return attemptId;} public void setAttemptId(String attemptId){this.attemptId=attemptId;}
 public String getRelayId(){return relayId;} public void setRelayId(String relayId){this.relayId=relayId;}
 public String getOperationCode(){return operationCode;} public void setOperationCode(String operationCode){this.operationCode=operationCode;}
 public String getOperationSide(){return operationSide;} public void setOperationSide(String operationSide){this.operationSide=operationSide;}
 public String getProviderType(){return providerType;} public void setProviderType(String providerType){this.providerType=providerType;}
 public String getProjectMappingId(){return projectMappingId;} public void setProjectMappingId(String projectMappingId){this.projectMappingId=projectMappingId;}
 public String getPrincipalId(){return principalId;} public void setPrincipalId(String principalId){this.principalId=principalId;}
 public int getAttemptNo(){return attemptNo;} public void setAttemptNo(int attemptNo){this.attemptNo=attemptNo;}
 public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
 public Integer getProviderStatus(){return providerStatus;} public void setProviderStatus(Integer providerStatus){this.providerStatus=providerStatus;}
 public String getExternalIssueId(){return externalIssueId;} public void setExternalIssueId(String externalIssueId){this.externalIssueId=externalIssueId;}
 public String getExternalIssueUrl(){return externalIssueUrl;} public void setExternalIssueUrl(String externalIssueUrl){this.externalIssueUrl=externalIssueUrl;}
 public String getResponseSummary(){return responseSummary;} public void setResponseSummary(String responseSummary){this.responseSummary=responseSummary;}
 public String getErrorCode(){return errorCode;} public void setErrorCode(String errorCode){this.errorCode=errorCode;}
 public boolean isRetryable(){return retryable;} public void setRetryable(boolean retryable){this.retryable=retryable;}
 public OffsetDateTime getStartedAt(){return startedAt;} public void setStartedAt(OffsetDateTime startedAt){this.startedAt=startedAt;}
 public OffsetDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(OffsetDateTime completedAt){this.completedAt=completedAt;}
 public String getCorrelationId(){return correlationId;} public void setCorrelationId(String correlationId){this.correlationId=correlationId;}
}
