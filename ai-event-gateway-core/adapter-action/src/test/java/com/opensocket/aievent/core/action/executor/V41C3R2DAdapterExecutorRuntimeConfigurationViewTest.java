package com.opensocket.aievent.core.action.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

/** C3R2D proof that Adapter Execution reads effective runtime values and fails closed after cutover. */
class V41C3R2DAdapterExecutorRuntimeConfigurationViewTest {
    @Test
    void snapshotWinsDuringMigrationReadyForNewAdapterExecutionConsumers() {
        AdapterActionExecutionProperties startup = new AdapterActionExecutionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        AdapterExecutorRuntimeConfigurationView view = new AdapterExecutorRuntimeConfigurationView(startup, values, new RuntimeConfigurationAuthorityRegistry());

        registry.atomicSwap(snapshot("r1", 1, """
                {"adapter-executor.auto-execute-interval":"PT3S",
                 "adapter-executor.audit.payload-snapshot-enabled":false,
                 "adapter-executor.mark-unavailable-when-no-executor":false,
                 "adapter-executor.issue.auto-execute-pending":false,
                 "adapter-executor.issue.connector-runtime-enabled":true,
                 "adapter-executor.issue.default-vendor":"REDMINE",
                 "adapter-executor.issue.link-projection-reconciliation-enabled":true,
                 "adapter-executor.issue.link-projection-batch-size":25,
                 "adapter-executor.issue.link-projection-max-attempts":7,
                 "adapter-executor.issue.link-projection-initial-backoff":"PT2S",
                 "adapter-executor.issue.link-projection-max-backoff":"PT30S",
                 "adapter-executor.mcp.http-enabled":true,
                 "adapter-executor.mcp.executor-name":"runtime-mcp",
                 "adapter-executor.mcp.endpoint-url":"https://mcp.example.test/execute",
                 "adapter-executor.mcp.timeout":"PT8S"}
                """, "hash-r1"));

        assertThat(view.autoExecuteInterval()).hasSeconds(3);
        assertThat(view.auditPayloadSnapshotEnabled()).isFalse();
        assertThat(view.markUnavailableWhenNoExecutor()).isFalse();
        assertThat(view.issueAutoExecutePending()).isFalse();
        assertThat(view.issueDefaultVendor()).isEqualTo("REDMINE");
        assertThat(view.issueLinkProjectionBatchSize()).isEqualTo(25);
        assertThat(view.issueLinkProjectionMaxAttempts()).isEqualTo(7);
        assertThat(view.mcpHttpEnabled()).isTrue();
        assertThat(view.mcpExecutorName()).isEqualTo("runtime-mcp");
        assertThat(view.mcpTimeout()).hasSeconds(8);
    }

    @Test
    void migratedAuthorityFailsClosedWhenRequiredRuntimeKeyIsMissing() {
        AdapterActionExecutionProperties startup = new AdapterActionExecutionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        authority.activate(Set.of(AdapterExecutorRuntimeConfigurationView.MCP_TIMEOUT));
        AdapterExecutorRuntimeConfigurationView view = new AdapterExecutorRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("r2", 2, "{\"adapter-executor.mcp.http-enabled\":true}", "hash-r2"));
        assertThatThrownBy(view::mcpTimeout)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE")
                .hasMessageContaining(AdapterExecutorRuntimeConfigurationView.MCP_TIMEOUT);
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision,long sequence,String payload,String hash) {
        OffsetDateTime now=OffsetDateTime.of(2026,9,23,10,0,0,0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","config-set-adapter-execution",RuntimeConfigurationSetKeys.ADAPTER_EXECUTION_SYSTEM,
                revision,sequence,2,now,now.plusHours(1),"CORE",payload,hash,"signature");
    }
}
