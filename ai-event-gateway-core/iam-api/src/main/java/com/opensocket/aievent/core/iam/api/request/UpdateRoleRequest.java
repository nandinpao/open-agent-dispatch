package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;
public record UpdateRoleRequest(@NotBlank String roleName,@Size(max=1000) String description) { }
