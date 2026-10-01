package com.opensocket.aievent.core.observability;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for Core observability and recovery-metrics settings. */
@Component
public final class CoreObservabilityRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final CoreObservabilityRuntimeConfigurationView view;

    public CoreObservabilityRuntimeConfigurationEffectiveValueResolver(CoreObservabilityRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "CORE_OBSERVABILITY"; }
    @Override public Set<String> supportedKeys() { return CoreObservabilityRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case CoreObservabilityRuntimeConfigurationView.ENABLED -> view.enabled();
            case CoreObservabilityRuntimeConfigurationView.BUSINESS_METRICS_ENABLED -> view.businessMetricsEnabled();
            case CoreObservabilityRuntimeConfigurationView.HEALTH_INDICATOR_ENABLED -> view.healthIndicatorEnabled();
            case CoreObservabilityRuntimeConfigurationView.INCLUDE_SITE_TAG -> view.includeSiteTag();
            case CoreObservabilityRuntimeConfigurationView.SUMMARY_SAMPLE_LIMIT -> view.summarySampleLimit();
            case CoreObservabilityRuntimeConfigurationView.SLOW_INTAKE_THRESHOLD -> view.slowIntakeThreshold().toString();
            case CoreObservabilityRuntimeConfigurationView.COMMON_TAG_COMPONENT -> view.commonTagComponent();
            case CoreObservabilityRuntimeConfigurationView.RECOVERY_ENABLED -> view.recoveryEnabled();
            case CoreObservabilityRuntimeConfigurationView.RECOVERY_WINDOW -> view.recoveryWindow().toString();
            case CoreObservabilityRuntimeConfigurationView.RECOVERY_HISTORY_LIMIT -> view.recoveryHistoryLimit();
            case CoreObservabilityRuntimeConfigurationView.RUNTIME_FAILURE_WARNING -> view.runtimeFailureWarningThreshold();
            case CoreObservabilityRuntimeConfigurationView.RUNTIME_FAILURE_CRITICAL -> view.runtimeFailureCriticalThreshold();
            case CoreObservabilityRuntimeConfigurationView.DELAYED_REQUEUE_WARNING -> view.delayedRequeueWarningThreshold();
            case CoreObservabilityRuntimeConfigurationView.DELAYED_REQUEUE_CRITICAL -> view.delayedRequeueCriticalThreshold();
            case CoreObservabilityRuntimeConfigurationView.DEAD_LETTER_WARNING -> view.deadLetterWarningThreshold();
            case CoreObservabilityRuntimeConfigurationView.DEAD_LETTER_CRITICAL -> view.deadLetterCriticalThreshold();
            case CoreObservabilityRuntimeConfigurationView.SCANNER_FAILURE_WARNING -> view.scannerFailureWarningThreshold();
            case CoreObservabilityRuntimeConfigurationView.SCANNER_FAILURE_CRITICAL -> view.scannerFailureCriticalThreshold();
            case CoreObservabilityRuntimeConfigurationView.RECOVERY_EXHAUSTED_WARNING -> view.recoveryExhaustedWarningThreshold();
            case CoreObservabilityRuntimeConfigurationView.RECOVERY_EXHAUSTED_CRITICAL -> view.recoveryExhaustedCriticalThreshold();
            default -> throw new IllegalArgumentException("CORE_OBSERVABILITY_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
