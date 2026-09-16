package com.opensocket.aievent.core.source;

import java.time.OffsetDateTime;

/**
 * Mutable Source System API view.
 *
 * <p>Keep the JavaBean accessors explicit in this executable module.  The
 * control-plane controllers and services consume this type through explicit JavaBean getter and setter
 * methods, so its compile contract must not depend on Lombok annotation
 * processing being active for this single class.</p>
 */
public class SourceSystemView {
    private String tenantId;
    private String sourceSystemId;
    private String displayName;
    private String description;
    private String status;
    /** Canonical business-data owner. Null/null means Tenant-owned. */
    private String ownerDepartmentId;
    private String ownerGroupId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getSourceSystemId() { return sourceSystemId; }
    public void setSourceSystemId(String sourceSystemId) { this.sourceSystemId = sourceSystemId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwnerDepartmentId() { return ownerDepartmentId; }
    public void setOwnerDepartmentId(String ownerDepartmentId) { this.ownerDepartmentId = ownerDepartmentId; }

    public String getOwnerGroupId() { return ownerGroupId; }
    public void setOwnerGroupId(String ownerGroupId) { this.ownerGroupId = ownerGroupId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
