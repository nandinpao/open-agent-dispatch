package com.opensocket.aievent.database.persistence.issue.converter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.database.persistence.issue.po.TaskIssueLinkPo;
import com.opensocket.aievent.database.persistence.spi.DatabasePersistenceConverter;

@DatabasePersistenceConverter
@ConditionalOnProperty(prefix = "task-issue-links", name = "store", havingValue = "MYBATIS")
public class TaskIssueLinkPersistenceConverter {
    public TaskIssueLinkPo toPo(TaskIssueLink link) {
        TaskIssueLinkPo po = new TaskIssueLinkPo();
        po.setTaskId(link.getTaskId());
        po.setIncidentId(link.getIncidentId());
        po.setDispatchRequestId(link.getDispatchRequestId());
        po.setAssignmentId(link.getAssignmentId());
        po.setAgentId(link.getAgentId());
        po.setIssueVendor(link.getIssueVendor());
        po.setIssueId(link.getIssueId());
        po.setIssueUrl(link.getIssueUrl());
        po.setIssueStatus(link.getIssueStatus());
        po.setSyncStatus(link.getSyncStatus());
        po.setIssueActionId(link.getIssueActionId());
        po.setIssueActionType(link.getIssueActionType());
        po.setIssueActionStatus(link.getIssueActionStatus());
        po.setIssueRetryable(link.isIssueRetryable());
        po.setIssueCommentMode(link.getIssueCommentMode());
        po.setAgentSummary(link.getAgentSummary());
        po.setIssueCommentPreview(link.getIssueCommentPreview());
        po.setLastSyncedAt(link.getLastSyncedAt());
        po.setSyncError(link.getSyncError());
        po.setMessage(link.getMessage());
        po.setLastAdapterActionAt(link.getLastAdapterActionAt());
        po.setCreatedAt(link.getCreatedAt());
        po.setUpdatedAt(link.getUpdatedAt());
        return po;
    }

    public TaskIssueLink toDomain(TaskIssueLinkPo po) {
        TaskIssueLink link = new TaskIssueLink();
        link.setTaskId(po.getTaskId());
        link.setIncidentId(po.getIncidentId());
        link.setDispatchRequestId(po.getDispatchRequestId());
        link.setAssignmentId(po.getAssignmentId());
        link.setAgentId(po.getAgentId());
        link.setIssueVendor(po.getIssueVendor());
        link.setIssueId(po.getIssueId());
        link.setIssueUrl(po.getIssueUrl());
        link.setIssueStatus(po.getIssueStatus());
        link.setSyncStatus(po.getSyncStatus());
        link.setIssueActionId(po.getIssueActionId());
        link.setIssueActionType(po.getIssueActionType());
        link.setIssueActionStatus(po.getIssueActionStatus());
        link.setIssueRetryable(po.isIssueRetryable());
        link.setIssueCommentMode(po.getIssueCommentMode());
        link.setAgentSummary(po.getAgentSummary());
        link.setIssueCommentPreview(po.getIssueCommentPreview());
        link.setLastSyncedAt(po.getLastSyncedAt());
        link.setSyncError(po.getSyncError());
        link.setMessage(po.getMessage());
        link.setLastAdapterActionAt(po.getLastAdapterActionAt());
        link.setCreatedAt(po.getCreatedAt());
        link.setUpdatedAt(po.getUpdatedAt());
        return link;
    }
}
