package com.opensocket.aievent.core.issue;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.opensocket.aievent.core.action.AdapterAction;

/**
 * Authoritative task-to-external-issue read model.
 *
 * <p>This model intentionally belongs to Core, not Admin UI. Admin screens should consume this
 * object from the Core task runtime-view instead of scanning recent adapter actions and guessing
 * which issue belongs to a task.</p>
 */
public class TaskIssueLink {
    public static final String SYNC_PENDING = "PENDING";
    public static final String SYNC_IN_PROGRESS = "IN_PROGRESS";
    public static final String SYNCED = "SYNCED";
    public static final String SYNC_FAILED_RETRYABLE = "FAILED_RETRYABLE";
    public static final String SYNC_FAILED_PERMANENT = "FAILED_PERMANENT";
    /** @deprecated use SYNC_FAILED_RETRYABLE or SYNC_FAILED_PERMANENT explicitly. */
    @Deprecated
    public static final String SYNC_FAILED = SYNC_FAILED_RETRYABLE;
    public static final String UNLINKED = "UNLINKED";
    public static final String LINK_INTERNAL = "INTERNAL_LINK";
    public static final String LINK_EXTERNAL_CONFIRMED = "EXTERNAL_CONFIRMED";

    private String tenantId;
    private String linkId;
    private String connectionId;
    private String projectMappingId;
    private String providerType;
    private String externalProjectId;
    private String externalProjectKey;
    private String externalIssueId;
    private String externalIssueKey;
    private String externalIssueUrl;
    private String linkRole = "PRIMARY";
    private String projectionStrategy = "CREATE_NEW_ISSUE";
    private String linkState = LINK_INTERNAL;
    private String payloadHash;
    private String idempotencyKey;
    private long resourceVersion = 1L;
    private String lastProviderEventId;
    private String conflictStatus;
    /** RS5 immutable primary Issue scope snapshot inherited from the canonical Task origin scope. */
    private String ownerDepartmentId;
    private String ownerGroupId;
    private String requesterDepartmentId;
    private String requesterGroupId;
    private String executorDepartmentId;
    private String executorGroupId;
    private String scopeStatus = "UNRESOLVED";
    private String scopeSourceType = "TASK";
    private String scopeSourceId;
    private Long scopeSourceVersion;
    private OffsetDateTime scopeInheritedAt;
    private String taskId;
    private String incidentId;
    private String dispatchRequestId;
    private String assignmentId;
    private String agentId;
    private String issueVendor;
    private String issueId;
    private String issueUrl;
    private String issueStatus;
    private String syncStatus = SYNC_PENDING;
    private String issueActionId;
    private String issueActionType;
    private String issueActionStatus;
    private boolean issueRetryable;
    private String issueCommentMode = "APPEND";
    private String agentSummary;
    private String issueCommentPreview;
    private OffsetDateTime lastSyncedAt;
    private String syncError;
    private String providerFailureCode;
    private Integer providerStatusCode;
    private String providerHealthImpact;
    private String providerOutcomeCertainty;
    private String operationFingerprint;
    private String correlationId;
    private String a2aRequestId;
    private String sourceSystemId;
    private String technicalPrincipalId;
    private String credentialId;
    private String credentialVersion;
    private String message;
    private OffsetDateTime lastAdapterActionAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static TaskIssueLink pendingFrom(AdapterAction action, OffsetDateTime observedAt) {
        TaskIssueLink link = baseFrom(action, observedAt);
        link.setSyncStatus(SYNC_PENDING);
        link.setIssueRetryable(false);
        link.setMessage("Issue Tracking action is waiting for adapter execution.");
        return link;
    }

    public static TaskIssueLink inProgressFrom(AdapterAction action, OffsetDateTime observedAt) {
        TaskIssueLink link = baseFrom(action, observedAt);
        link.setSyncStatus(SYNC_IN_PROGRESS);
        link.setIssueRetryable(false);
        link.setSyncError(null);
        link.setMessage("Issue Tracking executor is running.");
        return link;
    }

