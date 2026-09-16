package com.opensocket.aievent.core.task;

import java.util.List;

/**
 * Phase 9F classification-continuation compatibility contract.
 *
 * <p>The historical API/model name contains A2A, but this contract is not the
 * capability-first A2A delegation authority. TRIAGE Agents submit classification
 * results only; Core may create a normal RESOLUTION continuation Task and route it
 * through Source Flow / Agent Pool dispatch. Capability delegation remains solely
 * owned by ManagedCapabilityDelegationRuntimeService.</p>
 */
public class TaskA2AClassificationFlowContract {
    private String modelVersion = "A2A_CLASSIFICATION_V1";
    private String currentAuthorityModel = "CORE_TASK_CLASSIFICATION_CONTINUATION";
    private boolean a2aDelegationAuthority = false;
    private String continuationRoutingMode = "SOURCE_FLOW_DIRECT_DISPATCH";
    private boolean recommendedPoolRoutingAuthority = false;
    private String canonicalA2ADelegationAuthority = "ManagedCapabilityDelegationRuntimeService";
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
            "recommendedPoolCode is evidence only and cannot select the runtime pool or Agent",
            "Core validates classification result and recursion guardrails",
            "Core creates a normal RESOLUTION continuation Task when automation is allowed",
            "Source Flow and Agent Pool direct-dispatch authority route the continuation Task",
            "Capability-first A2A delegation remains a separate ManagedCapabilityDelegationRuntimeService path",
            "Unclear or failed classification routes to manual review instead of uncontrolled continuation"
    );

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getCurrentAuthorityModel() { return currentAuthorityModel; }
    public void setCurrentAuthorityModel(String currentAuthorityModel) { this.currentAuthorityModel = currentAuthorityModel; }
    public boolean isA2aDelegationAuthority() { return a2aDelegationAuthority; }
    public void setA2aDelegationAuthority(boolean a2aDelegationAuthority) { this.a2aDelegationAuthority = a2aDelegationAuthority; }
    public String getContinuationRoutingMode() { return continuationRoutingMode; }
    public void setContinuationRoutingMode(String continuationRoutingMode) { this.continuationRoutingMode = continuationRoutingMode; }
    public boolean isRecommendedPoolRoutingAuthority() { return recommendedPoolRoutingAuthority; }
    public void setRecommendedPoolRoutingAuthority(boolean recommendedPoolRoutingAuthority) { this.recommendedPoolRoutingAuthority = recommendedPoolRoutingAuthority; }
    public String getCanonicalA2ADelegationAuthority() { return canonicalA2ADelegationAuthority; }
    public void setCanonicalA2ADelegationAuthority(String canonicalA2ADelegationAuthority) { this.canonicalA2ADelegationAuthority = canonicalA2ADelegationAuthority; }
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
