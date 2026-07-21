package com.opensocket.aievent.core.task;

import java.util.List;

/**
 * Phase 9F A2A Classification Flow contract.
 *
 * <p>This is a Current support contract that describes how TRIAGE Agents may
 * submit classification results while Core remains the only authority allowed
 * to create child / continuation Tasks.</p>
 */
public class TaskA2AClassificationFlowContract {
    private String modelVersion = "A2A_CLASSIFICATION_V1";
    private boolean coreOwnedTaskCreation = true;
    private boolean agentCanCreateTask = false;
    private boolean cycleDetectionRequired = true;
    private boolean idempotencyRequired = true;
    private int defaultMaxA2ADepth = 3;
    private String childTaskCreationAuthority = "CORE_ONLY";
    private String unclearClassificationFallback = "MANUAL_REVIEW_REQUIRED";
    private List<String> requiredRequestFields = List.of(
            "classificationStatus",
            "classificationVersion",
            "idempotencyKey",
            "correlationId"
    );
    private List<String> governanceFields = List.of(
            "parentTaskId",
            "rootTaskId",
            "correlationId",
            "classificationVersion",
            "maxA2ADepth",
            "cycleDetection",
            "idempotencyKey"
    );
    private List<String> childTaskRules = List.of(
            "Classification Agent submits result only",
            "Core validates classification result",
            "Core checks depth and cycle guardrails",
            "Core creates child / continuation Task when classification is clear",
            "Unclear or failed classification routes to manual review instead of uncontrolled A2A"
    );

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public boolean isCoreOwnedTaskCreation() { return coreOwnedTaskCreation; }
    public void setCoreOwnedTaskCreation(boolean coreOwnedTaskCreation) { this.coreOwnedTaskCreation = coreOwnedTaskCreation; }
    public boolean isAgentCanCreateTask() { return agentCanCreateTask; }
    public void setAgentCanCreateTask(boolean agentCanCreateTask) { this.agentCanCreateTask = agentCanCreateTask; }
    public boolean isCycleDetectionRequired() { return cycleDetectionRequired; }
    public void setCycleDetectionRequired(boolean cycleDetectionRequired) { this.cycleDetectionRequired = cycleDetectionRequired; }
    public boolean isIdempotencyRequired() { return idempotencyRequired; }
    public void setIdempotencyRequired(boolean idempotencyRequired) { this.idempotencyRequired = idempotencyRequired; }
    public int getDefaultMaxA2ADepth() { return defaultMaxA2ADepth; }
    public void setDefaultMaxA2ADepth(int defaultMaxA2ADepth) { this.defaultMaxA2ADepth = defaultMaxA2ADepth; }
    public String getChildTaskCreationAuthority() { return childTaskCreationAuthority; }
    public void setChildTaskCreationAuthority(String childTaskCreationAuthority) { this.childTaskCreationAuthority = childTaskCreationAuthority; }
    public String getUnclearClassificationFallback() { return unclearClassificationFallback; }
    public void setUnclearClassificationFallback(String unclearClassificationFallback) { this.unclearClassificationFallback = unclearClassificationFallback; }
    public List<String> getRequiredRequestFields() { return requiredRequestFields; }
    public void setRequiredRequestFields(List<String> requiredRequestFields) { this.requiredRequestFields = requiredRequestFields == null ? List.of() : List.copyOf(requiredRequestFields); }
    public List<String> getGovernanceFields() { return governanceFields; }
    public void setGovernanceFields(List<String> governanceFields) { this.governanceFields = governanceFields == null ? List.of() : List.copyOf(governanceFields); }
    public List<String> getChildTaskRules() { return childTaskRules; }
    public void setChildTaskRules(List<String> childTaskRules) { this.childTaskRules = childTaskRules == null ? List.of() : List.copyOf(childTaskRules); }
}
