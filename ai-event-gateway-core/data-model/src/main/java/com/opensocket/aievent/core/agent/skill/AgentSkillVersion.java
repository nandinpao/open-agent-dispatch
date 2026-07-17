package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned draft/approved/published definition for one skill code. */
public class AgentSkillVersion {
    private String skillCode;
    private int version;
    private AgentSkillLifecycleStatus status = AgentSkillLifecycleStatus.DRAFT;
    private AgentSkillDefinition definition;
    private String submittedBy;
    private OffsetDateTime submittedAt;
    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private String reviewComment;
    private String publishedBy;
    private OffsetDateTime publishedAt;
    private Integer supersedesVersion;
    private Integer rollbackOfVersion;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = Math.max(1, version); }
    public AgentSkillLifecycleStatus getStatus() { return status; }
    public void setStatus(AgentSkillLifecycleStatus status) { this.status = status == null ? AgentSkillLifecycleStatus.DRAFT : status; }
    public AgentSkillDefinition getDefinition() { return definition; }
    public void setDefinition(AgentSkillDefinition definition) { this.definition = definition; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(OffsetDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Integer getSupersedesVersion() { return supersedesVersion; }
    public void setSupersedesVersion(Integer supersedesVersion) { this.supersedesVersion = supersedesVersion; }
    public Integer getRollbackOfVersion() { return rollbackOfVersion; }
    public void setRollbackOfVersion(Integer rollbackOfVersion) { this.rollbackOfVersion = rollbackOfVersion; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
