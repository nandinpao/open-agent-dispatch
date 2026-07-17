package com.opensocket.aievent.core.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(@NotBlank String username, @NotBlank String password) {
}
