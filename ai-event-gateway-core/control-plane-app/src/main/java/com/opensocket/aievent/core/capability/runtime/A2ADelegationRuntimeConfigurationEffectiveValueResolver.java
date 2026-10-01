package com.opensocket.aievent.core.capability.runtime;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for A2A/delegation runtime settings. */
@Component
public final class A2ADelegationRuntimeConfigurationEffectiveValueResolver
        implements RuntimeConfigurationEffectiveValueResolver {
    private final A2ADelegationRuntimeConfigurationView view;

    public A2ADelegationRuntimeConfigurationEffectiveValueResolver(A2ADelegationRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "A2A_DELEGATION"; }
    @Override public Set<String> supportedKeys() { return A2ADelegationRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case A2ADelegationRuntimeConfigurationView.PUSH_MAX_BODY_BYTES -> view.pushMaxBodyBytes();
            case A2ADelegationRuntimeConfigurationView.RECONCILIATION_PIPELINE_MS -> view.reconciliationPipelineDelay().toMillis();
            case A2ADelegationRuntimeConfigurationView.ASYNC_ENABLED -> view.asyncEnabled();
            case A2ADelegationRuntimeConfigurationView.ASYNC_BATCH_SIZE -> view.asyncBatchSize();
            case A2ADelegationRuntimeConfigurationView.ASYNC_POLL_MS -> view.asyncPollDelay().toMillis();
            case A2ADelegationRuntimeConfigurationView.PUSH_HANDOFF_BATCH_SIZE -> view.pushHandoffBatchSize();
            case A2ADelegationRuntimeConfigurationView.PUSH_HANDOFF_MS -> view.pushHandoffDelay().toMillis();
            case A2ADelegationRuntimeConfigurationView.READ_ENABLED -> view.readEnabled();
            case A2ADelegationRuntimeConfigurationView.READ_BATCH_SIZE -> view.readBatchSize();
            case A2ADelegationRuntimeConfigurationView.READ_POLL_MS -> view.readPollDelay().toMillis();
            case A2ADelegationRuntimeConfigurationView.RESULT_RETRY_ENABLED -> view.resultRetryEnabled();
            case A2ADelegationRuntimeConfigurationView.RESULT_RETRY_BATCH_SIZE -> view.resultRetryBatchSize();
            case A2ADelegationRuntimeConfigurationView.RESULT_RETRY_POLL_MS -> view.resultRetryPollDelay().toMillis();
            case A2ADelegationRuntimeConfigurationView.MCP_READ_ENABLED -> view.mcpReadEnabled();
            case A2ADelegationRuntimeConfigurationView.MCP_READ_BATCH_SIZE -> view.mcpReadBatchSize();
            case A2ADelegationRuntimeConfigurationView.MCP_READ_POLL_MS -> view.mcpReadPollDelay().toMillis();
            case A2ADelegationRuntimeConfigurationView.PLAN_RUNTIME_BATCH_SIZE -> view.planRuntimeBatchSize();
            case A2ADelegationRuntimeConfigurationView.PLAN_RUNTIME_POLL_MS -> view.planRuntimePollDelay().toMillis();
            case A2ADelegationRuntimeConfigurationView.AUTHORITY_REVOCATION_SWEEP_MS -> view.authorityRevocationSweepDelay().toMillis();
            default -> throw new IllegalArgumentException("A2A_DELEGATION_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
