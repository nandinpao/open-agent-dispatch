package com.opensocket.aievent.core.dispatch.flow;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/** Runtime lookup criteria for Flow-owned Dispatch Rule matching. */
public class FlowRuleRuntimeQuery {
    private String tenantId;
    private String flowId;
    private String sourceSystem;
    private String originSourceSystem;
    private String targetSystem;
    private String eventStage;
    private String eventType;
    private String objectType;
    private String errorCode;
    private String severity;
    private String requestedSkill;
    private Map<String,Object> matchAttributes = Map.of();

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
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public Map<String,Object> getMatchAttributes() { return matchAttributes; }
    public void setMatchAttributes(Map<String,Object> matchAttributes) {
        if (matchAttributes == null || matchAttributes.isEmpty()) { this.matchAttributes = Map.of(); return; }
        LinkedHashMap<String,Object> copy = new LinkedHashMap<>();
        matchAttributes.forEach((key,value) -> { if (key != null) copy.put(key, value); });
        this.matchAttributes = Collections.unmodifiableMap(copy);
    }
}
