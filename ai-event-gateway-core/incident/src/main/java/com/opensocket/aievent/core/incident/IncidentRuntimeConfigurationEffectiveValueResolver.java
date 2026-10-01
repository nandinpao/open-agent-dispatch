package com.opensocket.aievent.core.incident;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for Incident lifecycle/reopen settings. */
@Component
public final class IncidentRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final IncidentRuntimeConfigurationView view;

    public IncidentRuntimeConfigurationEffectiveValueResolver(IncidentRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "INCIDENT"; }
    @Override public Set<String> supportedKeys() { return IncidentRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case IncidentRuntimeConfigurationView.SCAN_INTERVAL_MS -> view.scanInterval().toMillis();
            case IncidentRuntimeConfigurationView.INACTIVE_THRESHOLD -> view.inactiveThreshold().toString();
            case IncidentRuntimeConfigurationView.MAX_BATCH_SIZE -> view.maxBatchSize();
            case IncidentRuntimeConfigurationView.REOPEN_POLICY -> view.reopenPolicy().name();
            case IncidentRuntimeConfigurationView.REOPEN_WINDOW -> view.reopenWindow().toString();
            default -> throw new IllegalArgumentException("INCIDENT_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
