package com.opensocket.aievent.core.action;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

@Component
public final class AdapterActionMcpRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private static final Set<String> KEYS=AdapterActionMcpRuntimeConfigurationView.ALL;
    private final AdapterActionMcpRuntimeConfigurationView view;
    public AdapterActionMcpRuntimeConfigurationEffectiveValueResolver(AdapterActionMcpRuntimeConfigurationView view){this.view=view;}
    @Override public String owner(){return "ADAPTER_ACTION_MCP";}
    @Override public Set<String> supportedKeys(){return KEYS;}
    @Override public Object resolve(String key){return switch(key){
        case AdapterActionMcpRuntimeConfigurationView.ENABLED -> view.enabled();
        case AdapterActionMcpRuntimeConfigurationView.RUN_ON_COMPLETED -> view.runOnCompletedTask();
        case AdapterActionMcpRuntimeConfigurationView.RUN_ON_FAILED -> view.runOnFailedTask();
        case AdapterActionMcpRuntimeConfigurationView.ONE_PER_TASK -> view.onePerTask();
        case AdapterActionMcpRuntimeConfigurationView.ADAPTER_NAME -> view.adapterName();
        default -> throw new IllegalArgumentException("ADAPTER_ACTION_MCP_RUNTIME_CONFIG_KEY_NOT_OWNED key="+key);
    };}
}
