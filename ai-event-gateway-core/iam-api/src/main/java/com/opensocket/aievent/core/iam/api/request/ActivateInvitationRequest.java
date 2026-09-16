package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Consumes a one-time invitation and establishes the first canonical password. */
public record ActivateInvitationRequest(
        @NotBlank @Size(max = 1000) String token,
        @NotBlank @Size(min = 14, max = 256) String newPassword) {}
