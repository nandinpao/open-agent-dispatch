package com.opensocket.aievent.core.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminTenantSelectionRequest(@NotBlank String tenantId) {
}
