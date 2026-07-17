package com.opensocket.aievent.core.identity.dto;

import java.util.List;

public record AdminTenantsResponse(String selectedTenantId, List<AdminTenantOption> tenants) {
}
