package com.opensocket.aievent.core.lifecycle;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for V41-C3B1 Task lifecycle configuration. */
@Component
public final class TaskLifecycleRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final TaskLifecycleRuntimeConfigurationView view;

    public TaskLifecycleRuntimeConfigurationEffectiveValueResolver(TaskLifecycleRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "TASK"; }
    @Override public Set<String> supportedKeys() { return TaskLifecycleRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case TaskLifecycleRuntimeConfigurationView.TIMEOUT_ENABLED -> view.timeoutEnabled();
            case TaskLifecycleRuntimeConfigurationView.AUTO_REASSIGN_ENABLED -> view.autoReassignEnabled();
            case TaskLifecycleRuntimeConfigurationView.SCAN_INTERVAL_MS -> view.scanInterval().toMillis();
            case TaskLifecycleRuntimeConfigurationView.CREATED_TIMEOUT -> view.createdTimeout().toString();
            case TaskLifecycleRuntimeConfigurationView.ASSIGNED_TIMEOUT -> view.assignedTimeout().toString();
            case TaskLifecycleRuntimeConfigurationView.DISPATCHED_TIMEOUT -> view.dispatchedTimeout().toString();
            case TaskLifecycleRuntimeConfigurationView.RUNNING_TIMEOUT -> view.runningTimeout().toString();
            case TaskLifecycleRuntimeConfigurationView.MAX_REASSIGNMENTS -> view.maxReassignments();
            case TaskLifecycleRuntimeConfigurationView.MAX_BATCH_SIZE -> view.maxBatchSize();
            default -> throw new IllegalArgumentException("TASK_LIFECYCLE_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
