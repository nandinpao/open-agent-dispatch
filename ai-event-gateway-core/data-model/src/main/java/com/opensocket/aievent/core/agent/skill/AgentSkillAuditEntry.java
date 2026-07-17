package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Audit trail entry for Skill Registry workflow and definition changes. */
public class AgentSkillAuditEntry {
    private String auditId = UUID.randomUUID().toString();
    private String skillCode;
    private int version;
    private String action;
    private String operatorId;
    private String reason;
    private AgentSkillLifecycleStatus fromStatus;
    private AgentSkillLifecycleStatus toStatus;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime createdAt;

    public String getAuditId() { return auditId; }
    public void setAuditId(String auditId) { this.auditId = auditId; }
    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = Math.max(1, version); }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public AgentSkillLifecycleStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(AgentSkillLifecycleStatus fromStatus) { this.fromStatus = fromStatus; }
    public AgentSkillLifecycleStatus getToStatus() { return toStatus; }
    public void setToStatus(AgentSkillLifecycleStatus toStatus) { this.toStatus = toStatus; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
