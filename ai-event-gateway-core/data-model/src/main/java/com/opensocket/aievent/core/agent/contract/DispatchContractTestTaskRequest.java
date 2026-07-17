package com.opensocket.aievent.core.agent.contract;

import java.util.List;
import java.util.Map;

public class DispatchContractTestTaskRequest {
    private String tenantId;
    private String sourceSystem;
    private String sourceSystemName;
    private String taskType;
    private String severity;
    private String siteId;
    private String plantId;
    private String objectType;
    private String objectId;
    private String eventType;
    private String errorCode;
    private String message;
    private String agentId;
    private List<String> requiredCapabilities = List.of();
    private boolean ensureContract = false;
    private boolean assignAgent = true;
    private boolean approveAgentQualification = true;
    private boolean approveAgentCapability = true;
    private boolean activate = true;
    private String operatorId;
    private Map<String, Object> attributes;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceSystemName() { return sourceSystemName; }
    public void setSourceSystemName(String sourceSystemName) { this.sourceSystemName = sourceSystemName; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getSiteId() { return siteId; }
    public void setSiteId(String siteId) { this.siteId = siteId; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities); }
    public boolean isEnsureContract() { return ensureContract; }
    public void setEnsureContract(boolean ensureContract) { this.ensureContract = ensureContract; }
    public boolean isAssignAgent() { return assignAgent; }
    public void setAssignAgent(boolean assignAgent) { this.assignAgent = assignAgent; }
    public boolean isApproveAgentQualification() { return approveAgentQualification; }
    public void setApproveAgentQualification(boolean approveAgentQualification) { this.approveAgentQualification = approveAgentQualification; }
    public boolean isApproveAgentCapability() { return approveAgentCapability; }
    public void setApproveAgentCapability(boolean approveAgentCapability) { this.approveAgentCapability = approveAgentCapability; }
    public boolean isActivate() { return activate; }
    public void setActivate(boolean activate) { this.activate = activate; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}
