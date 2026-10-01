package com.opensocket.aievent.core.integration.issue.projection;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value resolver for Issue Projection runtime policy. */
@Component
public final class IssueProjectionRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private static final Set<String> KEYS = Set.of(
            IssueProjectionRuntimeConfigurationView.ENABLED,
            IssueProjectionRuntimeConfigurationView.MAX_ATTEMPTS,
            IssueProjectionRuntimeConfigurationView.RECONCILE_BATCH_SIZE,
            IssueProjectionRuntimeConfigurationView.RETRY_DELAY_SECONDS,
            IssueProjectionRuntimeConfigurationView.RECONCILE_DELAY_MS);
    private final IssueProjectionRuntimeConfigurationView view;

    public IssueProjectionRuntimeConfigurationEffectiveValueResolver(IssueProjectionRuntimeConfigurationView view) { this.view = view; }
    @Override public String owner() { return "ISSUE_PROJECTION"; }
    @Override public Set<String> supportedKeys() { return KEYS; }
    @Override public Object resolve(String key) {
        return switch (key) {
            case IssueProjectionRuntimeConfigurationView.ENABLED -> view.enabled();
            case IssueProjectionRuntimeConfigurationView.MAX_ATTEMPTS -> view.maxAttempts();
            case IssueProjectionRuntimeConfigurationView.RECONCILE_BATCH_SIZE -> view.reconcileBatchSize();
            case IssueProjectionRuntimeConfigurationView.RETRY_DELAY_SECONDS -> view.retryDelaySeconds();
            case IssueProjectionRuntimeConfigurationView.RECONCILE_DELAY_MS -> view.reconcileDelay().toMillis();
            default -> throw unsupported(key);
        };
    }
    private IllegalArgumentException unsupported(String key) { return new IllegalArgumentException("ISSUE_PROJECTION_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key); }
}
