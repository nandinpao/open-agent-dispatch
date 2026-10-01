package com.opensocket.aievent.core.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2ATaskCallbackRuntimeConfigurationViewTest {
    @Test
    void shouldPreferLocalSnapshotBeforeCutoverAndFailClosedAfterCutoverWhenKeyMissing() {
        TaskCallbackProperties startup = new TaskCallbackProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        TaskCallbackRuntimeConfigurationView view = new TaskCallbackRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("task-callback-r1", 1,
                "{\"task.callback.idempotency-enabled\":false," +
                "\"task.callback.max-recent\":250," +
                "\"task.callback.recovery.scan-interval-ms\":5000}"));

        assertThat(view.idempotencyEnabled()).isFalse();
        assertThat(view.maxRecent()).isEqualTo(250);
        assertThat(view.recoveryScanInterval().toMillis()).isEqualTo(5000);

        authority.activate(TaskCallbackRuntimeConfigurationView.ALL);
        registry.atomicSwap(snapshot("task-callback-r2", 2,
                "{\"task.callback.idempotency-enabled\":true}"));

        assertThat(view.idempotencyEnabled()).isTrue();
        assertThatThrownBy(view::maxRecent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE")
                .hasMessageContaining(TaskCallbackRuntimeConfigurationView.MAX_RECENT);
    }

    @Test
    void shouldValidateRecoveryBoundsFromRuntimeSnapshot() {
        TaskCallbackProperties startup = new TaskCallbackProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        TaskCallbackRuntimeConfigurationView view = new TaskCallbackRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("task-callback-invalid", 3,
                "{\"task.callback.recovery.max-attempts\":99}"));

        assertThatThrownBy(view::recoveryMaxAttempts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_VALIDATION_FAILED")
                .hasMessageContaining(TaskCallbackRuntimeConfigurationView.RECOVERY_MAX_ATTEMPTS);
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 23, 6, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-task", RuntimeConfigurationSetKeys.TASK_SYSTEM,
                revision, sequence, 5, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
