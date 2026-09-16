package com.opensocket.aievent.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class R3HttpPermissionRuleTest {
    @Test
    void resolvesDepartmentScopeAndResourceFromPathVariables() {
        var rule = new R3HttpPermissionRule(
                "PATCH",
                "/api/departments/{departmentId}/members/{userId}",
                "identity.department.membership.manage",
                ScopeType.DEPARTMENT,
                "departmentId",
                "DEPARTMENT_MEMBERSHIP",
                "userId",
                true,
                Set.of("ADMIN"),
                Set.of("OPERATOR"));

        var resolved = rule.resolve(
                "PATCH", "/api/departments/department-a/members/user-7").orElseThrow();

        assertThat(resolved.scopeId()).isEqualTo("department-a");
        assertThat(resolved.resourceId()).isEqualTo("user-7");
        assertThat(resolved.pathVariables())
                .containsEntry("departmentId", "department-a")
                .containsEntry("userId", "user-7");
    }

    @Test
    void controllerScopedTenantRuleDefersOnlyTheFinalResourceScope() {
        var rule = new R3HttpPermissionRule(
                "GET", "/api/admin/access/tenants/{tenantId}/users", "identity.user.read",
                ScopeType.TENANT, R3HttpPermissionRule.CONTROLLER_SCOPED,
                "UNIFIED_ACCESS_MANAGEMENT", "tenantId", false, Set.of(), Set.of());

        var resolved = rule.resolve("GET", "/api/admin/access/tenants/tenant-a/users").orElseThrow();

        assertThat(rule.controllerScoped()).isTrue();
        assertThat(resolved.pathVariables()).containsEntry("tenantId", "tenant-a");
        assertThat(resolved.scopeId()).isEmpty();
    }

    @Test
    void doesNotMatchAdditionalPathSegments() {
        var rule = new R3HttpPermissionRule(
                "GET", "/api/users/{userId}", "identity.user.read",
                ScopeType.TENANT, "", "USER", "userId", false,
                Set.of("VIEWER"), Set.of("OPERATOR"));

        assertThat(rule.resolve("GET", "/api/users/user-1/details")).isEmpty();
    }
}
