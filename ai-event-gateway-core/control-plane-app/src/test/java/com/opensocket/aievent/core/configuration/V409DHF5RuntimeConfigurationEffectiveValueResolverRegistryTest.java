package com.opensocket.aievent.core.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.action.AdapterActionMcpRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.action.AdapterActionMcpRuntimeConfigurationView;
import com.opensocket.aievent.core.action.AdapterActionWorkerRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.action.AdapterActionWorkerRuntimeConfigurationView;
import com.opensocket.aievent.core.action.executor.AdapterExecutorRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.action.executor.AdapterExecutorRuntimeConfigurationView;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.configuration.runtime.RuntimeConfigurationEffectiveValueResolverRegistry;
import com.opensocket.aievent.core.dispatch.DispatchRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.dispatch.DispatchRuntimeConfigurationView;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionRuntimeConfigurationView;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryRuntimeConfigurationEffectiveValueResolver;
import com.opensocket.aievent.core.task.TaskDispatchRecoveryRuntimeConfigurationView;

class V409DHF5RuntimeConfigurationEffectiveValueResolverRegistryTest {

    @Test
    void allExistingMigrationAuthorizedKeysHaveExactlyOneDomainResolver() {
        RuntimeConfigurationEffectiveValueResolverRegistry registry = registry();

        assertThat(registry.supportedKeys()).hasSize(30);
        assertThat(registry.ownerFor("dispatch.retry.max-attempts")).isEqualTo("DISPATCH");
        assertThat(registry.ownerFor("adapter-executor.circuit-breaker.failure-threshold")).isEqualTo("ADAPTER_EXECUTION");
        assertThat(registry.ownerFor("adapter-actions.worker.retry-enabled")).isEqualTo("ADAPTER_ACTION_WORKER");
        assertThat(registry.ownerFor("adapter-actions.mcp.enabled")).isEqualTo("ADAPTER_ACTION_MCP");
        assertThat(registry.ownerFor("issue-projection.reconcile-delay-ms")).isEqualTo("ISSUE_PROJECTION");
        assertThat(registry.ownerFor("task.dispatch-recovery.interval-ms")).isEqualTo("TASK_DISPATCH_RECOVERY");
        registry.requireCoverage(registry.supportedKeys());
    }

    @Test
    void duplicateOwnershipFailsFast() {
        RuntimeConfigurationEffectiveValueResolver one = staticResolver("ONE", Set.of("duplicate.key"));
        RuntimeConfigurationEffectiveValueResolver two = staticResolver("TWO", Set.of("duplicate.key"));

        assertThatThrownBy(() -> new RuntimeConfigurationEffectiveValueResolverRegistry(List.of(one, two)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_DUPLICATE")
                .hasMessageContaining("duplicate.key");
    }

    @Test
    void missingMigrationAuthorizedCoverageFailsFast() {
        RuntimeConfigurationEffectiveValueResolverRegistry registry =
                new RuntimeConfigurationEffectiveValueResolverRegistry(List.of(staticResolver("ONE", Set.of("owned.key"))));

        assertThatThrownBy(() -> registry.requireCoverage(Set.of("owned.key", "missing.key")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_EFFECTIVE_VALUE_RESOLVER_COVERAGE_INCOMPLETE")
                .hasMessageContaining("missing.key");
    }

    @Test
    void projectionCoverageAllowsRemoteSnapshotProjectionWithoutCreatingCrossProcessResolverDependencies() {
        RuntimeConfigurationEffectiveValueResolverRegistry registry =
                new RuntimeConfigurationEffectiveValueResolverRegistry(List.of(staticResolver("CORE", Set.of("core.key"))));

        registry.requireProjectionCoverage(Set.of("core.key", "gateway.key"), Set.of("gateway.key"));
        assertThat(registry.supports("core.key")).isTrue();
        assertThat(registry.supports("gateway.key")).isFalse();
    }

    @Test
    void projectionCoverageStillFailsWhenNeitherTypedResolverNorSnapshotPathExists() {
        RuntimeConfigurationEffectiveValueResolverRegistry registry =
                new RuntimeConfigurationEffectiveValueResolverRegistry(List.of(staticResolver("CORE", Set.of("core.key"))));

        assertThatThrownBy(() -> registry.requireProjectionCoverage(
                Set.of("core.key", "orphan.key"), Set.of("gateway.key")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNTIME_CONFIG_EFFECTIVE_VALUE_PROJECTION_COVERAGE_INCOMPLETE")
                .hasMessageContaining("orphan.key");
    }

    private static RuntimeConfigurationEffectiveValueResolverRegistry registry() {
        return new RuntimeConfigurationEffectiveValueResolverRegistry(List.of(
                new DispatchRuntimeConfigurationEffectiveValueResolver(mock(DispatchRuntimeConfigurationView.class)),
                new AdapterExecutorRuntimeConfigurationEffectiveValueResolver(mock(AdapterExecutorRuntimeConfigurationView.class)),
                new AdapterActionWorkerRuntimeConfigurationEffectiveValueResolver(mock(AdapterActionWorkerRuntimeConfigurationView.class)),
                new AdapterActionMcpRuntimeConfigurationEffectiveValueResolver(mock(AdapterActionMcpRuntimeConfigurationView.class)),
                new IssueProjectionRuntimeConfigurationEffectiveValueResolver(mock(IssueProjectionRuntimeConfigurationView.class)),
                new TaskDispatchRecoveryRuntimeConfigurationEffectiveValueResolver(mock(TaskDispatchRecoveryRuntimeConfigurationView.class))));
    }

    private static RuntimeConfigurationEffectiveValueResolver staticResolver(String owner, Set<String> keys) {
        return new RuntimeConfigurationEffectiveValueResolver() {
            @Override public String owner() { return owner; }
            @Override public Set<String> supportedKeys() { return keys; }
            @Override public Object resolve(String key) { return key; }
        };
    }
}
