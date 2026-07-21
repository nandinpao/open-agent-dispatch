package com.opensocket.aievent.core.dispatch.flow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * No-side-effect Dispatch Simulation criteria used by the Current Source Flow / Agent Pool workspace.
 *
 * <p>The simulation request mirrors the fields that production Flow/Rule routing consumes.  It must
 * not be handled by Event Intake and must never create Task, Assignment, Delivery, ACK, or Result rows.</p>
 */
public class DispatchSimulationRequest {
    private String tenantId;
    private String flowId;
    private String sourceSystem;
    private String originSourceSystem;
    private String targetSystem;
    private String eventStage = "EXTERNAL";
    private String objectType;
    private String eventType;
    private String errorCode;
    private String severity;
    private String message;
    private String siteId;
    private String plantId;
    private Map<String, Object> attributes = new LinkedHashMap<>();
    private Boolean includeRuntimeSnapshot = Boolean.TRUE;

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
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes); }
    public Boolean getIncludeRuntimeSnapshot() { return includeRuntimeSnapshot; }
    public void setIncludeRuntimeSnapshot(Boolean includeRuntimeSnapshot) { this.includeRuntimeSnapshot = includeRuntimeSnapshot; }
}
