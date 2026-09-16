package com.opensocket.aievent.database.persistence.issue.converter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.database.persistence.issue.po.TaskIssueLinkPo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "task-issue-links", name = "store", havingValue = "MYBATIS")
public class TaskIssueLinkPersistenceConverter {
    public TaskIssueLinkPo toPo(TaskIssueLink v) {
        TaskIssueLinkPo p = new TaskIssueLinkPo();
        p.setTenantId(v.getTenantId()); p.setLinkId(v.getLinkId()); p.setConnectionId(v.getConnectionId()); p.setProjectMappingId(v.getProjectMappingId());
        p.setProviderType(v.getProviderType()); p.setExternalProjectId(v.getExternalProjectId()); p.setExternalProjectKey(v.getExternalProjectKey());
        p.setExternalIssueId(v.getExternalIssueId()); p.setExternalIssueKey(v.getExternalIssueKey()); p.setExternalIssueUrl(v.getExternalIssueUrl());
        p.setLinkRole(v.getLinkRole()); p.setLinkState(v.getLinkState()); p.setProjectionStrategy(v.getProjectionStrategy()); p.setPayloadHash(v.getPayloadHash()); p.setIdempotencyKey(v.getIdempotencyKey());
        p.setResourceVersion(v.getResourceVersion()); p.setLastProviderEventId(v.getLastProviderEventId()); p.setConflictStatus(v.getConflictStatus());
        p.setOwnerDepartmentId(v.getOwnerDepartmentId()); p.setOwnerGroupId(v.getOwnerGroupId()); p.setRequesterDepartmentId(v.getRequesterDepartmentId()); p.setRequesterGroupId(v.getRequesterGroupId()); p.setExecutorDepartmentId(v.getExecutorDepartmentId()); p.setExecutorGroupId(v.getExecutorGroupId()); p.setScopeStatus(v.getScopeStatus()); p.setScopeSourceType(v.getScopeSourceType()); p.setScopeSourceId(v.getScopeSourceId()); p.setScopeSourceVersion(v.getScopeSourceVersion()); p.setScopeInheritedAt(v.getScopeInheritedAt());
        p.setTaskId(v.getTaskId()); p.setIncidentId(v.getIncidentId()); p.setDispatchRequestId(v.getDispatchRequestId()); p.setAssignmentId(v.getAssignmentId()); p.setAgentId(v.getAgentId());
        p.setIssueVendor(v.getIssueVendor()); p.setIssueId(v.getIssueId()); p.setIssueUrl(v.getIssueUrl()); p.setIssueStatus(v.getIssueStatus()); p.setSyncStatus(v.getSyncStatus());
        p.setIssueActionId(v.getIssueActionId()); p.setIssueActionType(v.getIssueActionType()); p.setIssueActionStatus(v.getIssueActionStatus()); p.setIssueRetryable(v.isIssueRetryable());
        p.setIssueCommentMode(v.getIssueCommentMode()); p.setAgentSummary(v.getAgentSummary()); p.setIssueCommentPreview(v.getIssueCommentPreview()); p.setLastSyncedAt(v.getLastSyncedAt());
        p.setSyncError(v.getSyncError()); p.setProviderFailureCode(v.getProviderFailureCode()); p.setProviderStatusCode(v.getProviderStatusCode()); p.setProviderHealthImpact(v.getProviderHealthImpact()); p.setProviderOutcomeCertainty(v.getProviderOutcomeCertainty()); p.setOperationFingerprint(v.getOperationFingerprint()); p.setCorrelationId(v.getCorrelationId()); p.setA2aRequestId(v.getA2aRequestId()); p.setSourceSystemId(v.getSourceSystemId()); p.setTechnicalPrincipalId(v.getTechnicalPrincipalId()); p.setCredentialId(v.getCredentialId()); p.setCredentialVersion(v.getCredentialVersion()); p.setMessage(v.getMessage()); p.setLastAdapterActionAt(v.getLastAdapterActionAt()); p.setCreatedAt(v.getCreatedAt()); p.setUpdatedAt(v.getUpdatedAt()); return p;
    }
    public TaskIssueLink toDomain(TaskIssueLinkPo p) {
        TaskIssueLink v = new TaskIssueLink();
        v.setTenantId(p.getTenantId()); v.setLinkId(p.getLinkId()); v.setConnectionId(p.getConnectionId()); v.setProjectMappingId(p.getProjectMappingId());
        v.setProviderType(p.getProviderType()); v.setExternalProjectId(p.getExternalProjectId()); v.setExternalProjectKey(p.getExternalProjectKey());
        v.setExternalIssueId(p.getExternalIssueId()); v.setExternalIssueKey(p.getExternalIssueKey()); v.setExternalIssueUrl(p.getExternalIssueUrl());
        v.setLinkRole(p.getLinkRole()); v.setLinkState(p.getLinkState()); v.setProjectionStrategy(p.getProjectionStrategy()); v.setPayloadHash(p.getPayloadHash()); v.setIdempotencyKey(p.getIdempotencyKey());
        v.setResourceVersion(p.getResourceVersion()); v.setLastProviderEventId(p.getLastProviderEventId()); v.setConflictStatus(p.getConflictStatus());
        v.setOwnerDepartmentId(p.getOwnerDepartmentId()); v.setOwnerGroupId(p.getOwnerGroupId()); v.setRequesterDepartmentId(p.getRequesterDepartmentId()); v.setRequesterGroupId(p.getRequesterGroupId()); v.setExecutorDepartmentId(p.getExecutorDepartmentId()); v.setExecutorGroupId(p.getExecutorGroupId()); v.setScopeStatus(p.getScopeStatus()); v.setScopeSourceType(p.getScopeSourceType()); v.setScopeSourceId(p.getScopeSourceId()); v.setScopeSourceVersion(p.getScopeSourceVersion()); v.setScopeInheritedAt(p.getScopeInheritedAt());
        v.setTaskId(p.getTaskId()); v.setIncidentId(p.getIncidentId()); v.setDispatchRequestId(p.getDispatchRequestId()); v.setAssignmentId(p.getAssignmentId()); v.setAgentId(p.getAgentId());
        v.setIssueVendor(p.getIssueVendor()); v.setIssueId(p.getIssueId()); v.setIssueUrl(p.getIssueUrl()); v.setIssueStatus(p.getIssueStatus()); v.setSyncStatus(p.getSyncStatus());
        v.setIssueActionId(p.getIssueActionId()); v.setIssueActionType(p.getIssueActionType()); v.setIssueActionStatus(p.getIssueActionStatus()); v.setIssueRetryable(p.isIssueRetryable());
        v.setIssueCommentMode(p.getIssueCommentMode()); v.setAgentSummary(p.getAgentSummary()); v.setIssueCommentPreview(p.getIssueCommentPreview()); v.setLastSyncedAt(p.getLastSyncedAt());
        v.setSyncError(p.getSyncError()); v.setProviderFailureCode(p.getProviderFailureCode()); v.setProviderStatusCode(p.getProviderStatusCode()); v.setProviderHealthImpact(p.getProviderHealthImpact()); v.setProviderOutcomeCertainty(p.getProviderOutcomeCertainty()); v.setOperationFingerprint(p.getOperationFingerprint()); v.setCorrelationId(p.getCorrelationId()); v.setA2aRequestId(p.getA2aRequestId()); v.setSourceSystemId(p.getSourceSystemId()); v.setTechnicalPrincipalId(p.getTechnicalPrincipalId()); v.setCredentialId(p.getCredentialId()); v.setCredentialVersion(p.getCredentialVersion()); v.setMessage(p.getMessage()); v.setLastAdapterActionAt(p.getLastAdapterActionAt()); v.setCreatedAt(p.getCreatedAt()); v.setUpdatedAt(p.getUpdatedAt()); return v;
    }
}
