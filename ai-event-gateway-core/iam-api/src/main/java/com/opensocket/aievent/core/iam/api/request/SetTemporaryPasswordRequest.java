package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Explicit administrator password replacement. Plaintext is write-only and never returned. */
public record SetTemporaryPasswordRequest(
        @NotBlank @Size(min = 14, max = 1024) String temporaryPassword,
        @NotBlank @Size(max = 500) String reason) {
    @Override
    public String toString() {
        return "SetTemporaryPasswordRequest[temporaryPassword=<redacted>, reason=" + reason + "]";
    }
}
