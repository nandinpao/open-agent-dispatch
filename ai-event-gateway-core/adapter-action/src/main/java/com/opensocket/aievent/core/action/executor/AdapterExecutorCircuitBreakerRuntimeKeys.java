package com.opensocket.aievent.core.action.executor;

import java.util.Set;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;

/** Stable V40-9D/HF1 identity for the Adapter Executor circuit-breaker migration unit. */
public final class AdapterExecutorCircuitBreakerRuntimeKeys {
    public static final String SET_KEY = RuntimeConfigurationSetKeys.ADAPTER_EXECUTION_SYSTEM;
    public static final String ENABLED = "adapter-executor.circuit-breaker.enabled";
    public static final String FAILURE_THRESHOLD = "adapter-executor.circuit-breaker.failure-threshold";
    public static final String OPEN_DURATION = "adapter-executor.circuit-breaker.open-duration";
    public static final Set<String> ALL = Set.of(ENABLED, FAILURE_THRESHOLD, OPEN_DURATION);

    private AdapterExecutorCircuitBreakerRuntimeKeys() {}
}
