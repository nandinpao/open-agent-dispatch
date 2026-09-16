package com.opensocket.aievent.database.persistence.handoff.po;

import java.time.OffsetDateTime;

public class HandoffContextSnapshotPo {
    private String tenantId;
    private String snapshotId;
    private String aggregateId;
    private int schemaVersion;
    private String rootTaskId;
    private String sourceTaskId;
    private String targetTaskId;
    private String sourceAgentId;
    private String targetAgentId;
    private String targetDomainId;
    private String targetBindingHash;
    private String contextPolicyId;
    private long policyVersion;
    private int snapshotVersion;
    private String summary;
    private String structuredContextJson;
    private String allowedCommentRefsJson;
    private String attachmentMetadataJson;
    private String redactedFieldPathsJson;
    private String omittedContentReasonsJson;
    private String sensitivityLevel;
    private String contentHash;
    private OffsetDateTime sourceObservedAt;
    private OffsetDateTime createdAt;
    private String createdByType;
    private String createdById;
    private OffsetDateTime expiresAt;
    private String status;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String approvalEvidenceHash;
    private String supersedesSnapshotId;
    private String correlationId;
    private String releaseStatus;
    private String releaseEvidenceId;
    private OffsetDateTime releasedAt;
    private String lastReleaseErrorCode;
    private String reconciliationClassification;
    private OffsetDateTime nextReconcileAt;
    private int reconciliationCount;
    private long rowVersion;

    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;}
    public String getSnapshotId(){return snapshotId;} public void setSnapshotId(String v){snapshotId=v;}
    public String getAggregateId(){return aggregateId;} public void setAggregateId(String v){aggregateId=v;}
    public int getSchemaVersion(){return schemaVersion;} public void setSchemaVersion(int v){schemaVersion=v;}
    public String getRootTaskId(){return rootTaskId;} public void setRootTaskId(String v){rootTaskId=v;}
    public String getSourceTaskId(){return sourceTaskId;} public void setSourceTaskId(String v){sourceTaskId=v;}
    public String getTargetTaskId(){return targetTaskId;} public void setTargetTaskId(String v){targetTaskId=v;}
    public String getSourceAgentId(){return sourceAgentId;} public void setSourceAgentId(String v){sourceAgentId=v;}
    public String getTargetAgentId(){return targetAgentId;} public void setTargetAgentId(String v){targetAgentId=v;}
    public String getTargetDomainId(){return targetDomainId;} public void setTargetDomainId(String v){targetDomainId=v;}
    public String getTargetBindingHash(){return targetBindingHash;} public void setTargetBindingHash(String v){targetBindingHash=v;}
    public String getContextPolicyId(){return contextPolicyId;} public void setContextPolicyId(String v){contextPolicyId=v;}
    public long getPolicyVersion(){return policyVersion;} public void setPolicyVersion(long v){policyVersion=v;}
    public int getSnapshotVersion(){return snapshotVersion;} public void setSnapshotVersion(int v){snapshotVersion=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getStructuredContextJson(){return structuredContextJson;} public void setStructuredContextJson(String v){structuredContextJson=v;}
    public String getAllowedCommentRefsJson(){return allowedCommentRefsJson;} public void setAllowedCommentRefsJson(String v){allowedCommentRefsJson=v;}
    public String getAttachmentMetadataJson(){return attachmentMetadataJson;} public void setAttachmentMetadataJson(String v){attachmentMetadataJson=v;}
    public String getRedactedFieldPathsJson(){return redactedFieldPathsJson;} public void setRedactedFieldPathsJson(String v){redactedFieldPathsJson=v;}
    public String getOmittedContentReasonsJson(){return omittedContentReasonsJson;} public void setOmittedContentReasonsJson(String v){omittedContentReasonsJson=v;}
    public String getSensitivityLevel(){return sensitivityLevel;} public void setSensitivityLevel(String v){sensitivityLevel=v;}
    public String getContentHash(){return contentHash;} public void setContentHash(String v){contentHash=v;}
    public OffsetDateTime getSourceObservedAt(){return sourceObservedAt;} public void setSourceObservedAt(OffsetDateTime v){sourceObservedAt=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public String getCreatedByType(){return createdByType;} public void setCreatedByType(String v){createdByType=v;}
    public String getCreatedById(){return createdById;} public void setCreatedById(String v){createdById=v;}
    public OffsetDateTime getExpiresAt(){return expiresAt;} public void setExpiresAt(OffsetDateTime v){expiresAt=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getApprovedBy(){return approvedBy;} public void setApprovedBy(String v){approvedBy=v;}
    public OffsetDateTime getApprovedAt(){return approvedAt;} public void setApprovedAt(OffsetDateTime v){approvedAt=v;}
    public String getApprovalEvidenceHash(){return approvalEvidenceHash;} public void setApprovalEvidenceHash(String v){approvalEvidenceHash=v;}
    public String getSupersedesSnapshotId(){return supersedesSnapshotId;} public void setSupersedesSnapshotId(String v){supersedesSnapshotId=v;}
    public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
    public String getReleaseStatus(){return releaseStatus;} public void setReleaseStatus(String v){releaseStatus=v;}
    public String getReleaseEvidenceId(){return releaseEvidenceId;} public void setReleaseEvidenceId(String v){releaseEvidenceId=v;}
    public OffsetDateTime getReleasedAt(){return releasedAt;} public void setReleasedAt(OffsetDateTime v){releasedAt=v;}
    public String getLastReleaseErrorCode(){return lastReleaseErrorCode;} public void setLastReleaseErrorCode(String v){lastReleaseErrorCode=v;}
    public String getReconciliationClassification(){return reconciliationClassification;} public void setReconciliationClassification(String v){reconciliationClassification=v;}
    public OffsetDateTime getNextReconcileAt(){return nextReconcileAt;} public void setNextReconcileAt(OffsetDateTime v){nextReconcileAt=v;}
    public int getReconciliationCount(){return reconciliationCount;} public void setReconciliationCount(int v){reconciliationCount=v;}
    public long getRowVersion(){return rowVersion;} public void setRowVersion(long v){rowVersion=v;}
}
