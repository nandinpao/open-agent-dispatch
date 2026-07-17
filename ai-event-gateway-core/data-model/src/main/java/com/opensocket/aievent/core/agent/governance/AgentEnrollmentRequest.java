package com.opensocket.aievent.core.agent.governance;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentEnrollmentRequest {
    private String enrollmentId;
    private String claimedAgentId;
    private String tenantId;
    private String agentName;
    private String agentType;
    private Map<String, Object> submittedMetadata = new LinkedHashMap<>();
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private String fingerprint;
    private String remoteAddress;
    private AgentEnrollmentStatus status = AgentEnrollmentStatus.PENDING_REVIEW;
    private OffsetDateTime submittedAt;
    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private String reviewComment;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(String enrollmentId) { this.enrollmentId = enrollmentId; }
    public String getClaimedAgentId() { return claimedAgentId; }
    public void setClaimedAgentId(String claimedAgentId) { this.claimedAgentId = claimedAgentId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public Map<String, Object> getSubmittedMetadata() { return submittedMetadata; }
    public void setSubmittedMetadata(Map<String, Object> submittedMetadata) { this.submittedMetadata = submittedMetadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(submittedMetadata); }
    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(evidence); }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getRemoteAddress() { return remoteAddress; }
    public void setRemoteAddress(String remoteAddress) { this.remoteAddress = remoteAddress; }
    public AgentEnrollmentStatus getStatus() { return status; }
    public void setStatus(AgentEnrollmentStatus status) { this.status = status == null ? AgentEnrollmentStatus.PENDING_REVIEW : status; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
