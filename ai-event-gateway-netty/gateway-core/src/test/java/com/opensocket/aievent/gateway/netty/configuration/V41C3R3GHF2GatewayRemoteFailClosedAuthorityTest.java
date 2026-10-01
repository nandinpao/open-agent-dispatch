package com.opensocket.aievent.gateway.netty.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class V41C3R3GHF2GatewayRemoteFailClosedAuthorityTest {
    private static final String SET_KEY = "RUNTIME/GATEWAY/SYSTEM";
    private static final String KEY = "gateway.core-forward.enabled";

    @Test
    void strictRemoteAuthorityRejectsMissingSnapshotInsteadOfReactivatingStartupValue() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(
                registry, JsonMapper.builder().build(), true);

        assertThatThrownBy(() -> values.booleanValueOrStartup(SET_KEY, KEY, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIGURATION_SNAPSHOT_MISSING")
                .hasMessageContaining(SET_KEY)
                .hasMessageContaining(KEY);
    }

    @Test
    void dualReadSnapshotMayUseStartupFallbackForKeyNotYetRuntimeAuthoritative() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(
                registry, JsonMapper.builder().build(), true);
        registry.atomicSwap(snapshot("DUAL_READ", Set.of(), "{}"));

        assertThat(values.booleanValueOrStartup(SET_KEY, KEY, true)).isTrue();
    }

    @Test
    void signedRequiredKeyNeverFallsBackToStartupValue() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(
                registry, JsonMapper.builder().build(), true);
        registry.atomicSwap(snapshot("DUAL_READ", Set.of(KEY), "{}"));

        assertThatThrownBy(() -> values.booleanValueOrStartup(SET_KEY, KEY, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONFIGURATION_INCOMPLETE");
    }

    @Test
    void runtimeOnlyValueWinsEvenWhenStartupValueDisagrees() {
        GatewayRuntimeConfigurationLocalRegistry registry = new GatewayRuntimeConfigurationLocalRegistry();
        GatewayRuntimeConfigurationSnapshotValues values = new GatewayRuntimeConfigurationSnapshotValues(
                registry, JsonMapper.builder().build(), true);
        registry.atomicSwap(snapshot("RUNTIME_ONLY", Set.of(KEY), "{\"" + KEY + "\":false}"));

        assertThat(values.booleanValueOrStartup(SET_KEY, KEY, true)).isFalse();
        assertThat(values.runtimeAuthoritative(SET_KEY, KEY)).isTrue();
    }

    private static GatewayRuntimeConfigurationSnapshot snapshot(String mode, Set<String> required, String payload) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new GatewayRuntimeConfigurationSnapshot(
                "LOCAL", "gateway", SET_KEY, "hf2-r1", 1, 13, now, now.plusHours(1), "GATEWAY",
                mode, required, payload,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "signature");
    }
}
