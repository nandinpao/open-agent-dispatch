package com.opensocket.aievent.core.task.evidence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.opensocket.aievent.core.agent.contract.DispatchContractReadinessResponse;
import com.opensocket.aievent.core.agent.eligibility.TaskEligibleAgentsResponse;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.issue.TaskIssueLink;
import com.opensocket.aievent.core.routing.RoutingDecisionRecord;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.timeline.DispatchTimelineResponse;

public class TaskDispatchEvidenceView {
    private String taskId;
    private String status;
    private String summary;
    private String firstBlockingStage;
    private String firstBlockingCode;
    private String firstBlockingReason;
    private TaskRecord task;
    private DispatchContractReadinessResponse contractReadiness;
    private TaskEligibleAgentsResponse eligibleAgents;
    private RoutingDecisionRecord latestRoutingDecision;
    private List<DispatchRequest> dispatchRequests = List.of();
    private DispatchTimelineResponse timeline;
    private TaskIssueLink issueTracking;
    private List<TaskDispatchEvidenceStage> stages = List.of();
    private List<TaskDispatchRecoveryAction> suggestedActions = List.of();
    private Map<String, Object> diagnostics = Map.of();
    private OffsetDateTime generatedAt;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFirstBlockingStage() { return firstBlockingStage; }
    public void setFirstBlockingStage(String firstBlockingStage) { this.firstBlockingStage = firstBlockingStage; }
    public String getFirstBlockingCode() { return firstBlockingCode; }
    public void setFirstBlockingCode(String firstBlockingCode) { this.firstBlockingCode = firstBlockingCode; }
    public String getFirstBlockingReason() { return firstBlockingReason; }
    public void setFirstBlockingReason(String firstBlockingReason) { this.firstBlockingReason = firstBlockingReason; }
    public TaskRecord getTask() { return task; }
    public void setTask(TaskRecord task) { this.task = task; }
    public DispatchContractReadinessResponse getContractReadiness() { return contractReadiness; }
    public void setContractReadiness(DispatchContractReadinessResponse contractReadiness) { this.contractReadiness = contractReadiness; }
    public TaskEligibleAgentsResponse getEligibleAgents() { return eligibleAgents; }
    public void setEligibleAgents(TaskEligibleAgentsResponse eligibleAgents) { this.eligibleAgents = eligibleAgents; }
    public RoutingDecisionRecord getLatestRoutingDecision() { return latestRoutingDecision; }
    public void setLatestRoutingDecision(RoutingDecisionRecord latestRoutingDecision) { this.latestRoutingDecision = latestRoutingDecision; }
    public List<DispatchRequest> getDispatchRequests() { return dispatchRequests; }
    public void setDispatchRequests(List<DispatchRequest> dispatchRequests) { this.dispatchRequests = dispatchRequests == null ? List.of() : List.copyOf(dispatchRequests); }
    public DispatchTimelineResponse getTimeline() { return timeline; }
    public void setTimeline(DispatchTimelineResponse timeline) { this.timeline = timeline; }
    public TaskIssueLink getIssueTracking() { return issueTracking; }
    public void setIssueTracking(TaskIssueLink issueTracking) { this.issueTracking = issueTracking; }
    public List<TaskDispatchEvidenceStage> getStages() { return stages; }
    public void setStages(List<TaskDispatchEvidenceStage> stages) { this.stages = stages == null ? List.of() : List.copyOf(stages); }
    public List<TaskDispatchRecoveryAction> getSuggestedActions() { return suggestedActions; }
    public void setSuggestedActions(List<TaskDispatchRecoveryAction> suggestedActions) { this.suggestedActions = suggestedActions == null ? List.of() : List.copyOf(suggestedActions); }
    public Map<String, Object> getDiagnostics() { return diagnostics; }
    public void setDiagnostics(Map<String, Object> diagnostics) { this.diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics); }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
}
