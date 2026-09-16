package com.opensocket.aievent.core.iam.organization.domain;

import com.opensocket.aievent.core.iam.security.contract.TenantRef;

public record TenantId(String value) {
    public TenantId { value = OrganizationText.required(value, "tenantId", 128); }
    public TenantRef toTenantRef() { return TenantRef.tenant(value); }
}
