package com.opensocket.aievent.core.action;

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

/** C3R2E proof for active Adapter Action policy, MCP adapter and worker cadence consumers. */
class V41C3R2EAdapterActionRuntimeConfigurationViewTest {
    @Test
    void snapshotWinsForActiveAdapterActionConsumers() {
        AdapterActionProperties startup = new AdapterActionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        AdapterActionPolicyRuntimeConfigurationView policy = new AdapterActionPolicyRuntimeConfigurationView(startup, values, authority);
        AdapterActionMcpRuntimeConfigurationView mcp = new AdapterActionMcpRuntimeConfigurationView(startup, values, authority);
        AdapterActionWorkerRuntimeConfigurationView worker = new AdapterActionWorkerRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("r1", 1, """
                {"adapter-actions.create-suppressed-records":false,
                 "adapter-actions.issue.adapter-name":"runtime-issue",
                 "adapter-actions.mcp.enabled":true,
                 "adapter-actions.mcp.run-on-completed-task":false,
                 "adapter-actions.mcp.run-on-failed-task":true,
                 "adapter-actions.mcp.one-per-task":false,
                 "adapter-actions.mcp.adapter-name":"runtime-mcp",
                 "adapter-actions.worker.retry-enabled":true,
                 "adapter-actions.worker.max-attempts":9,
                 "adapter-actions.worker.initial-backoff":"PT2S",
                 "adapter-actions.worker.max-backoff":"PT1M",
                 "adapter-actions.worker.expired-lease-scan-batch-size":25,
                 "adapter-actions.worker.expired-lease-scan-interval-ms":4000}
                """, "hash-r1"));

        assertThat(policy.createSuppressedRecords()).isFalse();
        assertThat(policy.issueAdapterName()).isEqualTo("runtime-issue");
        assertThat(mcp.enabled()).isTrue();
        assertThat(mcp.adapterName()).isEqualTo("runtime-mcp");
        assertThat(worker.maxAttempts()).isEqualTo(9);
        assertThat(worker.expiredLeaseScanBatchSize()).isEqualTo(25);
        assertThat(worker.expiredLeaseScanInterval()).hasMillis(4000);
    }

    @Test
    void migratedAuthorityFailsClosedWhenNewActiveKeyIsMissing() {
        AdapterActionProperties startup = new AdapterActionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        authority.activate(Set.of(AdapterActionPolicyRuntimeConfigurationView.ISSUE_ADAPTER_NAME));
        AdapterActionPolicyRuntimeConfigurationView policy = new AdapterActionPolicyRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("r2", 2, "{\"adapter-actions.create-suppressed-records\":true}", "hash-r2"));
        assertThatThrownBy(policy::issueAdapterName)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE")
                .hasMessageContaining(AdapterActionPolicyRuntimeConfigurationView.ISSUE_ADAPTER_NAME);
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision,long sequence,String payload,String hash) {
        OffsetDateTime now=OffsetDateTime.of(2026,9,23,12,0,0,0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","config-set-adapter-action",RuntimeConfigurationSetKeys.ADAPTER_ACTION_SYSTEM,
                revision,sequence,2,now,now.plusHours(1),"CORE",payload,hash,"signature");
    }
}
