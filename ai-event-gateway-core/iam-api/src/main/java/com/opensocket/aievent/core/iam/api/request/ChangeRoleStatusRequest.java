package com.opensocket.aievent.core.iam.api.request;
import jakarta.validation.constraints.*;
public record ChangeRoleStatusRequest(@NotBlank String status) { }
