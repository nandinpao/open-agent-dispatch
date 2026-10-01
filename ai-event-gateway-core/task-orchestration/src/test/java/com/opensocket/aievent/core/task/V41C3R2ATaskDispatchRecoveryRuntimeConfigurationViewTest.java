package com.opensocket.aievent.core.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2ATaskDispatchRecoveryRuntimeConfigurationViewTest {
    @Test
    void shouldReadAllNewTaskDispatchRecoveryKeysAndFailClosedAfterCutover() {
        TaskDispatchRecoveryProperties startup = new TaskDispatchRecoveryProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        TaskDispatchRecoveryRuntimeConfigurationView view =
                new TaskDispatchRecoveryRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("task-dispatch-r1", 1,
                "{\"task.dispatch-recovery.enabled\":false," +
                "\"task.dispatch-recovery.scanner-enabled\":false," +
                "\"task.dispatch-recovery.interval-ms\":2500," +
                "\"task.dispatch-recovery.max-batch-size\":75," +
                "\"task.dispatch-recovery.max-attempts\":7," +
                "\"task.dispatch-recovery.initial-delay\":\"PT5S\"," +
                "\"task.dispatch-recovery.max-delay\":\"PT1M\"," +
                "\"task.dispatch-recovery.claim-lease\":\"PT15S\"," +
                "\"task.dispatch-recovery.worker-id\":\"task-worker-r1\"}"));

        assertThat(view.enabled()).isFalse();
        assertThat(view.scannerEnabled()).isFalse();
        assertThat(view.scanInterval()).isEqualTo(Duration.ofMillis(2500));
        assertThat(view.claimLease()).isEqualTo(Duration.ofSeconds(15));
        assertThat(view.workerId()).isEqualTo("task-worker-r1");
        assertThat(view.delayForAttempt(2)).isEqualTo(Duration.ofSeconds(10));

        authority.activate(TaskDispatchRecoveryRuntimeConfigurationView.ALL);
        registry.atomicSwap(snapshot("task-dispatch-r2", 2,
                "{\"task.dispatch-recovery.enabled\":true}"));

        assertThat(view.enabled()).isTrue();
        assertThatThrownBy(view::workerId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE")
                .hasMessageContaining(TaskDispatchRecoveryRuntimeConfigurationView.WORKER_ID);
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 23, 6, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-task", RuntimeConfigurationSetKeys.TASK_SYSTEM,
                revision, sequence, 5, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
