package com.opensocket.aievent.core.taskauthority.runtime;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for Task authority/finalization runtime settings. */
@Component
public final class TaskAuthorityRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final TaskAuthorityRuntimeConfigurationView view;

    public TaskAuthorityRuntimeConfigurationEffectiveValueResolver(TaskAuthorityRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "TASK_AUTHORITY"; }
    @Override public Set<String> supportedKeys() { return TaskAuthorityRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case TaskAuthorityRuntimeConfigurationView.CONDITIONS_ENABLED -> view.conditionsEnabled();
            case TaskAuthorityRuntimeConfigurationView.CONDITIONS_BATCH_SIZE -> view.conditionsBatchSize();
            case TaskAuthorityRuntimeConfigurationView.CONDITIONS_POLL_MS -> view.conditionsPollDelay().toMillis();
            case TaskAuthorityRuntimeConfigurationView.FINALIZATION_ENABLED -> view.finalizationEnabled();
            case TaskAuthorityRuntimeConfigurationView.FINALIZATION_BATCH_SIZE -> view.finalizationBatchSize();
            case TaskAuthorityRuntimeConfigurationView.FINALIZATION_CLAIM_SECONDS -> view.finalizationClaimSeconds();
            case TaskAuthorityRuntimeConfigurationView.FINALIZATION_MAX_ATTEMPTS -> view.finalizationMaxAttempts();
            case TaskAuthorityRuntimeConfigurationView.FINALIZATION_POLL_MS -> view.finalizationPollDelay().toMillis();
            case TaskAuthorityRuntimeConfigurationView.PROJECTION_ENABLED -> view.projectionEnabled();
            case TaskAuthorityRuntimeConfigurationView.PROJECTION_BATCH_SIZE -> view.projectionBatchSize();
            case TaskAuthorityRuntimeConfigurationView.PROJECTION_CLAIM_SECONDS -> view.projectionClaimSeconds();
            case TaskAuthorityRuntimeConfigurationView.PROJECTION_MAX_ATTEMPTS -> view.projectionMaxAttempts();
            case TaskAuthorityRuntimeConfigurationView.PROJECTION_POLL_MS -> view.projectionPollDelay().toMillis();
            default -> throw new IllegalArgumentException("TASK_AUTHORITY_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
