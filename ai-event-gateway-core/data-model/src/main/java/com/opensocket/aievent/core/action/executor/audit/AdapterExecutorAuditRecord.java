package com.opensocket.aievent.core.action.executor.audit;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdapterExecutorAuditRecord {
    private String auditId;
    private String actionId;
    private String taskId;
    private String incidentId;
    private String tenantId;
    private String correlationId;
    private String a2aRequestId;
    private String dispatchRequestId;
    private String assignmentId;
    private String agentId;
    private String sourceSystemId;
    private String connectionId;
    private String projectMappingId;
    private String externalProjectId;
    private String externalIssueId;
    private Integer providerStatusCode;
    private String providerFailureCode;
    private String providerHealthImpact;
    private String providerOutcomeCertainty;
    private String idempotencyKey;
    private String operationFingerprint;
    private String technicalPrincipalId;
    private String credentialId;
    private String credentialVersion;
    private String adapterType;
    private String actionType;
    private String executorName;
    private String beforeStatus;
    private String afterStatus;
    private String outcome;
    private String message;
    private int attemptCount;
    private OffsetDateTime createdAt;
    private Map<String, Object> payloadSnapshot = new LinkedHashMap<>();

    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getA2aRequestId() { return a2aRequestId; }
    public void setA2aRequestId(String a2aRequestId) { this.a2aRequestId = a2aRequestId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSourceSystemId() { return sourceSystemId; }
    public void setSourceSystemId(String sourceSystemId) { this.sourceSystemId = sourceSystemId; }
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getProjectMappingId() { return projectMappingId; }
    public void setProjectMappingId(String projectMappingId) { this.projectMappingId = projectMappingId; }
    public String getExternalProjectId() { return externalProjectId; }
    public void setExternalProjectId(String externalProjectId) { this.externalProjectId = externalProjectId; }
    public String getExternalIssueId() { return externalIssueId; }
    public void setExternalIssueId(String externalIssueId) { this.externalIssueId = externalIssueId; }
    public Integer getProviderStatusCode() { return providerStatusCode; }
    public void setProviderStatusCode(Integer providerStatusCode) { this.providerStatusCode = providerStatusCode; }
    public String getProviderFailureCode() { return providerFailureCode; }
    public void setProviderFailureCode(String providerFailureCode) { this.providerFailureCode = providerFailureCode; }
    public String getProviderHealthImpact() { return providerHealthImpact; }
    public void setProviderHealthImpact(String providerHealthImpact) { this.providerHealthImpact = providerHealthImpact; }
    public String getProviderOutcomeCertainty() { return providerOutcomeCertainty; }
    public void setProviderOutcomeCertainty(String providerOutcomeCertainty) { this.providerOutcomeCertainty = providerOutcomeCertainty; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getOperationFingerprint() { return operationFingerprint; }
    public void setOperationFingerprint(String operationFingerprint) { this.operationFingerprint = operationFingerprint; }
    public String getTechnicalPrincipalId() { return technicalPrincipalId; }
    public void setTechnicalPrincipalId(String technicalPrincipalId) { this.technicalPrincipalId = technicalPrincipalId; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(String credentialVersion) { this.credentialVersion = credentialVersion; }
    public String getAdapterType() { return adapterType; }
    public void setAdapterType(String adapterType) { this.adapterType = adapterType; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getExecutorName() { return executorName; }
    public void setExecutorName(String executorName) { this.executorName = executorName; }
    public String getBeforeStatus() { return beforeStatus; }
    public void setBeforeStatus(String beforeStatus) { this.beforeStatus = beforeStatus; }
    public String getAfterStatus() { return afterStatus; }
    public void setAfterStatus(String afterStatus) { this.afterStatus = afterStatus; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Map<String, Object> getPayloadSnapshot() { return payloadSnapshot; }
    public void setPayloadSnapshot(Map<String, Object> payloadSnapshot) { this.payloadSnapshot = payloadSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payloadSnapshot); }
}
