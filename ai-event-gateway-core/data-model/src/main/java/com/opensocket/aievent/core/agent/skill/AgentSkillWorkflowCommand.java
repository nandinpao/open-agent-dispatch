package com.opensocket.aievent.core.agent.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic command used by Skill Registry versioning / approval / rollback APIs. */
public class AgentSkillWorkflowCommand {
    private String operatorId;
    private String reason;
    private List<String> operatorRoles = new ArrayList<>();
    private AgentSkillDefinition definition;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getOperatorRoles() { return operatorRoles; }
    public void setOperatorRoles(List<String> operatorRoles) { this.operatorRoles = operatorRoles == null ? new ArrayList<>() : new ArrayList<>(operatorRoles); }
    public AgentSkillDefinition getDefinition() { return definition; }
    public void setDefinition(AgentSkillDefinition definition) { this.definition = definition; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
}
