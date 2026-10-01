package com.opensocket.aievent.core.integration.issue;

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

class V41C3R2FProviderWebhookReconciliationRuntimeConfigurationViewTest {
    @Test
    void snapshotWinsBeforeCutoverAndMissingAuthoritativeValueFailsClosed() {
        ProviderWebhookReconciliationProperties startup = new ProviderWebhookReconciliationProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        ProviderWebhookReconciliationRuntimeConfigurationView view =
                new ProviderWebhookReconciliationRuntimeConfigurationView(startup, values, authority);

        assertThat(view.replayWindowSeconds()).isEqualTo(300);
        registry.atomicSwap(snapshot("webhook-r1", 1,
                "{\"integration-sync.webhook-replay-window-seconds\":600,"
                + "\"integration-sync.webhook-max-attempts\":9,"
                + "\"integration-sync.webhook-claim-lease-seconds\":90,"
                + "\"integration-sync.webhook-reconcile-delay-ms\":5000}"));
        assertThat(view.replayWindowSeconds()).isEqualTo(600);
        assertThat(view.maxAttempts()).isEqualTo(9);
        assertThat(view.claimLeaseSeconds()).isEqualTo(90);
        assertThat(view.reconcileDelay().toMillis()).isEqualTo(5000);

        authority.activate(Set.of(ProviderWebhookReconciliationRuntimeConfigurationView.MAX_ATTEMPTS));
        registry.atomicSwap(snapshot("webhook-r2", 2, "{}"));
        assertThatThrownBy(view::maxAttempts).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 23, 12, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-integration-sync",
                ProviderWebhookReconciliationRuntimeConfigurationView.SET_KEY,
                revision, sequence, 7, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
