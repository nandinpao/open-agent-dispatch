package com.opensocket.aievent.core.issue.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Locale;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterActionType;
import com.opensocket.aievent.core.action.executor.AdapterExecutionOutcome;
import com.opensocket.aievent.core.action.executor.AdapterExecutionResult;

/**
 * Durable, provider-observed evidence for one Issue Tracking AdapterAction attempt.
 *
 * <p>Provider evidence is immutable once inserted. Only the TaskIssueLink projection
 * lifecycle may change afterwards. This lets Core rebuild the read model without ever
 * re-running a provider side effect.</p>
 */
public class IssueProviderExecutionResult {
    private String tenantId;
    private String resultId;
    private String adapterActionId;
    private String taskId;
    private String adapterActionType;
    private int attemptNo;
    private String observationKind = "EXECUTION";
    private String provider;
    private String operation;
    private IssueProviderExecutionDisposition disposition;
    private String providerOutcomeCertainty;
    private Integer providerStatusCode;
    private boolean retryable;
    private String failureCode;
    private String providerHealthImpact;
    private String externalProjectId;
    private String externalIssueId;
    private String externalIssueKey;
    private String externalIssueUrl;
    private String externalStatus;
    private String responseRef;
    private String responseFingerprint;
    private String idempotencyKey;
    private String operationFingerprint;
    private String correlationId;
    private String a2aRequestId;
    private String sourceSystemId;
    private String connectionId;
    private String projectMappingId;
    private String technicalPrincipalId;
    private String credentialId;
    private String credentialVersion;
    private String errorMessage;
    private OffsetDateTime executedAt;
    private IssueLinkProjectionStatus projectionStatus = IssueLinkProjectionStatus.PENDING;
    private int projectionAttemptCount;
    private int projectionMaxAttempts = 10;
    private OffsetDateTime nextProjectionAttemptAt;
    private String lastProjectionError;
    private OffsetDateTime projectedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static IssueProviderExecutionResult observed(AdapterAction action,
                                                        AdapterExecutionResult execution,
                                                        OffsetDateTime observedAt,
                                                        int projectionMaxAttempts) {
        if (action == null) throw new IllegalArgumentException("adapter action is required");
        if (execution == null) throw new IllegalArgumentException("adapter execution result is required");
        OffsetDateTime now = observedAt == null ? OffsetDateTime.now() : observedAt;
        IssueProviderExecutionResult value = new IssueProviderExecutionResult();
        value.setTenantId(execution.getTenantId() == null ? payloadText(action, "tenantId") : execution.getTenantId());
        value.setAdapterActionId(action.getActionId());
        value.setTaskId(action.getTaskId());
        value.setAdapterActionType(action.getActionType() == null ? null : action.getActionType().name());
        value.setOperation(action.getActionType() == null ? null : action.getActionType().name());
        value.setAttemptNo(Math.max(1, action.getAttemptCount()));
        value.setObservationKind("EXECUTION");
        value.setResultId("issue-provider-result-" + action.getActionId() + "-" + value.getAttemptNo());
        value.setProvider(firstNonBlank(execution.getIssueVendor(), payloadText(action, "issueVendor", "vendor", "provider")));
        value.setDisposition(disposition(execution));
        value.setProviderOutcomeCertainty(firstNonBlank(execution.getProviderOutcomeCertainty(), value.getDisposition() == IssueProviderExecutionDisposition.UNKNOWN ? "UNCERTAIN" : "CONFIRMED"));
        value.setProviderStatusCode(execution.getProviderStatusCode());
        value.setRetryable(execution.isRetryable());
        value.setFailureCode(execution.getErrorCode());
        value.setProviderHealthImpact(execution.getProviderHealthImpact());
        value.setExternalProjectId(execution.getExternalProjectId());
        value.setExternalIssueId(execution.getIssueId());
        value.setExternalIssueKey(execution.getIssueId());
        value.setExternalIssueUrl(execution.getIssueUrl());
        value.setExternalStatus(execution.getIssueStatus());
        value.setResponseRef(execution.getResponseRef());
        value.setResponseFingerprint(fingerprint(execution));
        value.setIdempotencyKey(firstNonBlank(execution.getIdempotencyKey(), action.getIdempotencyKey()));
        value.setOperationFingerprint(execution.getOperationFingerprint());
        value.setCorrelationId(execution.getCorrelationId());
        value.setA2aRequestId(execution.getA2aRequestId());
        value.setSourceSystemId(execution.getSourceSystemId());
        value.setConnectionId(execution.getConnectionId());
        value.setProjectMappingId(execution.getProjectMappingId());
        value.setTechnicalPrincipalId(execution.getTechnicalPrincipalId());
        value.setCredentialId(execution.getCredentialId());
        value.setCredentialVersion(execution.getCredentialVersion());
        value.setErrorMessage(execution.getError());
        value.setExecutedAt(now);
        value.setProjectionStatus(IssueLinkProjectionStatus.PENDING);
        value.setProjectionAttemptCount(0);
        value.setProjectionMaxAttempts(Math.max(1, projectionMaxAttempts));
        value.setNextProjectionAttemptAt(now);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }


