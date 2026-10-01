package com.opensocket.aievent.core.task;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for Task Dispatch Recovery tuning. */
@Component
public final class TaskDispatchRecoveryRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final TaskDispatchRecoveryRuntimeConfigurationView view;

    public TaskDispatchRecoveryRuntimeConfigurationEffectiveValueResolver(TaskDispatchRecoveryRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "TASK_DISPATCH_RECOVERY"; }
    @Override public Set<String> supportedKeys() { return TaskDispatchRecoveryRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case TaskDispatchRecoveryRuntimeConfigurationView.ENABLED -> view.enabled();
            case TaskDispatchRecoveryRuntimeConfigurationView.SCANNER_ENABLED -> view.scannerEnabled();
            case TaskDispatchRecoveryRuntimeConfigurationView.INTERVAL_MS -> view.scanInterval().toMillis();
            case TaskDispatchRecoveryRuntimeConfigurationView.MAX_BATCH_SIZE -> view.maxBatchSize();
            case TaskDispatchRecoveryRuntimeConfigurationView.MAX_ATTEMPTS -> view.maxAttempts();
            case TaskDispatchRecoveryRuntimeConfigurationView.INITIAL_DELAY -> view.initialDelay().toString();
            case TaskDispatchRecoveryRuntimeConfigurationView.MAX_DELAY -> view.maxDelay().toString();
            case TaskDispatchRecoveryRuntimeConfigurationView.CLAIM_LEASE -> view.claimLease().toString();
            case TaskDispatchRecoveryRuntimeConfigurationView.WORKER_ID -> view.workerId();
            default -> throw new IllegalArgumentException("TASK_DISPATCH_RECOVERY_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
