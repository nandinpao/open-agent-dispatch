package com.opensocket.aievent.core.resourceaccess.contract;

/** Permission-bound operation. Permission codes remain in the existing business permission catalog. */
public record ResourceAction(String permissionCode, ActionKind kind, boolean sideEffecting) {
    public ResourceAction {
        if (permissionCode == null || permissionCode.isBlank()) throw new IllegalArgumentException("permissionCode is required");
        permissionCode = permissionCode.trim();
        if (kind == null) throw new IllegalArgumentException("kind is required");
    }
    public enum ActionKind { READ, CREATE, UPDATE, DELETE, APPROVE, EXPORT, DOWNLOAD, MANAGE, EXECUTE }
}
