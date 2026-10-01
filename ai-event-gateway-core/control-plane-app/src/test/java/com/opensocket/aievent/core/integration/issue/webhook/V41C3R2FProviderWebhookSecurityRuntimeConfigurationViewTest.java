package com.opensocket.aievent.core.integration.issue.webhook;

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

class V41C3R2FProviderWebhookSecurityRuntimeConfigurationViewTest {
    @Test
    void ingressLimitsUseRuntimeSnapshotAndEnabledIsFailClosedAfterCutover() {
        ProviderWebhookSecurityProperties startup = new ProviderWebhookSecurityProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        ProviderWebhookSecurityRuntimeConfigurationView view =
                new ProviderWebhookSecurityRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("security-r1", 1,
                "{\"integration-sync.webhook-security.enabled\":false,"
                + "\"integration-sync.webhook-security.default-max-body-bytes\":2097152,"
                + "\"integration-sync.webhook-security.default-rate-limit-per-minute\":240,"
                + "\"integration-sync.webhook-security.clock-skew-seconds\":45,"
                + "\"integration-sync.webhook-security.nonce-cleanup-interval-ms\":30000,"
                + "\"integration-sync.webhook-security.nonce-cleanup-batch-size\":2500}"));
        assertThat(view.enabled()).isFalse();
        assertThat(view.defaultMaxBodyBytes()).isEqualTo(2097152);
        assertThat(view.defaultRateLimitPerMinute()).isEqualTo(240);
        assertThat(view.clockSkewSeconds()).isEqualTo(45);
        assertThat(view.nonceCleanupInterval().toMillis()).isEqualTo(30000);
        assertThat(view.nonceCleanupBatchSize()).isEqualTo(2500);

        authority.activate(Set.of(ProviderWebhookSecurityRuntimeConfigurationView.ENABLED));
        registry.atomicSwap(snapshot("security-r2", 2, "{}"));
        assertThatThrownBy(view::enabled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 23, 12, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-integration-sync", ProviderWebhookSecurityRuntimeConfigurationView.SET_KEY,
                revision, sequence, 7, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
