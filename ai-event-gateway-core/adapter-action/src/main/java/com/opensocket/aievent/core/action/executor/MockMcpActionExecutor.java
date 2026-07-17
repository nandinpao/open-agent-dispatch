package com.opensocket.aievent.core.action.executor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterType;

@Component
@ConditionalOnProperty(prefix = "adapter-executor.mock", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MockMcpActionExecutor implements AdapterActionExecutor {
    private final AdapterActionExecutionProperties properties;

    public MockMcpActionExecutor(AdapterActionExecutionProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return properties.getMock().getMcpExecutorName();
    }

    @Override
    public boolean supports(AdapterAction action) {
        return action != null && action.getAdapterType() == AdapterType.MCP;
    }

    @Override
    public AdapterExecutionResult execute(AdapterAction action) {
        if (properties.getMock().isForceFailure()) {
            return AdapterExecutionResult.failed(name(), "Mock MCP executor forced failure");
        }
        return AdapterExecutionResult.success(name(), "mock-mcp-response:" + action.getActionId());
    }
}
