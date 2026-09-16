package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.*;

public record CreateServiceAccountCredentialRequest(
        @NotBlank @Size(max=160) String name,
        @Min(60) long ttlSeconds) {}
