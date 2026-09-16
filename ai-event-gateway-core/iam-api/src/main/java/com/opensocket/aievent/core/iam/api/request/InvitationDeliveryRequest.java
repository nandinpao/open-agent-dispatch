package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import java.util.Set;

/** Selects the delivery channel for a newly reissued Person invitation. */
public record InvitationDeliveryRequest(@NotBlank String deliveryMethod) {
    public InvitationDeliveryRequest {
        deliveryMethod = deliveryMethod == null ? null : deliveryMethod.trim().toUpperCase(Locale.ROOT);
        if (deliveryMethod != null && !Set.of("EMAIL", "MANUAL", "DEVELOPMENT_FILE").contains(deliveryMethod)) {
            throw new IllegalArgumentException("IDENTITY_ACTIVATION_DELIVERY_METHOD_UNSUPPORTED");
        }
    }
}
