package com.opensocket.aievent.core.dispatch.flow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P2 Flow Rule dry-run criteria.
 *
 * This request mirrors the fields used by runtime Flow Rule matching so Admin UI
 * readiness does not fall back to legacy Task Definition / Profile checks.
 */
public class DispatchFlowReadinessRequest {
    private String tenantId;
    private String flowId;
    private String sourceSystem;
    private String originSourceSystem;
    private String targetSystem;
    private String eventStage = "EXTERNAL";
    private String objectType = "*";
    private String eventType = "*";
    private String errorCode = "*";
    private String requestedSkill;
    private String agentId;
    private String severity;
    private String message;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getOriginSourceSystem() { return originSourceSystem; }
    public void setOriginSourceSystem(String originSourceSystem) { this.originSourceSystem = originSourceSystem; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes); }
}
