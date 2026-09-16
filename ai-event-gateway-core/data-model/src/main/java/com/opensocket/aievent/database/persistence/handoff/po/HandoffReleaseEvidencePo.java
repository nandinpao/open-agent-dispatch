package com.opensocket.aievent.database.persistence.handoff.po;

import java.time.OffsetDateTime;

public class HandoffReleaseEvidencePo {
    private String tenantId;
    private String evidenceId;
    private String snapshotId;
    private String evidenceType;
    private String releaseStatus;
    private String classification;
    private String dispatchEvidenceReference;
    private String reasonCode;
    private int attemptNo;
    private String actorType;
    private String actorId;
    private String correlationId;
    private OffsetDateTime occurredAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String value) { tenantId = value; }
    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String value) { evidenceId = value; }
    public String getSnapshotId() { return snapshotId; }
    public void setSnapshotId(String value) { snapshotId = value; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String value) { evidenceType = value; }
    public String getReleaseStatus() { return releaseStatus; }
    public void setReleaseStatus(String value) { releaseStatus = value; }
    public String getClassification() { return classification; }
    public void setClassification(String value) { classification = value; }
    public String getDispatchEvidenceReference() { return dispatchEvidenceReference; }
    public void setDispatchEvidenceReference(String value) { dispatchEvidenceReference = value; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String value) { reasonCode = value; }
    public int getAttemptNo() { return attemptNo; }
    public void setAttemptNo(int value) { attemptNo = value; }
    public String getActorType() { return actorType; }
    public void setActorType(String value) { actorType = value; }
    public String getActorId() { return actorId; }
    public void setActorId(String value) { actorId = value; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String value) { correlationId = value; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime value) { occurredAt = value; }
}
