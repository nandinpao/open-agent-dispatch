package com.opensocket.aievent.core.dispatch;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for all V41-C3R2B Dispatch runtime settings. */
@Component
public final class DispatchRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final DispatchRuntimeConfigurationView view;

    public DispatchRuntimeConfigurationEffectiveValueResolver(DispatchRuntimeConfigurationView view) { this.view = view; }
    @Override public String owner() { return "DISPATCH"; }
    @Override public Set<String> supportedKeys() { return DispatchRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case DispatchRuntimeConfigurationView.CLAIM_LEASE -> view.claimLease().toString();
            case DispatchRuntimeConfigurationView.CLIENT_AUTO_EXECUTE_INTERVAL_MS -> view.autoExecuteInterval().toMillis();
            case DispatchRuntimeConfigurationView.CLIENT_CONNECT_TIMEOUT -> view.connectTimeout().toString();
            case DispatchRuntimeConfigurationView.CLIENT_DEFAULT_GATEWAY_BASE_URL -> view.defaultGatewayBaseUrl();
            case DispatchRuntimeConfigurationView.CLIENT_GATEWAY_TPE_001 -> view.gatewayBaseUrls().get("gateway-tpe-001");
            case DispatchRuntimeConfigurationView.CLIENT_GATEWAY_TYN_001 -> view.gatewayBaseUrls().get("gateway-tyn-001");
            case DispatchRuntimeConfigurationView.CLIENT_GATEWAY_TNN_001 -> view.gatewayBaseUrls().get("gateway-tnn-001");
            case DispatchRuntimeConfigurationView.CLIENT_MAX_BATCH_SIZE -> view.maxBatchSize();
            case DispatchRuntimeConfigurationView.CLIENT_REQUEST_TIMEOUT -> view.requestTimeout().toString();
            case DispatchRuntimeConfigurationView.EXECUTION_POLICY -> view.executionPolicy().name();
            case DispatchRuntimeConfigurationView.GATEWAY_DISPATCH_PATH -> view.gatewayDispatchPath();
            case DispatchRuntimeConfigurationView.REQUIRE_ASSIGNABLE_AGENT -> view.requireAssignableAgent();
            case DispatchRuntimeConfigurationView.REVIEW_MODE -> view.reviewMode().name();
            case DispatchRuntimeConfigurationView.SOURCE_NODE_ID -> view.sourceNodeId();
            case DispatchRuntimeConfigurationView.WORKER_ID -> view.workerId();
            case DispatchRuntimeConfigurationView.RETRY_ENABLED -> view.retryEnabled();
            case DispatchRuntimeConfigurationView.RETRY_MAX_ATTEMPTS -> view.maxAttempts();
            case DispatchRuntimeConfigurationView.RETRY_INITIAL_BACKOFF -> view.initialBackoff().toString();
            case DispatchRuntimeConfigurationView.RETRY_MAX_BACKOFF -> view.maxBackoff().toString();
            case DispatchRuntimeConfigurationView.RETRY_JITTER_PERCENT -> view.jitterPercent();
            case DispatchRuntimeConfigurationView.FAILURE_REQUEUE_ENABLED -> view.failureRequeueEnabled();
            case DispatchRuntimeConfigurationView.FAILURE_REQUEUE_MAX_REASSIGNMENTS -> view.failureRequeueMaxReassignments();
            case DispatchRuntimeConfigurationView.RUNTIME_INITIAL_BACKOFF -> view.runtimeInitialBackoff().toString();
            case DispatchRuntimeConfigurationView.RUNTIME_MAX_BACKOFF -> view.runtimeMaxBackoff().toString();
            case DispatchRuntimeConfigurationView.RUNTIME_JITTER_PERCENT -> view.runtimeJitterPercent();
            case DispatchRuntimeConfigurationView.POISON_AGENT_FAILURE_THRESHOLD -> view.poisonAgentFailureThreshold();
            default -> throw new IllegalArgumentException("DISPATCH_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
