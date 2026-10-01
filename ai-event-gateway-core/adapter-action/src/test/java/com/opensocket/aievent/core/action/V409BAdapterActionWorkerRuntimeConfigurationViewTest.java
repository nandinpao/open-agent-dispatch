package com.opensocket.aievent.core.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

/** V40-9B local-snapshot and rollback proof for Adapter Action worker tuning. */
class V409BAdapterActionWorkerRuntimeConfigurationViewTest {
    @Test
    void shouldApplyGovernedWorkerValuesAndRollbackWithoutReconstructingTheView() {
        AdapterActionProperties startup = new AdapterActionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        AdapterActionWorkerRuntimeConfigurationView view = new AdapterActionWorkerRuntimeConfigurationView(startup, values);

        assertThat(view.retryEnabled()).isTrue();
        assertThat(view.maxAttempts()).isEqualTo(3);

        registry.atomicSwap(snapshot("worker-r1", 1,
                "{\"adapter-actions.worker.retry-enabled\":false,\"adapter-actions.worker.max-attempts\":5," +
                "\"adapter-actions.worker.initial-backoff\":\"PT5S\",\"adapter-actions.worker.max-backoff\":\"PT1M\"," +
                "\"adapter-actions.worker.expired-lease-scan-batch-size\":40}", "hash-a"));
        assertThat(view.retryEnabled()).isFalse();
        assertThat(view.maxAttempts()).isEqualTo(5);
        assertThat(view.initialBackoff()).hasSeconds(5);
        assertThat(view.expiredLeaseScanBatchSize()).isEqualTo(40);

        registry.atomicSwap(snapshot("worker-r2-rollback", 2,
                "{\"adapter-actions.worker.retry-enabled\":true,\"adapter-actions.worker.max-attempts\":3," +
                "\"adapter-actions.worker.initial-backoff\":\"PT30S\",\"adapter-actions.worker.max-backoff\":\"PT10M\"," +
                "\"adapter-actions.worker.expired-lease-scan-batch-size\":100}", "hash-b"));
        assertThat(view.retryEnabled()).isTrue();
        assertThat(view.maxAttempts()).isEqualTo(3);
        assertThat(view.expiredLeaseScanBatchSize()).isEqualTo(100);
        assertThat(view.revisionId()).isEqualTo("worker-r2-rollback");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision,long sequence,String payload,String hash) {
        OffsetDateTime now=OffsetDateTime.of(2026,9,21,0,0,0,0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","config-set-adapter-action",RuntimeConfigurationSetKeys.ADAPTER_ACTION_SYSTEM,
                revision,sequence,2,now,now.plusHours(1),"CORE",payload,hash,"signature");
    }
}
