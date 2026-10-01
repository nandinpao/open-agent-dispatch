package com.opensocket.aievent.core.action.executor;

import java.util.Set;
import org.springframework.stereotype.Component;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for Adapter Execution runtime tuning. */
@Component
public final class AdapterExecutorRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private static final Set<String> KEYS = Set.of(
            AdapterExecutorRuntimeConfigurationView.AUDIT_PAYLOAD_SNAPSHOT_ENABLED,
            AdapterExecutorRuntimeConfigurationView.AUTO_EXECUTE_INTERVAL,
            AdapterExecutorRuntimeConfigurationView.BATCH_SIZE,
            AdapterExecutorRuntimeConfigurationView.EXECUTION_TIMEOUT,
            AdapterExecutorRuntimeConfigurationView.INITIAL_BACKOFF,
            AdapterExecutorRuntimeConfigurationView.MAX_ATTEMPTS,
            AdapterExecutorRuntimeConfigurationView.MAX_BACKOFF,
            AdapterExecutorRuntimeConfigurationView.MARK_UNAVAILABLE,
            AdapterExecutorRuntimeConfigurationView.ISSUE_AUTO_EXECUTE,
            AdapterExecutorRuntimeConfigurationView.ISSUE_CONNECTOR_ENABLED,
            AdapterExecutorRuntimeConfigurationView.ISSUE_DEFAULT_VENDOR,
            AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_ENABLED,
            AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_DELAY,
            AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_BATCH,
            AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_MAX_ATTEMPTS,
            AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_INITIAL_BACKOFF,
            AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_MAX_BACKOFF,
            AdapterExecutorRuntimeConfigurationView.MCP_ENDPOINT_URL,
            AdapterExecutorRuntimeConfigurationView.MCP_EXECUTOR_NAME,
            AdapterExecutorRuntimeConfigurationView.MCP_HTTP_ENABLED,
            AdapterExecutorRuntimeConfigurationView.MCP_TIMEOUT,
            AdapterExecutorCircuitBreakerRuntimeKeys.ENABLED,
            AdapterExecutorCircuitBreakerRuntimeKeys.FAILURE_THRESHOLD,
            AdapterExecutorCircuitBreakerRuntimeKeys.OPEN_DURATION);
    private final AdapterExecutorRuntimeConfigurationView view;
    public AdapterExecutorRuntimeConfigurationEffectiveValueResolver(AdapterExecutorRuntimeConfigurationView view) { this.view = view; }
    @Override public String owner() { return "ADAPTER_EXECUTION"; }
    @Override public Set<String> supportedKeys() { return KEYS; }
    @Override public Object resolve(String key) {
        return switch (key) {
            case AdapterExecutorRuntimeConfigurationView.AUDIT_PAYLOAD_SNAPSHOT_ENABLED -> view.auditPayloadSnapshotEnabled();
            case AdapterExecutorRuntimeConfigurationView.AUTO_EXECUTE_INTERVAL -> view.autoExecuteInterval().toString();
            case AdapterExecutorRuntimeConfigurationView.BATCH_SIZE -> view.batchSize();
            case AdapterExecutorRuntimeConfigurationView.EXECUTION_TIMEOUT -> view.executionTimeout().toString();
            case AdapterExecutorRuntimeConfigurationView.INITIAL_BACKOFF -> view.initialBackoff().toString();
            case AdapterExecutorRuntimeConfigurationView.MAX_ATTEMPTS -> view.maxAttempts();
            case AdapterExecutorRuntimeConfigurationView.MAX_BACKOFF -> view.maxBackoff().toString();
            case AdapterExecutorRuntimeConfigurationView.MARK_UNAVAILABLE -> view.markUnavailableWhenNoExecutor();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_AUTO_EXECUTE -> view.issueAutoExecutePending();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_CONNECTOR_ENABLED -> view.issueConnectorRuntimeEnabled();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_DEFAULT_VENDOR -> view.issueDefaultVendor();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_ENABLED -> view.issueLinkProjectionReconciliationEnabled();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_DELAY -> view.issueLinkProjectionReconciliationDelay().toString();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_BATCH -> view.issueLinkProjectionBatchSize();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_MAX_ATTEMPTS -> view.issueLinkProjectionMaxAttempts();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_INITIAL_BACKOFF -> view.issueLinkProjectionInitialBackoff().toString();
            case AdapterExecutorRuntimeConfigurationView.ISSUE_RECONCILE_MAX_BACKOFF -> view.issueLinkProjectionMaxBackoff().toString();
            case AdapterExecutorRuntimeConfigurationView.MCP_ENDPOINT_URL -> view.mcpEndpointUrl();
            case AdapterExecutorRuntimeConfigurationView.MCP_EXECUTOR_NAME -> view.mcpExecutorName();
            case AdapterExecutorRuntimeConfigurationView.MCP_HTTP_ENABLED -> view.mcpHttpEnabled();
            case AdapterExecutorRuntimeConfigurationView.MCP_TIMEOUT -> view.mcpTimeout().toString();
            case AdapterExecutorCircuitBreakerRuntimeKeys.ENABLED -> view.circuitBreakerEnabled();
            case AdapterExecutorCircuitBreakerRuntimeKeys.FAILURE_THRESHOLD -> view.circuitBreakerFailureThreshold();
            case AdapterExecutorCircuitBreakerRuntimeKeys.OPEN_DURATION -> view.circuitBreakerOpenDuration().toString();
            default -> throw new IllegalArgumentException("ADAPTER_EXECUTION_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
