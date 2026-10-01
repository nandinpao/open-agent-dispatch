package com.opensocket.aievent.core.agent.runtime;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class AgentDirectoryRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final AgentDirectoryRuntimeConfigurationView view;

    public AgentDirectoryRuntimeConfigurationEffectiveValueResolver(AgentDirectoryRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override
    public String owner() {
        return "AGENT_DIRECTORY";
    }

    @Override
    public Set<String> supportedKeys() {
        return AgentDirectoryRuntimeConfigurationView.ALL;
    }

    @Override
    public Object resolve(String key) {
        if (AgentDirectoryRuntimeConfigurationView.LEASE_REAPER_FIXED_DELAY.equals(key)) {
            return view.leaseReaperFixedDelay().toString();
        }
        return null;
    }
}
