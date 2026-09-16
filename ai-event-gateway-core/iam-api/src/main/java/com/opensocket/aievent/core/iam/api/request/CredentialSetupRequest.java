package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Administrator-selected delivery method for a one-time password setup/reset credential. */
public record CredentialSetupRequest(
        @NotBlank String deliveryMethod,
        @NotBlank @Size(max = 500) String reason) {
    public CredentialSetupRequest {
        deliveryMethod = deliveryMethod == null ? null : deliveryMethod.trim().toUpperCase(Locale.ROOT);
        if (deliveryMethod != null && !deliveryMethod.isBlank()
                && !deliveryMethod.equals("EMAIL")
                && !deliveryMethod.equals("MANUAL")
                && !deliveryMethod.equals("DEVELOPMENT_FILE")) {
            throw new IllegalArgumentException("IDENTITY_ACTIVATION_DELIVERY_METHOD_UNSUPPORTED");
        }
    }
}
