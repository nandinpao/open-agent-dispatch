package com.opensocket.aievent.core.action.executor;

import com.opensocket.aievent.core.action.AdapterAction;

public interface AdapterActionExecutor {
    String name();
    boolean supports(AdapterAction action);
    AdapterExecutionResult execute(AdapterAction action);
}
