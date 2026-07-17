package com.opensocket.aievent.database.persistence.issue.po;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TaskIssueLinkPo {
    private String taskId;
    private String incidentId;
    private String dispatchRequestId;
    private String assignmentId;
    private String agentId;
    private String issueVendor;
    private String issueId;
    private String issueUrl;
    private String issueStatus;
    private String syncStatus;
    private String issueActionId;
    private String issueActionType;
    private String issueActionStatus;
    private boolean issueRetryable;
    private String issueCommentMode;
    private String agentSummary;
    private String issueCommentPreview;
    private OffsetDateTime lastSyncedAt;
    private String syncError;
    private String message;
    private OffsetDateTime lastAdapterActionAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
