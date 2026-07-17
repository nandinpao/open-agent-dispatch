package com.opensocket.aievent.core.observability;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "core.observability")
public class ObservabilityProperties {
    private boolean enabled = true;
    private boolean businessMetricsEnabled = true;
    private boolean repositorySummaryEnabled = true;
    private boolean healthIndicatorEnabled = true;
    private boolean includeTenantTag = false;
    private boolean includeSiteTag = true;
    private int summarySampleLimit = 500;
    private Duration slowIntakeThreshold = Duration.ofSeconds(3);
    private Map<String, String> commonTags = new LinkedHashMap<>();
    private RecoveryMetrics recoveryMetrics = new RecoveryMetrics();
    private RemediationWorkflowMetrics remediationWorkflowMetrics = new RemediationWorkflowMetrics();
    private SloMetrics sloMetrics = new SloMetrics();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isBusinessMetricsEnabled() { return businessMetricsEnabled; }
    public void setBusinessMetricsEnabled(boolean businessMetricsEnabled) { this.businessMetricsEnabled = businessMetricsEnabled; }
    public boolean isRepositorySummaryEnabled() { return repositorySummaryEnabled; }
    public void setRepositorySummaryEnabled(boolean repositorySummaryEnabled) { this.repositorySummaryEnabled = repositorySummaryEnabled; }
    public boolean isHealthIndicatorEnabled() { return healthIndicatorEnabled; }
    public void setHealthIndicatorEnabled(boolean healthIndicatorEnabled) { this.healthIndicatorEnabled = healthIndicatorEnabled; }
    public boolean isIncludeTenantTag() { return includeTenantTag; }
    public void setIncludeTenantTag(boolean includeTenantTag) { this.includeTenantTag = includeTenantTag; }
    public boolean isIncludeSiteTag() { return includeSiteTag; }
    public void setIncludeSiteTag(boolean includeSiteTag) { this.includeSiteTag = includeSiteTag; }
    public int getSummarySampleLimit() { return Math.max(1, Math.min(summarySampleLimit, 5000)); }
    public void setSummarySampleLimit(int summarySampleLimit) { this.summarySampleLimit = summarySampleLimit; }
    public Duration getSlowIntakeThreshold() { return slowIntakeThreshold; }
    public void setSlowIntakeThreshold(Duration slowIntakeThreshold) { this.slowIntakeThreshold = slowIntakeThreshold == null ? Duration.ofSeconds(3) : slowIntakeThreshold; }
    public Map<String, String> getCommonTags() { return commonTags; }
    public void setCommonTags(Map<String, String> commonTags) { this.commonTags = commonTags == null ? new LinkedHashMap<>() : new LinkedHashMap<>(commonTags); }
    public RecoveryMetrics getRecoveryMetrics() { return recoveryMetrics; }
    public void setRecoveryMetrics(RecoveryMetrics recoveryMetrics) { this.recoveryMetrics = recoveryMetrics == null ? new RecoveryMetrics() : recoveryMetrics; }
    public RemediationWorkflowMetrics getRemediationWorkflowMetrics() { return remediationWorkflowMetrics; }
    public void setRemediationWorkflowMetrics(RemediationWorkflowMetrics remediationWorkflowMetrics) { this.remediationWorkflowMetrics = remediationWorkflowMetrics == null ? new RemediationWorkflowMetrics() : remediationWorkflowMetrics; }
    public SloMetrics getSloMetrics() { return sloMetrics; }
    public void setSloMetrics(SloMetrics sloMetrics) { this.sloMetrics = sloMetrics == null ? new SloMetrics() : sloMetrics; }

