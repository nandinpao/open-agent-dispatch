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

/** V40-9C local-snapshot and rollback proof for Adapter Action MCP orchestration. */
class V409CAdapterActionMcpRuntimeConfigurationViewTest {
    @Test
    void shouldApplyGovernedMcpValuesAndRollbackWithoutReconstructingTheView() {
        AdapterActionProperties startup = new AdapterActionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        AdapterActionMcpRuntimeConfigurationView view = new AdapterActionMcpRuntimeConfigurationView(startup, values);

        assertThat(view.enabled()).isFalse();
        assertThat(view.runOnCompletedTask()).isTrue();
        assertThat(view.runOnFailedTask()).isFalse();
        assertThat(view.onePerTask()).isTrue();

        registry.atomicSwap(snapshot("mcp-r1", 1,
                "{\"adapter-actions.mcp.enabled\":true,\"adapter-actions.mcp.run-on-completed-task\":false," +
                "\"adapter-actions.mcp.run-on-failed-task\":true,\"adapter-actions.mcp.one-per-task\":false}", "mcp-hash-a"));
        assertThat(view.enabled()).isTrue();
        assertThat(view.runOnCompletedTask()).isFalse();
        assertThat(view.runOnFailedTask()).isTrue();
        assertThat(view.onePerTask()).isFalse();

        registry.atomicSwap(snapshot("mcp-r2-rollback", 2,
                "{\"adapter-actions.mcp.enabled\":false,\"adapter-actions.mcp.run-on-completed-task\":true," +
                "\"adapter-actions.mcp.run-on-failed-task\":false,\"adapter-actions.mcp.one-per-task\":true}", "mcp-hash-b"));
        assertThat(view.enabled()).isFalse();
        assertThat(view.runOnCompletedTask()).isTrue();
        assertThat(view.runOnFailedTask()).isFalse();
        assertThat(view.onePerTask()).isTrue();
        assertThat(view.revisionId()).isEqualTo("mcp-r2-rollback");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision,long sequence,String payload,String hash) {
        OffsetDateTime now=OffsetDateTime.of(2026,9,21,1,0,0,0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","config-set-adapter-action",RuntimeConfigurationSetKeys.ADAPTER_ACTION_SYSTEM,
                revision,sequence,2,now,now.plusHours(1),"CORE",payload,hash,"signature");
    }
}
