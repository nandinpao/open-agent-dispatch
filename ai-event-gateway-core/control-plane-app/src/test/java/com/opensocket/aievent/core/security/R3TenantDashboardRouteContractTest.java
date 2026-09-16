package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import com.opensocket.aievent.core.api.CoreDashboardController;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;

class R3TenantDashboardRouteContractTest {
    @Test
    void dashboardRoutesAreExplicitlyTenantAddressed() {
        Map<String, String> expected = Map.of(
                "snapshot", "/tenants/{tenantId}/dashboard/snapshot",
                "agentRuntimeView", "/tenants/{tenantId}/agents/runtime-view",
                "tasksRuntimeView", "/tenants/{tenantId}/tasks/runtime-view",
                "securityEvents", "/tenants/{tenantId}/security-events",
                "agentGovernanceSummary", "/tenants/{tenantId}/agent-governance/summary");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Method method = Arrays.stream(CoreDashboardController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(entry.getKey()))
                    .findFirst().orElseThrow();
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            assertThat(mapping).isNotNull();
            assertThat(mapping.value()).containsExactly(entry.getValue());
        }
    }

    @Test
    void rootSessionProjectsOnlyFromDashboardTenantPath() {
        R3HumanApiPermissionRegistry registry = new R3HumanApiPermissionRegistry();
        var canonical = registry.resolve("GET", "/admin/tenants/tenant-a/dashboard/snapshot").orElseThrow();
        assertThat(canonical.rule().scopeType()).isEqualTo(ScopeType.TENANT);
        assertThat(canonical.pathVariables()).containsEntry("tenantId", "tenant-a");
        assertThat(registry.resolve("GET", "/admin/dashboard/snapshot")).isEmpty();
    }
}
