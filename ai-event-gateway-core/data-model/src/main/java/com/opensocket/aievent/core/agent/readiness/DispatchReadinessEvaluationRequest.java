package com.opensocket.aievent.core.agent.readiness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DispatchReadinessEvaluationRequest {
    private String tenantId;
    private String agentId;
    private String taskId;
    private String taskType;
    private String domain;
    private String provider;
    private String siteCode;
    private String plantId;
    private String objectType;
    private String eventType;
    private String errorCode;
    private String operation;
    private String requiredToolPolicy;
    private List<String> requiredCapabilities = new ArrayList<>();
    private List<String> dataClasses = new ArrayList<>();
    private Map<String, Object> payloadMetadata = new LinkedHashMap<>();

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getRequiredToolPolicy() { return requiredToolPolicy; }
    public void setRequiredToolPolicy(String requiredToolPolicy) { this.requiredToolPolicy = requiredToolPolicy; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(List<String> requiredCapabilities) { this.requiredCapabilities = requiredCapabilities == null ? new ArrayList<>() : new ArrayList<>(requiredCapabilities); }
    public List<String> getDataClasses() { return dataClasses; }
    public void setDataClasses(List<String> dataClasses) { this.dataClasses = dataClasses == null ? new ArrayList<>() : new ArrayList<>(dataClasses); }
    public Map<String, Object> getPayloadMetadata() { return payloadMetadata; }
    public void setPayloadMetadata(Map<String, Object> payloadMetadata) { this.payloadMetadata = payloadMetadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payloadMetadata); }
}
