package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;
public record RevokeEntryPointBypassRequest(@NotBlank @Size(min=12,max=1000) String reason) {}
