package com.opensocket.aievent.core.action.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationAuthorityRegistry;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSetKeys;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationSnapshotValues;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationLocalSnapshotRegistry;
import com.opensocket.aievent.core.kernel.configuration.distribution.RuntimeConfigurationSnapshotEnvelope;

import tools.jackson.databind.json.JsonMapper;

/** V40-9D-HF1 authority-cutover proof for Adapter Executor circuit breaking. */
class V409DAdapterExecutorCircuitBreakerRuntimeConfigurationViewTest {
    @Test
    void shouldKeepStartupAuthorityBeforeCutoverThenUseSnapshotAfterAuthorityActivation() {
        AdapterActionExecutionProperties startup = new AdapterActionExecutionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        AdapterExecutorRuntimeConfigurationView view = new AdapterExecutorRuntimeConfigurationView(startup, values, authority);
        AdapterExecutorCircuitBreaker breaker = new AdapterExecutorCircuitBreaker(startup, view);

        assertThat(view.circuitBreakerEnabled()).isTrue();
        assertThat(view.circuitBreakerFailureThreshold()).isEqualTo(5);

        registry.atomicSwap(snapshot("cb-r1", 1,
                "{\"adapter-executor.circuit-breaker.enabled\":true,\"adapter-executor.circuit-breaker.failure-threshold\":2," +
                "\"adapter-executor.circuit-breaker.open-duration\":\"PT1M\"}", "cb-hash-a"));

        // MIGRATION_READY: observing a candidate snapshot must not silently switch authority.
        assertThat(view.circuitBreakerFailureThreshold()).isEqualTo(5);
        assertThat(view.circuitBreakerRuntimeBacked()).isFalse();

        authority.activate(AdapterExecutorCircuitBreakerRuntimeKeys.ALL);
        breaker.recordFailure("mcp-http-executor");
        assertThat(breaker.isOpen("mcp-http-executor")).isFalse();
        breaker.recordFailure("mcp-http-executor");
        assertThat(breaker.isOpen("mcp-http-executor")).isTrue();
        assertThat(view.circuitBreakerRuntimeBacked()).isTrue();

        registry.atomicSwap(snapshot("cb-r2-rollback", 2,
                "{\"adapter-executor.circuit-breaker.enabled\":false,\"adapter-executor.circuit-breaker.failure-threshold\":5," +
                "\"adapter-executor.circuit-breaker.open-duration\":\"PT1M\"}", "cb-hash-b"));
        assertThat(view.circuitBreakerEnabled()).isFalse();
        assertThat(breaker.isOpen("mcp-http-executor")).isFalse();
        assertThat(view.revisionId()).isEqualTo("cb-r2-rollback");
    }

    @Test
    void shouldFailClosedWhenMigratedAuthorityHasIncompleteSnapshot() {
        AdapterActionExecutionProperties startup = new AdapterActionExecutionProperties();
        RuntimeConfigurationLocalSnapshotRegistry registry = new RuntimeConfigurationLocalSnapshotRegistry();
        RuntimeConfigurationSnapshotValues values = new RuntimeConfigurationSnapshotValues(registry, JsonMapper.builder().build());
        RuntimeConfigurationAuthorityRegistry authority = new RuntimeConfigurationAuthorityRegistry();
        authority.activate(AdapterExecutorCircuitBreakerRuntimeKeys.ALL);
        AdapterExecutorRuntimeConfigurationView view = new AdapterExecutorRuntimeConfigurationView(startup, values, authority);

        registry.atomicSwap(snapshot("cb-incomplete", 3,
                "{\"adapter-executor.circuit-breaker.enabled\":true,\"adapter-executor.circuit-breaker.open-duration\":\"PT1M\"}",
                "cb-hash-incomplete"));

        assertThatThrownBy(view::circuitBreakerFailureThreshold)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_SNAPSHOT_INCOMPLETE")
                .hasMessageContaining(AdapterExecutorCircuitBreakerRuntimeKeys.FAILURE_THRESHOLD);
    }

    private static RuntimeConfigurationSnapshotEnvelope snapshot(String revision,long sequence,String payload,String hash) {
        OffsetDateTime now=OffsetDateTime.of(2026,9,21,2,0,0,0, ZoneOffset.UTC);
        return new RuntimeConfigurationSnapshotEnvelope("LOCAL","config-set-adapter-execution",RuntimeConfigurationSetKeys.ADAPTER_EXECUTION_SYSTEM,
                revision,sequence,2,now,now.plusHours(1),"CORE",payload,hash,"signature");
    }
}
