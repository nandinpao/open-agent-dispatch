package com.opensocket.aievent.core.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

class V41C3R2CAgentRemediationMetricsRuntimeConfigurationViewTest {
    @Test
    void shouldUseEffectiveRuntimeEnabledAndFailClosedAfterCutover() {
        ObservabilityProperties startup = new ObservabilityProperties();
        startup.getRemediationWorkflowMetrics().setEnabled(true);
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        AgentRemediationMetricsRuntimeConfigurationView view =
                new AgentRemediationMetricsRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("metrics-r1", 1,
                "{\"core.observability.remediation-workflow-metrics.enabled\":false}"));
        assertThat(view.enabled()).isFalse();

        authority.activate(Set.of(AgentRemediationMetricsRuntimeConfigurationView.ENABLED));
        registry.atomicSwap(snapshot("metrics-r2", 2, "{}"));
        assertThatThrownBy(view::enabled)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 23, 10, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-core", AgentRemediationMetricsRuntimeConfigurationView.SET_KEY,
                revision, sequence, 7, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
