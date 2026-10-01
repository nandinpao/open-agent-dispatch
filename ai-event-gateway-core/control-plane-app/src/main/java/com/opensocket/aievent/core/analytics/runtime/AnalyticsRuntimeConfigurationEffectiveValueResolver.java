package com.opensocket.aievent.core.analytics.runtime;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for analytics runtime settings. */
@Component
public final class AnalyticsRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final AnalyticsRuntimeConfigurationView view;

    public AnalyticsRuntimeConfigurationEffectiveValueResolver(AnalyticsRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "ANALYTICS"; }
    @Override public Set<String> supportedKeys() { return AnalyticsRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case AnalyticsRuntimeConfigurationView.ENABLED -> view.enabled();
            case AnalyticsRuntimeConfigurationView.BATCH -> view.batchSize();
            case AnalyticsRuntimeConfigurationView.POLL -> view.pollDelay().toMillis();
            case AnalyticsRuntimeConfigurationView.ROLLUP -> view.rollupBatchSize();
            default -> throw new IllegalArgumentException("ANALYTICS_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
