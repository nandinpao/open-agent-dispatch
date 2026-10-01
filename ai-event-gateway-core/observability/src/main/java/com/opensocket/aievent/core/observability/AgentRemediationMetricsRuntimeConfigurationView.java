package com.opensocket.aievent.core.observability;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;

/**
 * Runtime view for the one remediation-metrics setting that currently changes execution behavior.
 * Alert thresholds remain Domain Config until an alert evaluator actually consumes them.
 */
@Component
public final class AgentRemediationMetricsRuntimeConfigurationView {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.CORE_SYSTEM;
    public static final String ENABLED = "core.observability.remediation-workflow-metrics.enabled";
    public static final Set<String> ALL = Set.of(ENABLED);

    private final ObservabilityProperties startup;
    private final RuntimeConfigurationSnapshotValues values;
    private final RuntimeConfigurationAuthorityRegistry authority;

    public AgentRemediationMetricsRuntimeConfigurationView(
            ObservabilityProperties startup,
            RuntimeConfigurationSnapshotValues values,
            RuntimeConfigurationAuthorityRegistry authority) {
        this.startup = startup == null ? new ObservabilityProperties() : startup;
        this.values = values;
        this.authority = authority;
    }

    public boolean enabled() {
        if (runtimeRequired()) {
            requireSnapshot();
            return values.booleanValue(SET_KEY, ENABLED).orElseThrow(() -> incomplete(ENABLED));
        }
        return values.booleanValue(SET_KEY, ENABLED)
                .orElse(startup.getRemediationWorkflowMetrics().isEnabled());
    }

    private boolean runtimeRequired() {
        return authority != null && authority.isRuntimeAuthoritative(ENABLED);
    }

    private void requireSnapshot() {
        if (!values.hasSnapshot(SET_KEY)) throw incomplete(ENABLED);
        values.requireKeys(SET_KEY, ALL);
    }

    private static IllegalStateException incomplete(String key) {
        return new IllegalStateException("CONFIGURATION_INCOMPLETE setKey=" + SET_KEY + " key=" + key);
    }
}