    public static IssueProviderExecutionResult reconciled(AdapterAction action,
                                                          AdapterExecutionResult execution,
                                                          String observationKind,
                                                          OffsetDateTime observedAt,
                                                          int projectionMaxAttempts) {
        IssueProviderExecutionResult value = observed(action, execution, observedAt, projectionMaxAttempts);
        String kind = observationKind == null || observationKind.isBlank() ? "RECONCILIATION" : observationKind.trim().toUpperCase(Locale.ROOT);
        value.setObservationKind(kind);
        value.setResultId("issue-provider-result-" + action.getActionId() + "-" + value.getAttemptNo() + "-" + kind.toLowerCase(Locale.ROOT).replace('_','-'));
        return value;
    }

    private static IssueProviderExecutionDisposition disposition(AdapterExecutionResult execution) {
        String certainty = execution.getProviderOutcomeCertainty();
        if (execution.getOutcome() == AdapterExecutionOutcome.OUTCOME_UNCERTAIN
                || (certainty != null && "UNCERTAIN".equalsIgnoreCase(certainty))) {
            return IssueProviderExecutionDisposition.UNKNOWN;
        }
        return execution.isSuccess()
                ? IssueProviderExecutionDisposition.CONFIRMED_SUCCESS
                : IssueProviderExecutionDisposition.CONFIRMED_FAILURE;
    }

