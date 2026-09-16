package com.opensocket.aievent.core.task.lineage;

import java.time.OffsetDateTime;

/**
 * Immutable investigation evidence. Current authorization must always be evaluated separately.
 */
public class TaskLineageEvidence {
    private String tenantId;
    private String evidenceId;
    private String rootTaskId;
    private String taskId;
    private String parentTaskId;
    private TaskLineageEventType eventType;
    private String originPrincipalType;
    private String originPrincipalId;
    private String actorPrincipalType;
    private String actorPrincipalId;
    private String executorAgentId;
    private String assignmentId;
    private String departmentId;
    private String groupId;
    private String credentialId;
    private String oauthClientId;
    private String sourceSystem;
    private String correlationId;
    private String traceId;
    private TaskFailureDomain failureDomain = TaskFailureDomain.NONE;
    private String failureCode;
    private String reason;
    private OffsetDateTime occurredAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { tenantId = v; }
    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String v) { evidenceId = v; }
    public String getRootTaskId() { return rootTaskId; }
    public void setRootTaskId(String v) { rootTaskId = v; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String v) { taskId = v; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String v) { parentTaskId = v; }
    public TaskLineageEventType getEventType() { return eventType; }
    public void setEventType(TaskLineageEventType v) { eventType = v; }
    public String getOriginPrincipalType() { return originPrincipalType; }
    public void setOriginPrincipalType(String v) { originPrincipalType = v; }
    public String getOriginPrincipalId() { return originPrincipalId; }
    public void setOriginPrincipalId(String v) { originPrincipalId = v; }
    public String getActorPrincipalType() { return actorPrincipalType; }
    public void setActorPrincipalType(String v) { actorPrincipalType = v; }
    public String getActorPrincipalId() { return actorPrincipalId; }
    public void setActorPrincipalId(String v) { actorPrincipalId = v; }
    public String getExecutorAgentId() { return executorAgentId; }
    public void setExecutorAgentId(String v) { executorAgentId = v; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String v) { assignmentId = v; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String v) { departmentId = v; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String v) { groupId = v; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String v) { credentialId = v; }
    public String getOauthClientId() { return oauthClientId; }
    public void setOauthClientId(String v) { oauthClientId = v; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String v) { sourceSystem = v; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String v) { correlationId = v; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String v) { traceId = v; }
    public TaskFailureDomain getFailureDomain() { return failureDomain; }
    public void setFailureDomain(TaskFailureDomain v) { failureDomain = v == null ? TaskFailureDomain.NONE : v; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String v) { failureCode = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime v) { occurredAt = v; }
}
