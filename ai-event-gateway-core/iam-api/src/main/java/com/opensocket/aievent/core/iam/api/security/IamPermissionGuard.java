package com.opensocket.aievent.core.iam.api.security;

import com.opensocket.aievent.core.iam.api.context.IamApiRequestContext;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import java.util.Map;

/**
 * IAM authorization guard. Callers must express the actual requested authorization scope;
 * resource labels alone are not a substitute for DEPARTMENT or GROUP scope.
 */
public final class IamPermissionGuard {
    private final IamSecurityAdapter adapter;

    public IamPermissionGuard(IamSecurityAdapter adapter) {
        this.adapter = adapter;
    }

    public void requireTenant(IamApiRequestContext context, String permission, String resourceType, String resourceId) {
        require(context, permission, resourceType, resourceId, ScopeType.TENANT, context.activeTenantId());
    }

    public void requireDepartment(IamApiRequestContext context, String permission, String departmentId) {
        requireDepartment(context, permission, "DEPARTMENT", departmentId);
    }

    public void requireDepartment(
            IamApiRequestContext context, String permission, String resourceType, String departmentId) {
        require(context, permission, resourceType, departmentId, ScopeType.DEPARTMENT, requireScopeId(departmentId, "departmentId"));
    }

    public void requireGroup(IamApiRequestContext context, String permission, String groupId) {
        requireGroup(context, permission, "GROUP", groupId);
    }

    public void requireGroup(IamApiRequestContext context, String permission, String resourceType, String groupId) {
        require(context, permission, resourceType, groupId, ScopeType.GROUP, requireScopeId(groupId, "groupId"));
    }

    public void requireInstance(IamApiRequestContext context, String permission, String resourceType, String resourceId) {
        require(context, permission, resourceType, resourceId, ScopeType.INSTANCE, "INSTANCE");
    }

    private void require(
            IamApiRequestContext context, String permission, String resourceType, String resourceId,
            ScopeType scopeType, String scopeId) {
        context.requireCredentialPermission(permission);
        adapter.require(
                context.requireAuthentication(),
                permission,
                resourceType,
                resourceId,
                scopeType,
                scopeId,
                Map.of("correlationId", context.correlationId(), "clientAddress", context.clientAddress()));
    }

    private static String requireScopeId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required for scoped authorization");
        }
        return value.trim();
    }
}
