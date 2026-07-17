package com.opensocket.aievent.core.routing.cutover;

import java.time.OffsetDateTime;

/** Tenant/Flow scoped P10 cutover policy. Flow scope "*" is the tenant default. */
public class DispatchCutoverPolicy {
    private String tenantId;
    private String policyId;
    private String flowId = "*";
    private DispatchCutoverMode mode = DispatchCutoverMode.SHADOW;
    private int canaryPercentage;
    private int minimumSampleSize = 50;
    private double maximumRequirementBlockedRate = 0.05d;
    private double maximumNoCandidateRate = 0.10d;
    private double maximumSelectionDifferenceRate = 0.20d;
    private boolean autoRollbackEnabled = true;
    private DispatchCutoverPolicyStatus status = DispatchCutoverPolicyStatus.DRAFT;
    private int version = 1;
    private OffsetDateTime rolledBackAt;
    private String rollbackReason;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime updatedAt;
    private String updatedBy;

    public void validate() {
        require(tenantId, "tenantId");
        require(policyId, "policyId");
        require(flowId, "flowId");
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (canaryPercentage < 0 || canaryPercentage > 100) throw new IllegalArgumentException("canaryPercentage must be 0..100");
        if (minimumSampleSize < 1) throw new IllegalArgumentException("minimumSampleSize must be >= 1");
        rate(maximumRequirementBlockedRate, "maximumRequirementBlockedRate");
        rate(maximumNoCandidateRate, "maximumNoCandidateRate");
        rate(maximumSelectionDifferenceRate, "maximumSelectionDifferenceRate");
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        if (status == DispatchCutoverPolicyStatus.ROLLED_BACK) {
            if (mode != DispatchCutoverMode.ROLLED_BACK) {
                throw new IllegalArgumentException("ROLLED_BACK policy status requires ROLLED_BACK mode");
            }
            if (rolledBackAt == null || rollbackReason == null || rollbackReason.isBlank()) {
                throw new IllegalArgumentException("rolledBackAt and rollbackReason are required for ROLLED_BACK policy");
            }
        } else if (mode == DispatchCutoverMode.ROLLED_BACK) {
            throw new IllegalArgumentException("ROLLED_BACK mode can only be set by the rollback API");
        }
    }
    public boolean active() { return status == DispatchCutoverPolicyStatus.ACTIVE; }
    private static void rate(double value, String field) { if (value < 0d || value > 1d) throw new IllegalArgumentException(field + " must be 0..1"); }
    private static void require(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId == null || flowId.isBlank() ? "*" : flowId; }
    public DispatchCutoverMode getMode() { return mode; }
    public void setMode(DispatchCutoverMode mode) { this.mode = mode; }
    public int getCanaryPercentage() { return canaryPercentage; }
    public void setCanaryPercentage(int canaryPercentage) { this.canaryPercentage = canaryPercentage; }
    public int getMinimumSampleSize() { return minimumSampleSize; }
    public void setMinimumSampleSize(int minimumSampleSize) { this.minimumSampleSize = minimumSampleSize; }
    public double getMaximumRequirementBlockedRate() { return maximumRequirementBlockedRate; }
    public void setMaximumRequirementBlockedRate(double value) { this.maximumRequirementBlockedRate = value; }
    public double getMaximumNoCandidateRate() { return maximumNoCandidateRate; }
    public void setMaximumNoCandidateRate(double value) { this.maximumNoCandidateRate = value; }
    public double getMaximumSelectionDifferenceRate() { return maximumSelectionDifferenceRate; }
    public void setMaximumSelectionDifferenceRate(double value) { this.maximumSelectionDifferenceRate = value; }
    public boolean isAutoRollbackEnabled() { return autoRollbackEnabled; }
    public void setAutoRollbackEnabled(boolean value) { this.autoRollbackEnabled = value; }
    public DispatchCutoverPolicyStatus getStatus() { return status; }
    public void setStatus(DispatchCutoverPolicyStatus status) { this.status = status; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public OffsetDateTime getRolledBackAt() { return rolledBackAt; }
    public void setRolledBackAt(OffsetDateTime value) { this.rolledBackAt = value; }
    public String getRollbackReason() { return rollbackReason; }
    public void setRollbackReason(String value) { this.rollbackReason = value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { this.createdBy = value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt = value; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String value) { this.updatedBy = value; }
}
