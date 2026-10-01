package com.opensocket.aievent.core.observability;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class AgentRemediationMetricsRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final AgentRemediationMetricsRuntimeConfigurationView view;

    public AgentRemediationMetricsRuntimeConfigurationEffectiveValueResolver(
            AgentRemediationMetricsRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override
    public String owner() {
        return "CORE_OBSERVABILITY_REMEDIATION";
    }

    @Override
    public Set<String> supportedKeys() {
        return AgentRemediationMetricsRuntimeConfigurationView.ALL;
    }

    @Override
    public Object resolve(String key) {
        if (AgentRemediationMetricsRuntimeConfigurationView.ENABLED.equals(key)) {
            return view.enabled();
        }
        return null;
    }
}
