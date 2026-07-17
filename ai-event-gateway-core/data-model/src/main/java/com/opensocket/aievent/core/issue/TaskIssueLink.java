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
    public static final String SYNC_PENDING = "SYNC_PENDING";
    public static final String SYNCED = "SYNCED";
    public static final String SYNC_FAILED = "SYNC_FAILED";
    public static final String NOT_LINKED = "NOT_LINKED";

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
        link.setIssueStatus(firstNonBlank(issueStatus, syncStatus));
        link.setSyncStatus(firstNonBlank(syncStatus, error == null || error.isBlank() ? SYNCED : SYNC_FAILED));
        link.setIssueRetryable(retryable);
        link.setSyncError(error);
        link.setLastSyncedAt(SYNCED.equals(link.getSyncStatus()) ? observedAt : null);
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
        link.setTaskId(action == null ? null : action.getTaskId());
        link.setIncidentId(action == null ? null : action.getIncidentId());
        link.setDispatchRequestId(action == null ? null : action.getDispatchRequestId());
        link.setAssignmentId(action == null ? null : action.getAssignmentId());
        link.setAgentId(action == null ? null : action.getAgentId());
        link.setIssueVendor(normalizeVendor(firstNonBlank(text(payload.get("issueVendor")), text(payload.get("vendor")), text(payload.get("provider")))));
        link.setIssueId(firstNonBlank(text(payload.get("linkedIssueId")), text(payload.get("issueId")), text(payload.get("externalIssueId")), text(payload.get("iid")), text(payload.get("key"))));
        link.setIssueUrl(firstNonBlank(text(payload.get("issueUrl")), text(payload.get("webUrl")), text(payload.get("url"))));
        link.setIssueActionId(action == null ? null : action.getActionId());
        link.setIssueActionType(action == null || action.getActionType() == null ? null : action.getActionType().name());
        link.setIssueActionStatus(action == null || action.getStatus() == null ? null : action.getStatus().name());
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

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

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
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
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
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getLastAdapterActionAt() { return lastAdapterActionAt; }
    public void setLastAdapterActionAt(OffsetDateTime lastAdapterActionAt) { this.lastAdapterActionAt = lastAdapterActionAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