    public static TaskIssueLink terminalFrom(AdapterAction action,
                                             String issueVendor,
                                             String issueId,
                                             String issueUrl,
                                             String issueStatus,
                                             String syncStatus,
                                             boolean retryable,
                                             String error,
                                             OffsetDateTime observedAt) {
        TaskIssueLink link = baseFrom(action, observedAt);
        Map<String, Object> payload = actionPayload(action);
        link.setIssueVendor(normalizeVendor(firstNonBlank(issueVendor, text(payload.get("issueVendor")), text(payload.get("vendor")))));
        link.setIssueId(firstNonBlank(issueId, text(payload.get("linkedIssueId")), text(payload.get("issueId")), text(payload.get("externalIssueId"))));
        link.setIssueUrl(firstNonBlank(issueUrl, text(payload.get("issueUrl")), text(payload.get("webUrl")), text(payload.get("url"))));
        link.setProviderType(link.getIssueVendor());
        link.setExternalIssueId(link.getIssueId());
        link.setExternalIssueKey(firstNonBlank(text(payload.get("issueKey")), link.getIssueId()));
        link.setExternalIssueUrl(link.getIssueUrl());
        link.setIssueStatus(firstNonBlank(issueStatus, syncStatus));
        link.setSyncStatus(firstNonBlank(syncStatus, error == null || error.isBlank() ? SYNCED : SYNC_FAILED));
        link.setIssueRetryable(retryable);
        link.setSyncError(error);
        link.setLastSyncedAt(SYNCED.equals(link.getSyncStatus()) ? observedAt : null);
        link.setLinkState(SYNCED.equals(link.getSyncStatus()) && link.getExternalIssueId()!=null && !link.getExternalIssueId().isBlank()
                ? LINK_EXTERNAL_CONFIRMED : LINK_INTERNAL);
        if (SYNCED.equals(link.getSyncStatus())) {
            link.setMessage("Issue Tracking sync completed. Agent result history is written to the external issue comment stream.");
        } else if (SYNC_PENDING.equals(link.getSyncStatus())) {
            link.setMessage("Issue Tracking action is waiting for adapter execution.");
        } else {
            link.setMessage("Issue Tracking sync failed: " + firstNonBlank(error, "unknown error"));
        }
        return link;
    }

