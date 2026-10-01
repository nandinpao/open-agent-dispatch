package com.opensocket.aievent.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2CAgentDirectoryRuntimeConfigurationViewTest {
    @Test
    void shouldUseSnapshotBeforeCutoverAndFailClosedAfterCutover() {
        MockEnvironment environment = new MockEnvironment().withProperty("agent-directory.lease-reaper.fixed-delay", "10000");
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        AgentDirectoryRuntimeConfigurationView view = new AgentDirectoryRuntimeConfigurationView(values, authority, environment);

        assertThat(view.leaseReaperFixedDelay().toMillis()).isEqualTo(10000);
        registry.atomicSwap(snapshot("directory-r1", 1, "{\"agent-directory.lease-reaper.fixed-delay\":2500}"));
        assertThat(view.leaseReaperFixedDelay().toMillis()).isEqualTo(2500);

        authority.activate(Set.of(AgentDirectoryRuntimeConfigurationView.LEASE_REAPER_FIXED_DELAY));
        registry.atomicSwap(snapshot("directory-r2", 2, "{}"));
        assertThatThrownBy(view::leaseReaperFixedDelay)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 23, 10, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-agent-directory", AgentDirectoryRuntimeConfigurationView.SET_KEY,
                revision, sequence, 7, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
