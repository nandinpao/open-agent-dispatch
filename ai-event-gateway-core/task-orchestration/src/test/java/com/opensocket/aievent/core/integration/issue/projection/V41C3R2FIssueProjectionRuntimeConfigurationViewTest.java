package com.opensocket.aievent.core.integration.issue.projection;

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

class V41C3R2FIssueProjectionRuntimeConfigurationViewTest {
    @Test
    void activeProjectionPolicyUsesSnapshotAndFailsClosedAfterAuthorityCutover() {
        IssueProjectionProperties startup = new IssueProjectionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        IssueProjectionRuntimeConfigurationView view = new IssueProjectionRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("issue-r1", 1,
                "{\"issue-projection.enabled\":false,\"issue-projection.max-attempts\":12,"
                + "\"issue-projection.reconcile-batch-size\":50,\"issue-projection.retry-delay-seconds\":20,"
                + "\"issue-projection.reconcile-delay-ms\":2500}"));
        assertThat(view.enabled()).isFalse();
        assertThat(view.maxAttempts()).isEqualTo(12);
        assertThat(view.reconcileBatchSize()).isEqualTo(50);
        assertThat(view.retryDelaySeconds()).isEqualTo(20);
        assertThat(view.reconcileDelay().toMillis()).isEqualTo(2500);

        authority.activate(Set.of(IssueProjectionRuntimeConfigurationView.ENABLED));
        registry.atomicSwap(snapshot("issue-r2", 2, "{}"));
        assertThatThrownBy(view::enabled).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision, long sequence, String payload) {
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 23, 12, 0, 0, 0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope(
                "LOCAL", "config-set-issue", IssueProjectionRuntimeConfigurationView.SET_KEY,
                revision, sequence, 7, now, now.plusHours(1), "CORE", payload,
                "hash-" + sequence, "signature");
    }
}