    private static TaskIssueLink baseFrom(AdapterAction action, OffsetDateTime observedAt) {
        TaskIssueLink link = new TaskIssueLink();
        Map<String, Object> payload = actionPayload(action);
        link.setTenantId(text(payload.get("tenantId")));
        link.setTaskId(action == null ? null : action.getTaskId());
        link.setLinkId(firstNonBlank(text(payload.get("taskIssueLinkId")),
                action == null || action.getActionId() == null ? null : "task-issue-link-" + action.getActionId()));
        link.setIncidentId(action == null ? null : action.getIncidentId());
        link.setDispatchRequestId(action == null ? null : action.getDispatchRequestId());
        link.setAssignmentId(action == null ? null : action.getAssignmentId());
        link.setAgentId(action == null ? null : action.getAgentId());
        link.setIssueVendor(normalizeVendor(firstNonBlank(text(payload.get("issueVendor")), text(payload.get("vendor")), text(payload.get("provider")))));
        link.setProviderType(link.getIssueVendor());
        link.setConnectionId(text(payload.get("connectionId")));
        link.setProjectMappingId(text(payload.get("projectMappingId")));
        link.setExternalProjectId(firstNonBlank(text(payload.get("externalProjectId")), text(payload.get("projectId"))));
        link.setExternalProjectKey(firstNonBlank(text(payload.get("externalProjectKey")), text(payload.get("projectKey"))));
        link.setIssueId(firstNonBlank(text(payload.get("linkedIssueId")), text(payload.get("issueId")), text(payload.get("externalIssueId")), text(payload.get("iid")), text(payload.get("key"))));
        link.setIssueUrl(firstNonBlank(text(payload.get("issueUrl")), text(payload.get("webUrl")), text(payload.get("url"))));
        link.setExternalIssueId(link.getIssueId());
        link.setExternalIssueKey(firstNonBlank(text(payload.get("issueKey")), link.getIssueId()));
        link.setExternalIssueUrl(link.getIssueUrl());
        link.setLinkState(link.getExternalIssueId()!=null && !link.getExternalIssueId().isBlank() ? LINK_EXTERNAL_CONFIRMED : LINK_INTERNAL);
        link.setLinkRole(firstNonBlank(text(payload.get("linkRole")), "PRIMARY"));
        link.setProjectionStrategy(firstNonBlank(text(payload.get("projectionStrategy")), "CREATE_NEW_ISSUE"));
        link.setPayloadHash(firstNonBlank(action == null ? null : action.getRequestHash(), text(payload.get("payloadHash"))));
        link.setIdempotencyKey(action == null ? null : action.getIdempotencyKey());
        link.setIssueActionId(action == null ? null : action.getActionId());
        link.setIssueActionType(action == null || action.getActionType() == null ? null : action.getActionType().name());
        link.setIssueActionStatus(action == null || action.getStatus() == null ? null : action.getStatus().name());
        link.setOwnerDepartmentId(text(payload.get("ownerDepartmentId")));
        link.setOwnerGroupId(text(payload.get("ownerGroupId")));
        link.setRequesterDepartmentId(firstNonBlank(text(payload.get("requesterDepartmentId")), link.getOwnerDepartmentId()));
        link.setRequesterGroupId(firstNonBlank(text(payload.get("requesterGroupId")), link.getOwnerGroupId()));
        link.setExecutorDepartmentId(text(payload.get("executorDepartmentId")));
        link.setExecutorGroupId(text(payload.get("executorGroupId")));
        link.setScopeStatus(firstNonBlank(text(payload.get("originScopeStatus")),
                (link.getOwnerDepartmentId()!=null||link.getOwnerGroupId()!=null)?"RESOLVED":"UNRESOLVED"));
        link.setScopeSourceType("TASK");
        link.setScopeSourceId(link.getTaskId());
        String scopeVersion=text(payload.get("originScopeSourceVersion"));
        if(scopeVersion!=null) try{link.setScopeSourceVersion(Long.valueOf(scopeVersion));}catch(NumberFormatException ignored){}
        link.setScopeInheritedAt(observedAt);
        link.setIssueCommentMode(firstNonBlank(text(payload.get("issueCommentMode")), "APPEND"));
        link.setAgentSummary(firstNonBlank(text(payload.get("agentSummary")), text(payload.get("summary")), text(payload.get("resultSummary")), text(payload.get("callbackMessage"))));
        link.setIssueCommentPreview(truncate(text(payload.get("issueComment")), 220));
        link.setLastAdapterActionAt(firstNonNull(
                action == null ? null : action.getUpdatedAt(),
                action == null ? null : action.getCompletedAt(),
                action == null ? null : action.getCreatedAt(),
                observedAt));
        link.setCreatedAt(action == null ? observedAt : firstNonNull(action.getCreatedAt(), observedAt));
        link.setUpdatedAt(observedAt);
        return link;
    }

    private static Map<String, Object> actionPayload(AdapterAction action) {
        if (action == null || action.getPayload() == null) return Map.of();
        return new LinkedHashMap<>(action.getPayload());
    }

    private static String normalizeVendor(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[-_](COMMENT|NOTE|ISSUE|RESPONSE)$", "");
        if (normalized.contains("REDMINE")) return "REDMINE";
        if (normalized.contains("GITLAB")) return "GITLAB";
        if (normalized.contains("JIRA")) return "JIRA";
        if (normalized.contains("MOCK")) return "MOCK";
        return normalized;
    }

