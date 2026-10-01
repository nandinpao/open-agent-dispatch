package com.opensocket.aievent.core.observability;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;

/** C3R2K typed runtime view for active Core observability and recovery-metrics controls. */
@Component
public final class CoreObservabilityRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String ENABLED = "core.observability.enabled";
    public static final String BUSINESS_METRICS_ENABLED = "core.observability.business-metrics-enabled";
    public static final String HEALTH_INDICATOR_ENABLED = "core.observability.health-indicator-enabled";
    public static final String INCLUDE_SITE_TAG = "core.observability.include-site-tag";
    public static final String SUMMARY_SAMPLE_LIMIT = "core.observability.summary-sample-limit";
    public static final String SLOW_INTAKE_THRESHOLD = "core.observability.slow-intake-threshold";
    public static final String COMMON_TAG_COMPONENT = "core.observability.common-tags.component";
    public static final String RECOVERY_ENABLED = "core.observability.recovery-metrics.enabled";
    public static final String RECOVERY_WINDOW = "core.observability.recovery-metrics.window";
    public static final String RECOVERY_HISTORY_LIMIT = "core.observability.recovery-metrics.history-limit";
    public static final String RUNTIME_FAILURE_WARNING = "core.observability.recovery-metrics.runtime-failure-warning-threshold";
    public static final String RUNTIME_FAILURE_CRITICAL = "core.observability.recovery-metrics.runtime-failure-critical-threshold";
    public static final String DELAYED_REQUEUE_WARNING = "core.observability.recovery-metrics.delayed-requeue-warning-threshold";
    public static final String DELAYED_REQUEUE_CRITICAL = "core.observability.recovery-metrics.delayed-requeue-critical-threshold";
    public static final String DEAD_LETTER_WARNING = "core.observability.recovery-metrics.dead-letter-warning-threshold";
    public static final String DEAD_LETTER_CRITICAL = "core.observability.recovery-metrics.dead-letter-critical-threshold";
    public static final String SCANNER_FAILURE_WARNING = "core.observability.recovery-metrics.scanner-failure-warning-threshold";
    public static final String SCANNER_FAILURE_CRITICAL = "core.observability.recovery-metrics.scanner-failure-critical-threshold";
    public static final String RECOVERY_EXHAUSTED_WARNING = "core.observability.recovery-metrics.recovery-exhausted-warning-threshold";
    public static final String RECOVERY_EXHAUSTED_CRITICAL = "core.observability.recovery-metrics.recovery-exhausted-critical-threshold";

    public static final Set<String> ALL = Set.of(
            ENABLED, BUSINESS_METRICS_ENABLED, HEALTH_INDICATOR_ENABLED, INCLUDE_SITE_TAG,
            SUMMARY_SAMPLE_LIMIT, SLOW_INTAKE_THRESHOLD, COMMON_TAG_COMPONENT,
            RECOVERY_ENABLED, RECOVERY_WINDOW, RECOVERY_HISTORY_LIMIT,
            RUNTIME_FAILURE_WARNING, RUNTIME_FAILURE_CRITICAL,
            DELAYED_REQUEUE_WARNING, DELAYED_REQUEUE_CRITICAL,
            DEAD_LETTER_WARNING, DEAD_LETTER_CRITICAL,
            SCANNER_FAILURE_WARNING, SCANNER_FAILURE_CRITICAL,
            RECOVERY_EXHAUSTED_WARNING, RECOVERY_EXHAUSTED_CRITICAL);

    private final ObservabilityProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public CoreObservabilityRuntimeConfigurationView(ObservabilityProperties startup,
                                                      RuntimeConfigurationSnapshotValues values,
                                                      RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup == null ? new ObservabilityProperties() : startup;
        this.values = values;
        this.authority = authority;
    }

    public boolean runtimeBacked() { return values.hasSnapshot(SET_KEY); }
    public String revisionId() { return values.revisionId(SET_KEY).orElse(null); }
    public boolean enabled() { return bool(ENABLED, startup.isEnabled()); }
    public boolean businessMetricsEnabled() { return bool(BUSINESS_METRICS_ENABLED, startup.isBusinessMetricsEnabled()); }
    public boolean healthIndicatorEnabled() { return bool(HEALTH_INDICATOR_ENABLED, startup.isHealthIndicatorEnabled()); }
    public boolean includeSiteTag() { return bool(INCLUDE_SITE_TAG, startup.isIncludeSiteTag()); }
    public int summarySampleLimit() { return boundedInt(SUMMARY_SAMPLE_LIMIT, startup.getSummarySampleLimit(), 1, 5000); }
    public Duration slowIntakeThreshold() { return boundedDuration(SLOW_INTAKE_THRESHOLD, startup.getSlowIntakeThreshold(), Duration.ofMillis(1), Duration.ofHours(1)); }
    public String commonTagComponent() { return boundedText(COMMON_TAG_COMPONENT, text(COMMON_TAG_COMPONENT, startup.getCommonTags().getOrDefault("component", "core")), 1, 128); }
    public Map<String,String> commonTags() {
        Map<String,String> result = new LinkedHashMap<>(startup.getCommonTags());
        result.put("component", commonTagComponent());
        return result;
    }

    public boolean recoveryEnabled() { return bool(RECOVERY_ENABLED, startup.getRecoveryMetrics().isEnabled()); }
    public Duration recoveryWindow() { return boundedDuration(RECOVERY_WINDOW, startup.getRecoveryMetrics().getWindow(), Duration.ofMinutes(1), Duration.ofHours(24)); }
    public int recoveryHistoryLimit() { return boundedInt(RECOVERY_HISTORY_LIMIT, startup.getRecoveryMetrics().getHistoryLimit(), 1, 10000); }
    public int runtimeFailureWarningThreshold() { return nonNegative(RUNTIME_FAILURE_WARNING, startup.getRecoveryMetrics().getRuntimeFailureWarningThreshold()); }
    public int runtimeFailureCriticalThreshold() { return criticalAtLeastWarning(RUNTIME_FAILURE_CRITICAL, startup.getRecoveryMetrics().getRuntimeFailureCriticalThreshold(), runtimeFailureWarningThreshold()); }
    public int delayedRequeueWarningThreshold() { return nonNegative(DELAYED_REQUEUE_WARNING, startup.getRecoveryMetrics().getDelayedRequeueWarningThreshold()); }
    public int delayedRequeueCriticalThreshold() { return criticalAtLeastWarning(DELAYED_REQUEUE_CRITICAL, startup.getRecoveryMetrics().getDelayedRequeueCriticalThreshold(), delayedRequeueWarningThreshold()); }
    public int deadLetterWarningThreshold() { return nonNegative(DEAD_LETTER_WARNING, startup.getRecoveryMetrics().getDeadLetterWarningThreshold()); }
    public int deadLetterCriticalThreshold() { return criticalAtLeastWarning(DEAD_LETTER_CRITICAL, startup.getRecoveryMetrics().getDeadLetterCriticalThreshold(), deadLetterWarningThreshold()); }
    public int scannerFailureWarningThreshold() { return nonNegative(SCANNER_FAILURE_WARNING, startup.getRecoveryMetrics().getScannerFailureWarningThreshold()); }
    public int scannerFailureCriticalThreshold() { return criticalAtLeastWarning(SCANNER_FAILURE_CRITICAL, startup.getRecoveryMetrics().getScannerFailureCriticalThreshold(), scannerFailureWarningThreshold()); }
    public int recoveryExhaustedWarningThreshold() { return nonNegative(RECOVERY_EXHAUSTED_WARNING, startup.getRecoveryMetrics().getRecoveryExhaustedWarningThreshold()); }
    public int recoveryExhaustedCriticalThreshold() { return criticalAtLeastWarning(RECOVERY_EXHAUSTED_CRITICAL, startup.getRecoveryMetrics().getRecoveryExhaustedCriticalThreshold(), recoveryExhaustedWarningThreshold()); }

    private boolean bool(String key, boolean fallback) {
        if (required(key)) { require(key); return values.booleanValue(SET_KEY,key).orElseThrow(() -> incomplete(key)); }
        return values.booleanValue(SET_KEY,key).orElse(fallback);
    }
    private int integer(String key, int fallback) {
        if (required(key)) { require(key); return values.integerValue(SET_KEY,key).orElseThrow(() -> incomplete(key)); }
        return values.integerValue(SET_KEY,key).orElse(fallback);
    }
    private String text(String key, String fallback) {
        if (required(key)) { require(key); return values.textValue(SET_KEY,key).orElseThrow(() -> incomplete(key)); }
        return values.textValue(SET_KEY,key).orElse(fallback == null ? "" : fallback);
    }
    private Duration duration(String key, Duration fallback) {
        if (required(key)) { require(key); return values.durationValue(SET_KEY,key).orElseThrow(() -> incomplete(key)); }
        return values.durationValue(SET_KEY,key).orElse(fallback);
    }
    private int nonNegative(String key, int fallback) { int v=integer(key,fallback); if(v<0) throw invalid(key); return v; }
    private int criticalAtLeastWarning(String key, int fallback, int warning) { int v=nonNegative(key,fallback); if(v<warning) throw invalid(key); return v; }
    private static String boundedText(String key, String value, int minLength, int maxLength) { if(value==null||value.length()<minLength||value.length()>maxLength) throw invalid(key); return value; }
    private int boundedInt(String key,int fallback,int min,int max) { int v=integer(key,fallback); if(v<min||v>max) throw invalid(key); return v; }
    private Duration boundedDuration(String key,Duration fallback,Duration min,Duration max) { Duration v=duration(key,fallback); if(v==null||v.compareTo(min)<0||v.compareTo(max)>0) throw invalid(key); return v; }
    private boolean required(String key) { return authority != null && authority.isRuntimeAuthoritative(key); }
    private void require(String key) { if(!values.hasSnapshot(SET_KEY)) throw incomplete(key); values.requireKeys(SET_KEY,ALL); }
    private static IllegalStateException invalid(String key) { return new IllegalStateException("RUNTIME_CONFIG_VALIDATION_FAILED key="+key); }
    private static IllegalStateException incomplete(String key) { return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey="+SET_KEY+" key="+key); }
}
