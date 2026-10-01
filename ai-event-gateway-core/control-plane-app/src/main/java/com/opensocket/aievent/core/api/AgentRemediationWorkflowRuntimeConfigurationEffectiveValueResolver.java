package com.opensocket.aievent.core.api;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for V41-C3B1 remediation workflow configuration. */
@Component
public final class AgentRemediationWorkflowRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final AgentRemediationWorkflowRuntimeConfigurationView view;

    public AgentRemediationWorkflowRuntimeConfigurationEffectiveValueResolver(AgentRemediationWorkflowRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "AGENT_REMEDIATION"; }
    @Override public Set<String> supportedKeys() { return AgentRemediationWorkflowRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case AgentRemediationWorkflowRuntimeConfigurationView.ENABLED -> view.enabled();
            case AgentRemediationWorkflowRuntimeConfigurationView.FIXED_DELAY_MS -> view.fixedDelay().toMillis();
            case AgentRemediationWorkflowRuntimeConfigurationView.INITIAL_DELAY_MS -> view.initialDelay().toMillis();
            case AgentRemediationWorkflowRuntimeConfigurationView.LIMIT -> view.limit();
            default -> throw new IllegalArgumentException("AGENT_REMEDIATION_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
