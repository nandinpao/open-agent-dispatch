package com.opensocket.aievent.core.action.executor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V405AdapterRuntimeConfigurationViewTest {
    @Test
    void shouldReadCompleteAdapterPilotSnapshotAndFollowNewerRevision() {
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        AdapterExecutorRuntimeConfigurationView view = new AdapterExecutorRuntimeConfigurationView(new AdapterActionExecutionProperties(), values);
        registry.atomicSwap(snapshot("adapter-1", 1, 20));
        assertThat(view.batchSize()).isEqualTo(20);
        assertThat(view.maxAttempts()).isEqualTo(6);
        registry.atomicSwap(snapshot("adapter-2", 2, 40));
        assertThat(view.batchSize()).isEqualTo(40);
        assertThat(view.revisionId()).isEqualTo("adapter-2");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision,long sequence,int batch) {
        OffsetDateTime now=OffsetDateTime.of(2026,9,18,0,0,0,0,ZoneOffset.UTC);
        String payload="{\"adapter-executor.batch-size\":"+batch+",\"adapter-executor.execution-timeout\":\"PT30S\",\"adapter-executor.initial-backoff\":\"PT5S\",\"adapter-executor.max-attempts\":6,\"adapter-executor.max-backoff\":\"PT1M\"}";
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","config-set-adapter",RuntimeConfigurationSetKeys.ADAPTER_EXECUTION_SYSTEM,revision,sequence,2,now,now.plusHours(1),"CORE",payload,"hash","signature");
    }
}
