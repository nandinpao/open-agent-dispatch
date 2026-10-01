package com.opensocket.aievent.core.gateway.runtime;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for Gateway-node directory maintenance settings. */
@Component
public final class GatewayNodeRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final GatewayNodeRuntimeConfigurationView view;

    public GatewayNodeRuntimeConfigurationEffectiveValueResolver(GatewayNodeRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "GATEWAY_NODES"; }
    @Override public Set<String> supportedKeys() { return GatewayNodeRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        if (GatewayNodeRuntimeConfigurationView.LEASE_REAPER_FIXED_DELAY.equals(key)) {
            return view.leaseReaperDelay().toMillis();
        }
        throw new IllegalArgumentException("GATEWAY_NODE_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
    }
}
