package com.opensocket.aievent.core.identity;

/** Stable human-operator permissions exposed to the Admin UI. */
public enum AdminPermission {
    IDENTITY_SELF_READ("identity:self:read"),
    TENANT_SELECT("tenant:select"),
    GATEWAY_READ("gateway:read"),
    AGENT_READ("agent:read"),
    AGENT_OPERATE("agent:operate"),
    AGENT_DISCONNECT("agent:disconnect"),
    TASK_READ("task:read"),
    TASK_OPERATE("task:operate"),
    DISPATCH_READ("dispatch:read"),
    DISPATCH_OPERATE("dispatch:operate"),
    CLUSTER_READ("cluster:read"),
    CLUSTER_OPERATE("cluster:operate"),
    RECOVERY_READ("recovery:read"),
    RECOVERY_OPERATE("recovery:operate"),
    RECOVERY_APPROVE("recovery:approve"),
    IDENTITY_ADMIN("identity:admin"),
    SUPPORT_LEGACY_READ("support:legacy:read");

    private final String code;

    AdminPermission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
