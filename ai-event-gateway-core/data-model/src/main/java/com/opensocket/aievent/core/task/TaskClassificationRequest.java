package com.opensocket.aievent.core.task;

/**
 * Classification result submitted by a TRIAGE Agent.
 *
 * <p>Phase 9F A2A rule: the Agent submits only the classification result. Core
 * validates the result and remains the only authority that may create a child /
 * continuation Task.</p>
 */
public class TaskClassificationRequest {
    private String classificationStatus;
    private String sourceSystem;
    private String objectType;
    private String eventType;
    private String errorCode;
    private String severity;
    private Double confidence;
    private String reason;
    private String recommendedPoolCode;
    private Boolean createResolutionTask;
    private String parentTaskId;
    private String rootTaskId;
    private String correlationId;
    private String classificationVersion;
    private Integer maxA2ADepth;
    private String idempotencyKey;

    public String getClassificationStatus() { return classificationStatus; }
    public void setClassificationStatus(String classificationStatus) { this.classificationStatus = classificationStatus; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRecommendedPoolCode() { return recommendedPoolCode; }
    public void setRecommendedPoolCode(String recommendedPoolCode) { this.recommendedPoolCode = recommendedPoolCode; }
    public Boolean getCreateResolutionTask() { return createResolutionTask; }
    public void setCreateResolutionTask(Boolean createResolutionTask) { this.createResolutionTask = createResolutionTask; }
    public String getParentTaskId() { return parentTaskId; }
    public void setParentTaskId(String parentTaskId) { this.parentTaskId = parentTaskId; }
    public String getRootTaskId() { return rootTaskId; }
    public void setRootTaskId(String rootTaskId) { this.rootTaskId = rootTaskId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getClassificationVersion() { return classificationVersion; }
    public void setClassificationVersion(String classificationVersion) { this.classificationVersion = classificationVersion; }
    public Integer getMaxA2ADepth() { return maxA2ADepth; }
    public void setMaxA2ADepth(Integer maxA2ADepth) { this.maxA2ADepth = maxA2ADepth; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public boolean shouldCreateResolutionTask() {
        return createResolutionTask == null || createResolutionTask;
    }
}
