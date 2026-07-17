package com.opensocket.aievent.core.task.timeline;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class TaskCaseTimelineStepView {
    private Integer sequence;
    private String stepCode;
    private String eventStage;
    private String eventType;
    private String sourceSystem;
    private String targetSystem;
    private String matchedFlowId;
    private String matchedRuleId;
    private String requestedSkill;
    private String routingPath;
    private String selectedAgentId;
    private String status;
    private String failureStage;
    private String fixAction;
    private String taskId;
    private String parentTaskId;
    private String childTaskId;
    private String correlationId;
    private String message;
    private OffsetDateTime occurredAt;
    private Map<String, Object> details = new LinkedHashMap<>();

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getTargetSystem() { return targetSystem; }
    public void setTargetSystem(String targetSystem) { this.targetSystem = targetSystem; }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getRoutingPath() { return routingPath; }
    public void setRoutingPath(String routingPath) { this.routingPath = routingPath; }
    public String getSelectedAgentId() { return selectedAgentId; }
    public void setSelectedAgentId(String selectedAgentId) { this.selectedAgentId = selectedAgentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getFixAction() { return fixAction; }
    public void setFixAction(String fixAction) { this.fixAction = fixAction; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }
    public String getChildTaskId() { return childTaskId; }
    public void setChildTaskId(String childTaskId) { this.childTaskId = childTaskId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details); }
}
