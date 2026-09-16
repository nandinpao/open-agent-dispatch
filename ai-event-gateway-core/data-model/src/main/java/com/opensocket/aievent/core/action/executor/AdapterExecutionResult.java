package com.opensocket.aievent.core.action.executor;

public class AdapterExecutionResult {
    private AdapterExecutionOutcome outcome = AdapterExecutionOutcome.SUCCESS;
    private String executorName;
    private String responseRef;
    private String error;
    private boolean retryable;
    private String issueVendor;
    private String issueId;
    private String issueUrl;
    private String issueStatus;
    private String errorCode;
    private Integer providerStatusCode;
    private String providerHealthImpact;
    private String providerOutcomeCertainty;
    private String idempotencyKey;
    private String operationFingerprint;
    private String tenantId;
    private String correlationId;
    private String a2aRequestId;
    private String sourceSystemId;
    private String connectionId;
    private String projectMappingId;
    private String externalProjectId;
    private String technicalPrincipalId;
    private String credentialId;
    private String credentialVersion;

    public static AdapterExecutionResult success(String executorName, String responseRef) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.SUCCESS);
        result.setExecutorName(executorName);
        result.setResponseRef(responseRef);
        result.setRetryable(false);
        result.setProviderOutcomeCertainty("CONFIRMED");
        return result;
    }

    public static AdapterExecutionResult failed(String executorName, String error) { return retryableFailure(executorName, error); }

    public static AdapterExecutionResult retryableFailure(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.RETRYABLE_FAILURE);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(true);
        result.setProviderOutcomeCertainty("CONFIRMED");
        return result;
    }

    public static AdapterExecutionResult permanentFailure(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.PERMANENT_FAILURE);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(false);
        result.setProviderOutcomeCertainty("CONFIRMED");
        return result;
    }

    public static AdapterExecutionResult executorUnavailable(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.EXECUTOR_UNAVAILABLE);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(true);
        result.setProviderOutcomeCertainty("CONFIRMED");
        return result;
    }

    public static AdapterExecutionResult timeout(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.TIMEOUT);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(true);
        result.setProviderOutcomeCertainty("CONFIRMED");
        return result;
    }

    public static AdapterExecutionResult outcomeUncertain(String executorName, String error) {
        AdapterExecutionResult result = new AdapterExecutionResult();
        result.setOutcome(AdapterExecutionOutcome.OUTCOME_UNCERTAIN);
        result.setExecutorName(executorName);
        result.setError(error);
        result.setRetryable(false);
        result.setProviderOutcomeCertainty("UNCERTAIN");
        return result;
    }

    public boolean isSuccess() { return outcome == AdapterExecutionOutcome.SUCCESS; }
    public AdapterExecutionOutcome getOutcome() { return outcome; }
    public void setOutcome(AdapterExecutionOutcome outcome) { this.outcome = outcome == null ? AdapterExecutionOutcome.RETRYABLE_FAILURE : outcome; }
    public String getExecutorName() { return executorName; } public void setExecutorName(String executorName) { this.executorName = executorName; }
    public String getResponseRef() { return responseRef; } public void setResponseRef(String responseRef) { this.responseRef = responseRef; }
    public String getError() { return error; } public void setError(String error) { this.error = error; }
    public boolean isRetryable() { return retryable || outcome == AdapterExecutionOutcome.RETRYABLE_FAILURE || outcome == AdapterExecutionOutcome.EXECUTOR_UNAVAILABLE || outcome == AdapterExecutionOutcome.TIMEOUT; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }
    public String getIssueVendor() { return issueVendor; } public void setIssueVendor(String issueVendor) { this.issueVendor = issueVendor; }
    public String getIssueId() { return issueId; } public void setIssueId(String issueId) { this.issueId = issueId; }
    public String getIssueUrl() { return issueUrl; } public void setIssueUrl(String issueUrl) { this.issueUrl = issueUrl; }
    public String getIssueStatus() { return issueStatus; } public void setIssueStatus(String issueStatus) { this.issueStatus = issueStatus; }
    public String getErrorCode() { return errorCode; } public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Integer getProviderStatusCode() { return providerStatusCode; } public void setProviderStatusCode(Integer providerStatusCode) { this.providerStatusCode = providerStatusCode; }
    public String getProviderHealthImpact() { return providerHealthImpact; } public void setProviderHealthImpact(String providerHealthImpact) { this.providerHealthImpact = providerHealthImpact; }
    public String getProviderOutcomeCertainty() { return providerOutcomeCertainty; } public void setProviderOutcomeCertainty(String value) { this.providerOutcomeCertainty = value; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getOperationFingerprint() { return operationFingerprint; } public void setOperationFingerprint(String value) { this.operationFingerprint = value; }
    public String getTenantId() { return tenantId; } public void setTenantId(String value) { this.tenantId = value; }
    public String getCorrelationId() { return correlationId; } public void setCorrelationId(String value) { this.correlationId = value; }
    public String getA2aRequestId() { return a2aRequestId; } public void setA2aRequestId(String value) { this.a2aRequestId = value; }
    public String getSourceSystemId() { return sourceSystemId; } public void setSourceSystemId(String value) { this.sourceSystemId = value; }
    public String getConnectionId() { return connectionId; } public void setConnectionId(String value) { this.connectionId = value; }
    public String getProjectMappingId() { return projectMappingId; } public void setProjectMappingId(String value) { this.projectMappingId = value; }
    public String getExternalProjectId() { return externalProjectId; } public void setExternalProjectId(String value) { this.externalProjectId = value; }
    public String getTechnicalPrincipalId() { return technicalPrincipalId; } public void setTechnicalPrincipalId(String value) { this.technicalPrincipalId = value; }
    public String getCredentialId() { return credentialId; } public void setCredentialId(String value) { this.credentialId = value; }
    public String getCredentialVersion() { return credentialVersion; } public void setCredentialVersion(String value) { this.credentialVersion = value; }
}
