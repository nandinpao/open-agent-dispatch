package com.opensocket.aievent.core.task.timeline;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class TaskCaseTimelineView {
    private String taskId;
    private String parentTaskId;
    private List<String> childTaskIds = new ArrayList<>();
    private String correlationId;
    private String matchedFlowId;
    private String matchedRuleId;
    private String requestedSkill;
    private String eventStage;
    private String routingPath;
    private String failureStage;
    private String fixAction;
    private List<TaskCaseTimelineStepView> steps = new ArrayList<>();
    private OffsetDateTime generatedAt;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }
    public List<String> getChildTaskIds() { return childTaskIds; }
    public void setChildTaskIds(List<String> childTaskIds) { this.childTaskIds = childTaskIds == null ? new ArrayList<>() : new ArrayList<>(childTaskIds); }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getMatchedFlowId() { return matchedFlowId; }
    public void setMatchedFlowId(String matchedFlowId) { this.matchedFlowId = matchedFlowId; }
    public String getMatchedRuleId() { return matchedRuleId; }
    public void setMatchedRuleId(String matchedRuleId) { this.matchedRuleId = matchedRuleId; }
    public String getRequestedSkill() { return requestedSkill; }
    public void setRequestedSkill(String requestedSkill) { this.requestedSkill = requestedSkill; }
    public String getEventStage() { return eventStage; }
    public void setEventStage(String eventStage) { this.eventStage = eventStage; }
    public String getRoutingPath() { return routingPath; }
    public void setRoutingPath(String routingPath) { this.routingPath = routingPath; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String failureStage) { this.failureStage = failureStage; }
    public String getFixAction() { return fixAction; }
    public void setFixAction(String fixAction) { this.fixAction = fixAction; }
    public List<TaskCaseTimelineStepView> getSteps() { return steps; }
    public void setSteps(List<TaskCaseTimelineStepView> steps) { this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps); }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
