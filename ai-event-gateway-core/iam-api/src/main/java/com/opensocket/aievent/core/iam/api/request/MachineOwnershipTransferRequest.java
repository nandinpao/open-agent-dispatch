package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;

/** Transfers accountable machine and Agent ownership before a Person lifecycle change. */
public record MachineOwnershipTransferRequest(
        @NotBlank String toUserId,
        @NotBlank String reason) {}
