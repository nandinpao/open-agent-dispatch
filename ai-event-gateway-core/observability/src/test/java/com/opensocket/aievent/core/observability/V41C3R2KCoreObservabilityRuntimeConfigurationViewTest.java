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

class V41C3R2KCoreObservabilityRuntimeConfigurationViewTest {
    @Test
    void runtimeSnapshotOverridesStartupAndMigratedKeyFailsClosed() {
        var registry = new RuntimeConfigurationLocalSnapshotRegistry();
        var values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        var authority = new RuntimeConfigurationAuthorityRegistry();
        var startup = new ObservabilityProperties();
        startup.setSummarySampleLimit(10);
        var view = new CoreObservabilityRuntimeConfigurationView(startup, values, authority);

        assertThat(view.summarySampleLimit()).isEqualTo(10);
        registry.atomicSwap(snapshot(payload()));
        assertThat(view.summarySampleLimit()).isEqualTo(750);
        assertThat(view.recoveryHistoryLimit()).isEqualTo(3000);
        assertThat(view.commonTagComponent()).isEqualTo("runtime-core");

        authority.activate(Set.of(CoreObservabilityRuntimeConfigurationView.SUMMARY_SAMPLE_LIMIT));
        registry.atomicSwap(snapshot("{}"));
        assertThatThrownBy(view::summarySampleLimit).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void criticalRecoveryThresholdCannotBeLowerThanWarningThreshold() {
        var registry = new RuntimeConfigurationLocalSnapshotRegistry();
        var values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        var view = new CoreObservabilityRuntimeConfigurationView(new ObservabilityProperties(), values, new RuntimeConfigurationAuthorityRegistry());
        registry.atomicSwap(snapshot(payload().replace("\"core.observability.recovery-metrics.runtime-failure-critical-threshold\":30",
                "\"core.observability.recovery-metrics.runtime-failure-critical-threshold\":3")));
        assertThat(view.runtimeFailureWarningThreshold()).isEqualTo(8);
        assertThatThrownBy(view::runtimeFailureCriticalThreshold)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_VALIDATION_FAILED")
                .hasMessageContaining(CoreObservabilityRuntimeConfigurationView.RUNTIME_FAILURE_CRITICAL);
    }

    private static String payload() {
        return "{"
                + "\"core.observability.enabled\":true,"
                + "\"core.observability.business-metrics-enabled\":true,"
                + "\"core.observability.health-indicator-enabled\":true,"
                + "\"core.observability.include-site-tag\":false,"
                + "\"core.observability.summary-sample-limit\":750,"
                + "\"core.observability.slow-intake-threshold\":\"PT2S\","
                + "\"core.observability.common-tags.component\":\"runtime-core\","
                + "\"core.observability.recovery-metrics.enabled\":true,"
                + "\"core.observability.recovery-metrics.window\":\"PT30M\","
                + "\"core.observability.recovery-metrics.history-limit\":3000,"
                + "\"core.observability.recovery-metrics.runtime-failure-warning-threshold\":8,"
                + "\"core.observability.recovery-metrics.runtime-failure-critical-threshold\":30,"
                + "\"core.observability.recovery-metrics.delayed-requeue-warning-threshold\":12,"
                + "\"core.observability.recovery-metrics.delayed-requeue-critical-threshold\":60,"
                + "\"core.observability.recovery-metrics.dead-letter-warning-threshold\":2,"
                + "\"core.observability.recovery-metrics.dead-letter-critical-threshold\":6,"
                + "\"core.observability.recovery-metrics.scanner-failure-warning-threshold\":2,"
                + "\"core.observability.recovery-metrics.scanner-failure-critical-threshold\":7,"
                + "\"core.observability.recovery-metrics.recovery-exhausted-warning-threshold\":2,"
                + "\"core.observability.recovery-metrics.recovery-exhausted-critical-threshold\":5"
                + "}";
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String payload) {
        var now = OffsetDateTime.of(2026, 9, 24, 10, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL", "core-observability", CoreObservabilityRuntimeConfigurationView.SET_KEY,
                "r1", 1, 7, now, now.plusHours(1), "CORE", payload, "hash", "sig");
    }
}