    private static String text(Object value) {
        if (value == null) return null;
        if (value instanceof String s && !s.isBlank()) return s.trim();
        if (value instanceof Number n) return n.toString();
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) return null;
        for (T value : values) if (value != null) return value;
        return null;
    }

    private static String normalizeSyncStatus(String value) {
        if (value == null || value.isBlank()) return SYNC_PENDING;
        return switch (value.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "SYNC_PENDING" -> "PENDING";
            case "SYNC_FAILED" -> "FAILED_RETRYABLE";
            case "NOT_LINKED" -> "PENDING";
            default -> value.trim().toUpperCase(java.util.Locale.ROOT);
        };
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static String normalizeScopeId(String value) {
        if (value == null || value.isBlank() || "UNASSIGNED".equalsIgnoreCase(value.trim())) return null;
        return value.trim();
    }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getLinkId() { return linkId; }
    public void setLinkId(String linkId) { this.linkId = linkId; }
    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
    public String getProjectMappingId() { return projectMappingId; }
    public void setProjectMappingId(String projectMappingId) { this.projectMappingId = projectMappingId; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getExternalProjectId() { return externalProjectId; }
    public void setExternalProjectId(String externalProjectId) { this.externalProjectId = externalProjectId; }
    public String getExternalProjectKey() { return externalProjectKey; }
    public void setExternalProjectKey(String externalProjectKey) { this.externalProjectKey = externalProjectKey; }
    public String getExternalIssueId() { return externalIssueId; }
    public void setExternalIssueId(String externalIssueId) { this.externalIssueId = externalIssueId; }
    public String getExternalIssueKey() { return externalIssueKey; }
    public void setExternalIssueKey(String externalIssueKey) { this.externalIssueKey = externalIssueKey; }
    public String getExternalIssueUrl() { return externalIssueUrl; }
    public void setExternalIssueUrl(String externalIssueUrl) { this.externalIssueUrl = externalIssueUrl; }
    public String getLinkRole() { return linkRole; }
    public void setLinkRole(String linkRole) { this.linkRole = linkRole; }
    public String getProjectionStrategy() { return projectionStrategy; }
    public void setProjectionStrategy(String projectionStrategy) { this.projectionStrategy = projectionStrategy; }

    public String getLinkState() { return linkState; }
    public void setLinkState(String value) {
        if (value == null || value.isBlank()) { linkState = LINK_INTERNAL; return; }
        String normalized=value.trim().toUpperCase(java.util.Locale.ROOT);
        linkState = LINK_EXTERNAL_CONFIRMED.equals(normalized) ? LINK_EXTERNAL_CONFIRMED : LINK_INTERNAL;
    }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public long getResourceVersion() { return resourceVersion; }
    public void setResourceVersion(long resourceVersion) { this.resourceVersion = Math.max(1L, resourceVersion); }
    public String getLastProviderEventId() { return lastProviderEventId; }
    public void setLastProviderEventId(String lastProviderEventId) { this.lastProviderEventId = lastProviderEventId; }
    public String getConflictStatus() { return conflictStatus; }
    public void setConflictStatus(String conflictStatus) { this.conflictStatus = conflictStatus; }

    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String value) { ownerDepartmentId = normalizeScopeId(value); }
    public String getOwnerGroupId() { return ownerGroupId; }
    public void setOwnerGroupId(String value) { ownerGroupId = normalizeScopeId(value); }
    public String getRequesterDepartmentId() { return requesterDepartmentId; }
    public void setRequesterDepartmentId(String value) { requesterDepartmentId = normalizeScopeId(value); }
    public String getRequesterGroupId() { return requesterGroupId; }
    public void setRequesterGroupId(String value) { requesterGroupId = normalizeScopeId(value); }
    public String getExecutorDepartmentId() { return executorDepartmentId; }
    public void setExecutorDepartmentId(String value) { executorDepartmentId = normalizeScopeId(value); }
    public String getExecutorGroupId() { return executorGroupId; }
    public void setExecutorGroupId(String value) { executorGroupId = normalizeScopeId(value); }
    public String getScopeStatus() { return scopeStatus; }
    public void setScopeStatus(String value) { scopeStatus = value==null||value.isBlank()?"UNRESOLVED":value.trim().toUpperCase(java.util.Locale.ROOT); }
    public String getScopeSourceType() { return scopeSourceType; }
    public void setScopeSourceType(String value) { scopeSourceType = value==null||value.isBlank()?"TASK":value.trim().toUpperCase(java.util.Locale.ROOT); }
    public String getScopeSourceId() { return scopeSourceId; }
    public void setScopeSourceId(String value) { scopeSourceId = value==null||value.isBlank()?null:value.trim(); }
    public Long getScopeSourceVersion() { return scopeSourceVersion; }
    public void setScopeSourceVersion(Long value) { scopeSourceVersion = value; }
    public OffsetDateTime getScopeInheritedAt() { return scopeInheritedAt; }
    public void setScopeInheritedAt(OffsetDateTime value) { scopeInheritedAt = value; }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public String getDispatchRequestId() { return dispatchRequestId; }
    public void setDispatchRequestId(String dispatchRequestId) { this.dispatchRequestId = dispatchRequestId; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getIssueVendor() { return issueVendor; }
    public void setIssueVendor(String issueVendor) { this.issueVendor = issueVendor; }
    public String getIssueId() { return issueId; }
    public void setIssueId(String issueId) { this.issueId = issueId; }
    public String getIssueUrl() { return issueUrl; }
    public void setIssueUrl(String issueUrl) { this.issueUrl = issueUrl; }
    public String getIssueStatus() { return issueStatus; }
    public void setIssueStatus(String issueStatus) { this.issueStatus = issueStatus; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = normalizeSyncStatus(syncStatus); }
    public String getIssueActionId() { return issueActionId; }
    public void setIssueActionId(String issueActionId) { this.issueActionId = issueActionId; }
    public String getIssueActionType() { return issueActionType; }
    public void setIssueActionType(String issueActionType) { this.issueActionType = issueActionType; }
    public String getIssueActionStatus() { return issueActionStatus; }
    public void setIssueActionStatus(String issueActionStatus) { this.issueActionStatus = issueActionStatus; }
    public boolean isIssueRetryable() { return issueRetryable; }
    public void setIssueRetryable(boolean issueRetryable) { this.issueRetryable = issueRetryable; }
    public String getIssueCommentMode() { return issueCommentMode; }
    public void setIssueCommentMode(String issueCommentMode) { this.issueCommentMode = issueCommentMode; }
    public String getAgentSummary() { return agentSummary; }
    public void setAgentSummary(String agentSummary) { this.agentSummary = agentSummary; }
    public String getIssueCommentPreview() { return issueCommentPreview; }
    public void setIssueCommentPreview(String issueCommentPreview) { this.issueCommentPreview = issueCommentPreview; }
    public OffsetDateTime getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(OffsetDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public String getSyncError() { return syncError; }
    public void setSyncError(String syncError) { this.syncError = syncError; }
    public String getProviderFailureCode() { return providerFailureCode; }
    public void setProviderFailureCode(String providerFailureCode) { this.providerFailureCode = providerFailureCode; }
    public Integer getProviderStatusCode() { return providerStatusCode; }
    public void setProviderStatusCode(Integer providerStatusCode) { this.providerStatusCode = providerStatusCode; }
    public String getProviderHealthImpact() { return providerHealthImpact; }
    public void setProviderHealthImpact(String providerHealthImpact) { this.providerHealthImpact = providerHealthImpact; }
    public String getProviderOutcomeCertainty() { return providerOutcomeCertainty; }
    public void setProviderOutcomeCertainty(String providerOutcomeCertainty) { this.providerOutcomeCertainty = providerOutcomeCertainty; }
    public String getOperationFingerprint() { return operationFingerprint; }
    public void setOperationFingerprint(String operationFingerprint) { this.operationFingerprint = operationFingerprint; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getA2aRequestId() { return a2aRequestId; }
    public void setA2aRequestId(String a2aRequestId) { this.a2aRequestId = a2aRequestId; }
    public String getSourceSystemId() { return sourceSystemId; }
    public void setSourceSystemId(String sourceSystemId) { this.sourceSystemId = sourceSystemId; }
    public String getTechnicalPrincipalId() { return technicalPrincipalId; }
    public void setTechnicalPrincipalId(String technicalPrincipalId) { this.technicalPrincipalId = technicalPrincipalId; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getCredentialVersion() { return credentialVersion; }
    public void setCredentialVersion(String credentialVersion) { this.credentialVersion = credentialVersion; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getLastAdapterActionAt() { return lastAdapterActionAt; }
    public void setLastAdapterActionAt(OffsetDateTime lastAdapterActionAt) { this.lastAdapterActionAt = lastAdapterActionAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
