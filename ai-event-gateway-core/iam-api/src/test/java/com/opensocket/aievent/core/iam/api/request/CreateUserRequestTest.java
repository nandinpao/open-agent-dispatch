package com.opensocket.aievent.core.iam.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;
import org.junit.jupiter.api.Test;

class CreateUserRequestTest {
    @Test
    void blankUserIdIsNormalizedForServerGeneration() {
        CreateUserRequest request = new CreateUserRequest(
                "  ",
                " generated.user ",
                " generated.user@example.com ",
                " Generated User ",
                UserCreationMode.ADMIN_CREATED);

        assertNull(request.userId());
        assertEquals("SERVER_GENERATED_USER", request.authorizationTarget());
        assertEquals("generated.user", request.username());
        assertEquals("generated.user@example.com", request.email());
        assertEquals("Generated User", request.displayName());
    }

    @Test
    void controlledImportIdRemainsSupported() {
        CreateUserRequest request = new CreateUserRequest(
                " imported-user-1 ",
                "imported.user",
                null,
                "Imported User",
                UserCreationMode.LEGACY_IMPORT);

        assertEquals("imported-user-1", request.userId());
        assertEquals("imported-user-1", request.authorizationTarget());
    }
}
