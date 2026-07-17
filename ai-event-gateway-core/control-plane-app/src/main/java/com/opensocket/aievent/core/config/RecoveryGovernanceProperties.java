package com.opensocket.aievent.core.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** P10.6 guardrail configuration for operator-triggered recovery governance actions. */
@ConfigurationProperties(prefix = "core.recovery.governance")
public class RecoveryGovernanceProperties {
    private boolean enabled = true;
    private boolean requireReason = true;
    private int minReasonLength = 12;
    private boolean requireConfirmation = true;
    private String moderateConfirmationPhrase = "CONFIRM_RECOVERY_ACTION";
    private String highRiskConfirmationPhrase = "CONFIRM_HIGH_RISK_RECOVERY";
    private boolean allowBodyOperatorIdOverride = false;
    private boolean requireDualControlForHighRisk = true;
    private boolean forbidSelfApproval = true;
    private Duration approvalTtl = Duration.ofHours(4);
    private String approvalConfirmationPhrase = "CONFIRM_DUAL_CONTROL_APPROVAL";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRequireReason() { return requireReason; }
    public void setRequireReason(boolean requireReason) { this.requireReason = requireReason; }
    public int getMinReasonLength() { return minReasonLength; }
    public void setMinReasonLength(int minReasonLength) { this.minReasonLength = Math.max(1, minReasonLength); }
    public boolean isRequireConfirmation() { return requireConfirmation; }
    public void setRequireConfirmation(boolean requireConfirmation) { this.requireConfirmation = requireConfirmation; }
    public String getModerateConfirmationPhrase() { return normalize(moderateConfirmationPhrase, "CONFIRM_RECOVERY_ACTION"); }
    public void setModerateConfirmationPhrase(String moderateConfirmationPhrase) { this.moderateConfirmationPhrase = moderateConfirmationPhrase; }
    public String getHighRiskConfirmationPhrase() { return normalize(highRiskConfirmationPhrase, "CONFIRM_HIGH_RISK_RECOVERY"); }
    public void setHighRiskConfirmationPhrase(String highRiskConfirmationPhrase) { this.highRiskConfirmationPhrase = highRiskConfirmationPhrase; }
    public boolean isAllowBodyOperatorIdOverride() { return allowBodyOperatorIdOverride; }
    public void setAllowBodyOperatorIdOverride(boolean allowBodyOperatorIdOverride) { this.allowBodyOperatorIdOverride = allowBodyOperatorIdOverride; }
    public boolean isRequireDualControlForHighRisk() { return requireDualControlForHighRisk; }
    public void setRequireDualControlForHighRisk(boolean requireDualControlForHighRisk) { this.requireDualControlForHighRisk = requireDualControlForHighRisk; }
    public boolean isForbidSelfApproval() { return forbidSelfApproval; }
    public void setForbidSelfApproval(boolean forbidSelfApproval) { this.forbidSelfApproval = forbidSelfApproval; }
    public Duration getApprovalTtl() { return approvalTtl == null || approvalTtl.isNegative() || approvalTtl.isZero() ? Duration.ofHours(4) : approvalTtl; }
    public void setApprovalTtl(Duration approvalTtl) { this.approvalTtl = approvalTtl; }
    public String getApprovalConfirmationPhrase() { return normalize(approvalConfirmationPhrase, "CONFIRM_DUAL_CONTROL_APPROVAL"); }
    public void setApprovalConfirmationPhrase(String approvalConfirmationPhrase) { this.approvalConfirmationPhrase = approvalConfirmationPhrase; }

    private String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
