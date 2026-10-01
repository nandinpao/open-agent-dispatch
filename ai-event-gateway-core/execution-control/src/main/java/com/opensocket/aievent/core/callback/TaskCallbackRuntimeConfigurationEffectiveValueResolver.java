package com.opensocket.aievent.core.callback;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;

/** Domain-owned effective-value projection for Task callback runtime configuration. */
@Component
public final class TaskCallbackRuntimeConfigurationEffectiveValueResolver implements RuntimeConfigurationEffectiveValueResolver {
    private final TaskCallbackRuntimeConfigurationView view;

    public TaskCallbackRuntimeConfigurationEffectiveValueResolver(TaskCallbackRuntimeConfigurationView view) {
        this.view = view;
    }

    @Override public String owner() { return "TASK_CALLBACK"; }
    @Override public Set<String> supportedKeys() { return TaskCallbackRuntimeConfigurationView.ALL; }

    @Override
    public Object resolve(String key) {
        return switch (key) {
            case TaskCallbackRuntimeConfigurationView.IDEMPOTENCY_ENABLED -> view.idempotencyEnabled();
            case TaskCallbackRuntimeConfigurationView.REPLAY_PROTECTION_ENABLED -> view.replayProtectionEnabled();
            case TaskCallbackRuntimeConfigurationView.REJECT_REPLAY_MISMATCH -> view.rejectCallbackIdReplayMismatch();
            case TaskCallbackRuntimeConfigurationView.ALLOW_MISSING_DISPATCH_REQUEST_ID -> view.allowMissingDispatchRequestId();
            case TaskCallbackRuntimeConfigurationView.ENFORCE_STATE_TRANSITION -> view.enforceStateTransition();
            case TaskCallbackRuntimeConfigurationView.REJECT_OLD_ATTEMPT_CALLBACKS -> view.rejectOldAttemptCallbacks();
            case TaskCallbackRuntimeConfigurationView.REQUIRE_ATTEMPT_NO -> view.requireAttemptNo();
            case TaskCallbackRuntimeConfigurationView.ENFORCE_GATEWAY_AGENT_IDENTITY -> view.enforceGatewayAndAgentIdentity();
            case TaskCallbackRuntimeConfigurationView.ALLOW_TERMINAL_CALLBACK_OVERRIDE -> view.allowTerminalCallbackOverride();
            case TaskCallbackRuntimeConfigurationView.MAX_RECENT -> view.maxRecent();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_TIMEOUT_ENABLED -> view.recoveryTimeoutEnabled();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_SCAN_INTERVAL_MS -> view.recoveryScanInterval().toMillis();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_DISPATCH_TIMEOUT -> view.recoveryDispatchTimeout().toString();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_AUTO_FAIL_TIMED_OUT -> view.recoveryAutoFailTimedOut();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_RETRY_ENABLED -> view.recoveryRetryEnabled();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_MAX_ATTEMPTS -> view.recoveryMaxAttempts();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_INITIAL_BACKOFF -> view.recoveryInitialBackoff().toString();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_JITTER_PERCENT -> view.recoveryJitterPercent();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_MAX_BACKOFF -> view.recoveryMaxBackoff().toString();
            case TaskCallbackRuntimeConfigurationView.RECOVERY_MAX_BATCH_SIZE -> view.recoveryMaxBatchSize();
            default -> throw new IllegalArgumentException("TASK_CALLBACK_RUNTIME_CONFIG_KEY_NOT_OWNED key=" + key);
        };
    }
}
