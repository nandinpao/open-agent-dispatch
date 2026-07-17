package com.opensocket.aievent.core.agent.skill;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Result returned after a Skill Registry workflow transition. */
public class AgentSkillWorkflowResult {
    private String skillCode;
    private int version;
    private AgentSkillLifecycleStatus status;
    private AgentSkillDefinition definition;
    private AgentSkillAuditEntry auditEntry;
    private String message;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime occurredAt;

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = Math.max(1, version); }
    public AgentSkillLifecycleStatus getStatus() { return status; }
    public void setStatus(AgentSkillLifecycleStatus status) { this.status = status; }
    public AgentSkillDefinition getDefinition() { return definition; }
    public void setDefinition(AgentSkillDefinition definition) { this.definition = definition; }
    public AgentSkillAuditEntry getAuditEntry() { return auditEntry; }
    public void setAuditEntry(AgentSkillAuditEntry auditEntry) { this.auditEntry = auditEntry; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
}
