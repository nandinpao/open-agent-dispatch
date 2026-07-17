package com.opensocket.aievent.core.identity;

import java.util.EnumSet;
import java.util.Set;

/** Human roles. Internal machine roles remain in the control-plane security module. */
public enum AdminRole {
    VIEWER(EnumSet.of(
            AdminPermission.IDENTITY_SELF_READ,
            AdminPermission.TENANT_SELECT,
            AdminPermission.GATEWAY_READ,
            AdminPermission.AGENT_READ,
            AdminPermission.TASK_READ,
            AdminPermission.DISPATCH_READ,
            AdminPermission.CLUSTER_READ,
            AdminPermission.RECOVERY_READ)),
    OPERATOR(EnumSet.of(
            AdminPermission.IDENTITY_SELF_READ,
            AdminPermission.TENANT_SELECT,
            AdminPermission.GATEWAY_READ,
            AdminPermission.AGENT_READ,
            AdminPermission.AGENT_OPERATE,
            AdminPermission.TASK_READ,
            AdminPermission.TASK_OPERATE,
            AdminPermission.DISPATCH_READ,
            AdminPermission.DISPATCH_OPERATE,
            AdminPermission.CLUSTER_READ,
            AdminPermission.CLUSTER_OPERATE,
            AdminPermission.RECOVERY_READ,
            AdminPermission.RECOVERY_OPERATE)),
    RECOVERY_APPROVER(EnumSet.of(
            AdminPermission.IDENTITY_SELF_READ,
            AdminPermission.TENANT_SELECT,
            AdminPermission.GATEWAY_READ,
            AdminPermission.AGENT_READ,
            AdminPermission.TASK_READ,
            AdminPermission.DISPATCH_READ,
            AdminPermission.CLUSTER_READ,
            AdminPermission.RECOVERY_READ,
            AdminPermission.RECOVERY_APPROVE)),
    SUPPORT(EnumSet.of(
            AdminPermission.IDENTITY_SELF_READ,
            AdminPermission.TENANT_SELECT,
            AdminPermission.GATEWAY_READ,
            AdminPermission.AGENT_READ,
            AdminPermission.TASK_READ,
            AdminPermission.DISPATCH_READ,
            AdminPermission.CLUSTER_READ,
            AdminPermission.RECOVERY_READ,
            AdminPermission.SUPPORT_LEGACY_READ)),
    ADMIN(EnumSet.complementOf(EnumSet.of(AdminPermission.SUPPORT_LEGACY_READ)));

    private final Set<AdminPermission> permissions;

    AdminRole(Set<AdminPermission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<AdminPermission> permissions() {
        return permissions;
    }
}