    public static class SloMetrics {
        private boolean enabled = true;
        private Duration callbackLagWarning = Duration.ofSeconds(30);
        private Duration callbackLagCritical = Duration.ofMinutes(2);
        private int dispatchRetryWarningThreshold = 5;
        private int dispatchRetryCriticalThreshold = 20;
        private int dispatchDeadLetterWarningThreshold = 1;
        private int dispatchDeadLetterCriticalThreshold = 5;
        private double adapterFailureRatioWarning = 0.20d;
        private double adapterFailureRatioCritical = 0.50d;
        private double routingNoCandidateRatioWarning = 0.10d;
        private double routingNoCandidateRatioCritical = 0.30d;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getCallbackLagWarning() { return callbackLagWarning; }
        public void setCallbackLagWarning(Duration callbackLagWarning) { this.callbackLagWarning = callbackLagWarning == null ? Duration.ofSeconds(30) : callbackLagWarning; }
        public Duration getCallbackLagCritical() { return callbackLagCritical; }
        public void setCallbackLagCritical(Duration callbackLagCritical) { this.callbackLagCritical = callbackLagCritical == null ? Duration.ofMinutes(2) : callbackLagCritical; }
        public int getDispatchRetryWarningThreshold() { return Math.max(0, dispatchRetryWarningThreshold); }
        public void setDispatchRetryWarningThreshold(int dispatchRetryWarningThreshold) { this.dispatchRetryWarningThreshold = dispatchRetryWarningThreshold; }
        public int getDispatchRetryCriticalThreshold() { return Math.max(0, dispatchRetryCriticalThreshold); }
        public void setDispatchRetryCriticalThreshold(int dispatchRetryCriticalThreshold) { this.dispatchRetryCriticalThreshold = dispatchRetryCriticalThreshold; }
        public int getDispatchDeadLetterWarningThreshold() { return Math.max(0, dispatchDeadLetterWarningThreshold); }
        public void setDispatchDeadLetterWarningThreshold(int dispatchDeadLetterWarningThreshold) { this.dispatchDeadLetterWarningThreshold = dispatchDeadLetterWarningThreshold; }
        public int getDispatchDeadLetterCriticalThreshold() { return Math.max(0, dispatchDeadLetterCriticalThreshold); }
        public void setDispatchDeadLetterCriticalThreshold(int dispatchDeadLetterCriticalThreshold) { this.dispatchDeadLetterCriticalThreshold = dispatchDeadLetterCriticalThreshold; }
        public double getAdapterFailureRatioWarning() { return Math.max(0.0d, Math.min(adapterFailureRatioWarning, 1.0d)); }
        public void setAdapterFailureRatioWarning(double adapterFailureRatioWarning) { this.adapterFailureRatioWarning = adapterFailureRatioWarning; }
        public double getAdapterFailureRatioCritical() { return Math.max(0.0d, Math.min(adapterFailureRatioCritical, 1.0d)); }
        public void setAdapterFailureRatioCritical(double adapterFailureRatioCritical) { this.adapterFailureRatioCritical = adapterFailureRatioCritical; }
        public double getRoutingNoCandidateRatioWarning() { return Math.max(0.0d, Math.min(routingNoCandidateRatioWarning, 1.0d)); }
        public void setRoutingNoCandidateRatioWarning(double routingNoCandidateRatioWarning) { this.routingNoCandidateRatioWarning = routingNoCandidateRatioWarning; }
        public double getRoutingNoCandidateRatioCritical() { return Math.max(0.0d, Math.min(routingNoCandidateRatioCritical, 1.0d)); }
        public void setRoutingNoCandidateRatioCritical(double routingNoCandidateRatioCritical) { this.routingNoCandidateRatioCritical = routingNoCandidateRatioCritical; }
    }

    public static class RemediationWorkflowMetrics {
        private boolean enabled = true;
        private Duration staleLeaseAlertWindow = Duration.ofMinutes(10);
        private Duration approvalLatencyWarning = Duration.ofMinutes(30);
        private Duration approvalLatencyCritical = Duration.ofHours(2);
        private double actionFailureRatioWarning = 0.20d;
        private double actionFailureRatioCritical = 0.50d;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getStaleLeaseAlertWindow() { return staleLeaseAlertWindow; }
        public void setStaleLeaseAlertWindow(Duration staleLeaseAlertWindow) { this.staleLeaseAlertWindow = staleLeaseAlertWindow == null ? Duration.ofMinutes(10) : staleLeaseAlertWindow; }
        public Duration getApprovalLatencyWarning() { return approvalLatencyWarning; }
        public void setApprovalLatencyWarning(Duration approvalLatencyWarning) { this.approvalLatencyWarning = approvalLatencyWarning == null ? Duration.ofMinutes(30) : approvalLatencyWarning; }
        public Duration getApprovalLatencyCritical() { return approvalLatencyCritical; }
        public void setApprovalLatencyCritical(Duration approvalLatencyCritical) { this.approvalLatencyCritical = approvalLatencyCritical == null ? Duration.ofHours(2) : approvalLatencyCritical; }
        public double getActionFailureRatioWarning() { return Math.max(0.0d, Math.min(actionFailureRatioWarning, 1.0d)); }
        public void setActionFailureRatioWarning(double actionFailureRatioWarning) { this.actionFailureRatioWarning = actionFailureRatioWarning; }
        public double getActionFailureRatioCritical() { return Math.max(0.0d, Math.min(actionFailureRatioCritical, 1.0d)); }
        public void setActionFailureRatioCritical(double actionFailureRatioCritical) { this.actionFailureRatioCritical = actionFailureRatioCritical; }
    }

