package com.opensocket.aievent.core.a2a.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HandoffSensitiveDataGuardTest {
    private final HandoffSensitiveDataGuard guard = new HandoffSensitiveDataGuard();

    @Test
    void rejectsNestedCredentialsAndRawPayloadContainers() {
        assertTrue(guard.containsForbiddenContent(Map.of(
                "safe", true,
                "nested", Map.of("dispatch_token", "opaque-token"))));
        assertTrue(guard.isForbiddenPath("request.credentials.apiKey"));
        assertTrue(guard.isForbiddenPath("source.rawPayload"));
    }

    @Test
    void acceptsApprovedBusinessContext() {
        assertFalse(guard.containsForbiddenContent(Map.of(
                "temperature", 42,
                "alarms", List.of("TEMP_HIGH"),
                "site", "FAB-01")));
    }

    @Test
    void rejectsUnsafeReferences() {
        assertThrows(IllegalArgumentException.class,
                () -> guard.requireSafeReference("Authorization: Bearer opaque"));
    }
}
