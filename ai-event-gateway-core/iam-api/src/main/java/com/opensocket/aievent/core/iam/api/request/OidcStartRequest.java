package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record OidcStartRequest(@NotBlank @Size(max=128) String tenantId,@NotBlank @Size(max=128) String providerId,@Size(max=512) String returnTo) {}
