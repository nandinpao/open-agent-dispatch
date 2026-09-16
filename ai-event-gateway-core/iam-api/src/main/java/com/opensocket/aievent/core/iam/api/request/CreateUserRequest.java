package com.opensocket.aievent.core.iam.api.request;

import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Creates a human identity. The server generates a stable user ID when {@code userId}
 * is absent or blank. Supplying an ID remains supported for controlled imports and
 * backwards-compatible automation.
 */
public record CreateUserRequest(
        String userId,
        @NotBlank String username,
        @Email String email,
        @NotBlank String displayName,
        @NotNull UserCreationMode creationMode) {

    public CreateUserRequest {
        userId = normalizeOptional(userId);
        username = username == null ? null : username.trim();
        email = normalizeOptional(email);
        displayName = displayName == null ? null : displayName.trim();
    }

    public String authorizationTarget() {
        return userId == null ? "SERVER_GENERATED_USER" : userId;
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
