package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Confirms a one-time TOTP enrollment ceremony for Root or Human User setup.
 *
 * Recovery-code acknowledgement is captured at the point where the codes are
 * still visible. This prevents the UI from asserting acknowledgement later on
 * behalf of the operator.
 */
public record ConfirmMfaEnrollmentRequest(
        @NotBlank String methodId,
        @Pattern(regexp = "[0-9]{6}") String code,
        @AssertTrue boolean acknowledgeRecoveryCodesSaved,
        @Positive long expectedVersion) {}