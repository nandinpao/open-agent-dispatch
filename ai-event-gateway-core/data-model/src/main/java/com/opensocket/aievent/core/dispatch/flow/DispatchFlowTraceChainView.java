package com.opensocket.aievent.core.dispatch.flow;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DispatchFlowTraceChainView {
    private String tenantId;
    private String flowId;
    private String flowCode;
    private String testMode;
    private String status = "NOT_RUN";
    private String summary;
    private String failureStage;
    private String fixAction;
    private String correlationId;
    private String parentTaskId;
    private List<DispatchFlowTraceStepView> steps = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private OffsetDateTime generatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getFlowCode() { return flowCode; }
    public void setFlowCode(String flowCode) { this.flowCode = flowCode; }
    public String getTestMode() { return testMode; }
    public void setTestMode(String testMode) { this.testMode = testMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getFixAction() { return fixAction; }
    public void setFixAction(String fixAction) { this.fixAction = fixAction; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }
    public List<DispatchFlowTraceStepView> getSteps() { return steps; }
    public void setSteps(List<DispatchFlowTraceStepView> steps) { this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps); }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata); }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
