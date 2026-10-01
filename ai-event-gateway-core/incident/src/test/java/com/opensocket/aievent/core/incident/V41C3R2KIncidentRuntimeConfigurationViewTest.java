package com.opensocket.aievent.core.incident;

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

class V41C3R2KIncidentRuntimeConfigurationViewTest {
    @Test
    void incidentOperationalValuesComeFromSnapshotAndCutoverFailsClosed() {
        var registry = new RuntimeConfigurationLocalSnapshotRegistry();
        var values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        var authority = new RuntimeConfigurationAuthorityRegistry();
        var env = new MockEnvironment().withProperty(IncidentRuntimeConfigurationView.MAX_BATCH_SIZE, "25");
        var view = new IncidentRuntimeConfigurationView(values, authority, env);

        assertThat(view.maxBatchSize()).isEqualTo(25);
        registry.atomicSwap(snapshot(payload()));
        assertThat(view.maxBatchSize()).isEqualTo(200);
        assertThat(view.scanInterval().toMillis()).isEqualTo(15000);
        assertThat(view.reopenPolicy()).isEqualTo(IncidentModuleProperties.ReopenPolicy.CREATE_NEW);

        authority.activate(Set.of(IncidentRuntimeConfigurationView.MAX_BATCH_SIZE));
        registry.atomicSwap(snapshot("{}"));
        assertThatThrownBy(view::maxBatchSize).isInstanceOf(IllegalStateException.class);
    }

    private static String payload() {
        return "{"
                + "\"core.lifecycle.incident.scan-interval-ms\":15000,"
                + "\"core.lifecycle.incident.inactive-threshold\":\"PT6H\","
                + "\"core.lifecycle.incident.max-batch-size\":200,"
                + "\"core.lifecycle.incident.reopen-policy\":\"CREATE_NEW\","
                + "\"core.lifecycle.incident.reopen-window\":\"PT48H\""
                + "}";
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String payload) {
        var now = OffsetDateTime.of(2026, 9, 24, 10, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL", "incident", IncidentRuntimeConfigurationView.SET_KEY,
                "r1", 1, 7, now, now.plusHours(1), "CORE", payload, "hash", "sig");
    }
}
