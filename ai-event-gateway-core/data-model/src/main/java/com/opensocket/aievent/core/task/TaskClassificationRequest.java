package com.opensocket.aievent.core.task;

/**
 * Phase 32-E classification result submitted by a TRIAGE Agent.
 *
 * <p>The request updates the parent TRIAGE task and may create a RESOLUTION
 * child task that re-enters Source Flow / Agent Pool routing.</p>
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

    public boolean shouldCreateResolutionTask() {
        return createResolutionTask == null || createResolutionTask;
    }
}