    public static class RecoveryMetrics {
        private boolean enabled = true;
        private Duration window = Duration.ofMinutes(15);
        private int historyLimit = 2000;
        private int runtimeFailureWarningThreshold = 5;
        private int runtimeFailureCriticalThreshold = 20;
        private int delayedRequeueWarningThreshold = 10;
        private int delayedRequeueCriticalThreshold = 50;
        private int deadLetterWarningThreshold = 1;
        private int deadLetterCriticalThreshold = 5;
        private int scannerFailureWarningThreshold = 1;
        private int scannerFailureCriticalThreshold = 5;
        private int recoveryExhaustedWarningThreshold = 1;
        private int recoveryExhaustedCriticalThreshold = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window == null ? Duration.ofMinutes(15) : window; }
        public int getHistoryLimit() { return Math.max(1, Math.min(historyLimit, 10000)); }
        public void setHistoryLimit(int historyLimit) { this.historyLimit = historyLimit; }
        public int getRuntimeFailureWarningThreshold() { return Math.max(0, runtimeFailureWarningThreshold); }
        public void setRuntimeFailureWarningThreshold(int runtimeFailureWarningThreshold) { this.runtimeFailureWarningThreshold = runtimeFailureWarningThreshold; }
        public int getRuntimeFailureCriticalThreshold() { return Math.max(0, runtimeFailureCriticalThreshold); }
        public void setRuntimeFailureCriticalThreshold(int runtimeFailureCriticalThreshold) { this.runtimeFailureCriticalThreshold = runtimeFailureCriticalThreshold; }
        public int getDelayedRequeueWarningThreshold() { return Math.max(0, delayedRequeueWarningThreshold); }
        public void setDelayedRequeueWarningThreshold(int delayedRequeueWarningThreshold) { this.delayedRequeueWarningThreshold = delayedRequeueWarningThreshold; }
        public int getDelayedRequeueCriticalThreshold() { return Math.max(0, delayedRequeueCriticalThreshold); }
        public void setDelayedRequeueCriticalThreshold(int delayedRequeueCriticalThreshold) { this.delayedRequeueCriticalThreshold = delayedRequeueCriticalThreshold; }
        public int getDeadLetterWarningThreshold() { return Math.max(0, deadLetterWarningThreshold); }
        public void setDeadLetterWarningThreshold(int deadLetterWarningThreshold) { this.deadLetterWarningThreshold = deadLetterWarningThreshold; }
        public int getDeadLetterCriticalThreshold() { return Math.max(0, deadLetterCriticalThreshold); }
        public void setDeadLetterCriticalThreshold(int deadLetterCriticalThreshold) { this.deadLetterCriticalThreshold = deadLetterCriticalThreshold; }
        public int getScannerFailureWarningThreshold() { return Math.max(0, scannerFailureWarningThreshold); }
        public void setScannerFailureWarningThreshold(int scannerFailureWarningThreshold) { this.scannerFailureWarningThreshold = scannerFailureWarningThreshold; }
        public int getScannerFailureCriticalThreshold() { return Math.max(0, scannerFailureCriticalThreshold); }
        public void setScannerFailureCriticalThreshold(int scannerFailureCriticalThreshold) { this.scannerFailureCriticalThreshold = scannerFailureCriticalThreshold; }
        public int getRecoveryExhaustedWarningThreshold() { return Math.max(0, recoveryExhaustedWarningThreshold); }
        public void setRecoveryExhaustedWarningThreshold(int recoveryExhaustedWarningThreshold) { this.recoveryExhaustedWarningThreshold = recoveryExhaustedWarningThreshold; }
        public int getRecoveryExhaustedCriticalThreshold() { return Math.max(0, recoveryExhaustedCriticalThreshold); }
        public void setRecoveryExhaustedCriticalThreshold(int recoveryExhaustedCriticalThreshold) { this.recoveryExhaustedCriticalThreshold = recoveryExhaustedCriticalThreshold; }
    }
}

