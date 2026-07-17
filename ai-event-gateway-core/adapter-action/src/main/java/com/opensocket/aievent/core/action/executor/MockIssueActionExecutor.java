package com.opensocket.aievent.core.action.executor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.action.AdapterAction;
import com.opensocket.aievent.core.action.AdapterType;

@Component
@ConditionalOnProperty(prefix = "adapter-executor.mock", name = "legacy-issue-enabled", havingValue = "true")
public class MockIssueActionExecutor implements AdapterActionExecutor {
    private final AdapterActionExecutionProperties properties;

    public MockIssueActionExecutor(AdapterActionExecutionProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return properties.getMock().getIssueExecutorName();
    }

    @Override
    public boolean supports(AdapterAction action) {
        return action != null && action.getAdapterType() == AdapterType.ISSUE_TRACKING;
    }

    @Override
    public AdapterExecutionResult execute(AdapterAction action) {
        if (properties.getMock().isForceFailure()) {
            return AdapterExecutionResult.failed(name(), "Mock issue executor forced failure");
        }
        return AdapterExecutionResult.success(name(), "mock-issue-response:" + action.getActionId());
    }
}