    private static String fingerprint(AdapterExecutionResult execution) {
        String canonical = String.join("|",
                nullSafe(execution.getExecutorName()),
                execution.getOutcome() == null ? "" : execution.getOutcome().name(),
                nullSafe(execution.getIssueVendor()),
                nullSafe(execution.getIssueId()),
                nullSafe(execution.getIssueUrl()),
                nullSafe(execution.getIssueStatus()),
                execution.getProviderStatusCode() == null ? "" : execution.getProviderStatusCode().toString(),
                nullSafe(execution.getProviderOutcomeCertainty()),
                nullSafe(execution.getErrorCode()),
                nullSafe(execution.getResponseRef()),
                nullSafe(execution.getError()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("ISSUE_PROVIDER_RESULT_FINGERPRINT_FAILED", ex);
        }
    }

    private static String payloadText(AdapterAction action, String... keys) {
        if (action == null || action.getPayload() == null) return null;
        for (String key : keys) {
            Object value = action.getPayload().get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    private static String nullSafe(String value) { return value == null ? "" : value; }

    public boolean isConfirmedSuccess() { return disposition == IssueProviderExecutionDisposition.CONFIRMED_SUCCESS; }
    public boolean isUnknown() { return disposition == IssueProviderExecutionDisposition.UNKNOWN; }
    public boolean isProjectionDue(OffsetDateTime now) {
        return (projectionStatus == IssueLinkProjectionStatus.PENDING || projectionStatus == IssueLinkProjectionStatus.RETRY_WAITING)
                && (nextProjectionAttemptAt == null || !nextProjectionAttemptAt.isAfter(now));
    }
    public boolean isCreateOperation() { return AdapterActionType.ISSUE_CREATE.name().equalsIgnoreCase(operation); }

    public String getTenantId() { return tenantId; } public void setTenantId(String v) { tenantId=v; }
    public String getResultId() { return resultId; } public void setResultId(String v) { resultId=v; }
    public String getAdapterActionId() { return adapterActionId; } public void setAdapterActionId(String v) { adapterActionId=v; }
    public String getTaskId() { return taskId; } public void setTaskId(String v) { taskId=v; }
    public String getAdapterActionType() { return adapterActionType; } public void setAdapterActionType(String v) { adapterActionType=v; }
    public int getAttemptNo() { return attemptNo; } public void setAttemptNo(int v) { attemptNo=Math.max(1,v); }
    public String getObservationKind() { return observationKind; } public void setObservationKind(String v) { observationKind=v==null||v.isBlank()?"EXECUTION":v.trim().toUpperCase(Locale.ROOT); }
    public String getProvider() { return provider; } public void setProvider(String v) { provider=v; }
    public String getOperation() { return operation; } public void setOperation(String v) { operation=v; }
    public IssueProviderExecutionDisposition getDisposition() { return disposition; } public void setDisposition(IssueProviderExecutionDisposition v) { disposition=v; }
    public String getProviderOutcomeCertainty() { return providerOutcomeCertainty; } public void setProviderOutcomeCertainty(String v) { providerOutcomeCertainty=v; }
    public Integer getProviderStatusCode() { return providerStatusCode; } public void setProviderStatusCode(Integer v) { providerStatusCode=v; }
    public boolean isRetryable() { return retryable; } public void setRetryable(boolean v) { retryable=v; }
    public String getFailureCode() { return failureCode; } public void setFailureCode(String v) { failureCode=v; }
    public String getProviderHealthImpact() { return providerHealthImpact; } public void setProviderHealthImpact(String v) { providerHealthImpact=v; }
    public String getExternalProjectId() { return externalProjectId; } public void setExternalProjectId(String v) { externalProjectId=v; }
    public String getExternalIssueId() { return externalIssueId; } public void setExternalIssueId(String v) { externalIssueId=v; }
    public String getExternalIssueKey() { return externalIssueKey; } public void setExternalIssueKey(String v) { externalIssueKey=v; }
    public String getExternalIssueUrl() { return externalIssueUrl; } public void setExternalIssueUrl(String v) { externalIssueUrl=v; }
    public String getExternalStatus() { return externalStatus; } public void setExternalStatus(String v) { externalStatus=v; }
    public String getResponseRef() { return responseRef; } public void setResponseRef(String v) { responseRef=v; }
    public String getResponseFingerprint() { return responseFingerprint; } public void setResponseFingerprint(String v) { responseFingerprint=v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { idempotencyKey=v; }
    public String getOperationFingerprint() { return operationFingerprint; } public void setOperationFingerprint(String v) { operationFingerprint=v; }
    public String getCorrelationId() { return correlationId; } public void setCorrelationId(String v) { correlationId=v; }
    public String getA2aRequestId() { return a2aRequestId; } public void setA2aRequestId(String v) { a2aRequestId=v; }
    public String getSourceSystemId() { return sourceSystemId; } public void setSourceSystemId(String v) { sourceSystemId=v; }
    public String getConnectionId() { return connectionId; } public void setConnectionId(String v) { connectionId=v; }
    public String getProjectMappingId() { return projectMappingId; } public void setProjectMappingId(String v) { projectMappingId=v; }
    public String getTechnicalPrincipalId() { return technicalPrincipalId; } public void setTechnicalPrincipalId(String v) { technicalPrincipalId=v; }
    public String getCredentialId() { return credentialId; } public void setCredentialId(String v) { credentialId=v; }
    public String getCredentialVersion() { return credentialVersion; } public void setCredentialVersion(String v) { credentialVersion=v; }
    public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String v) { errorMessage=v; }
    public OffsetDateTime getExecutedAt() { return executedAt; } public void setExecutedAt(OffsetDateTime v) { executedAt=v; }
    public IssueLinkProjectionStatus getProjectionStatus() { return projectionStatus; } public void setProjectionStatus(IssueLinkProjectionStatus v) { projectionStatus=v==null?IssueLinkProjectionStatus.PENDING:v; }
    public int getProjectionAttemptCount() { return projectionAttemptCount; } public void setProjectionAttemptCount(int v) { projectionAttemptCount=Math.max(0,v); }
    public int getProjectionMaxAttempts() { return projectionMaxAttempts; } public void setProjectionMaxAttempts(int v) { projectionMaxAttempts=Math.max(1,v); }
    public OffsetDateTime getNextProjectionAttemptAt() { return nextProjectionAttemptAt; } public void setNextProjectionAttemptAt(OffsetDateTime v) { nextProjectionAttemptAt=v; }
    public String getLastProjectionError() { return lastProjectionError; } public void setLastProjectionError(String v) { lastProjectionError=v; }
    public OffsetDateTime getProjectedAt() { return projectedAt; } public void setProjectedAt(OffsetDateTime v) { projectedAt=v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt=v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(OffsetDateTime v) { updatedAt=v; }
}
