package com.opensocket.aievent.core.iam.rbac.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record RolePermissionGrant(String grantId, Optional<String> tenantId, RoleId roleId, PermissionCode permissionCode,
                                  Instant createdAt, String createdBy, long version) {
    public RolePermissionGrant {
        if(grantId==null||grantId.isBlank())throw new IllegalArgumentException("grantId is required"); grantId=grantId.trim();
        tenantId=tenantId==null?Optional.empty():tenantId.map(String::trim); Objects.requireNonNull(roleId,"roleId");
        Objects.requireNonNull(permissionCode,"permissionCode"); Objects.requireNonNull(createdAt,"createdAt");
        if(createdBy==null||createdBy.isBlank())throw new IllegalArgumentException("createdBy is required"); createdBy=createdBy.trim();
        if(version<1)throw new IllegalArgumentException("version must be positive");
    }
}
