package com.opensocket.aievent.core.iam.api.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.opensocket.aievent.core.iam.api.error.IamApiException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IamApiRequestContextTest {
    private final IamApiRequestContext context = new IamApiRequestContext(
            Optional.empty(),
            "corr-1",
            "idem-1",
            "administrative change",
            "127.0.0.1",
            "test",
            Instant.parse("2026-07-23T00:00:00Z"),
            java.util.Set.of());

    @Test
    void parsesStrongAndWeakNumericEtags() {
        assertEquals(7, context.requireExpectedVersion("\"7\""));
        assertEquals(8, context.requireExpectedVersion("W/\"8\""));
    }

    @Test
    void rejectsMissingOrInvalidEtagsWithStableErrors() {
        IamApiException missing = assertThrows(
                IamApiException.class,
                () -> context.requireExpectedVersion(""));
        assertEquals("IAM_IF_MATCH_REQUIRED", missing.errorCode());

        IamApiException invalid = assertThrows(
                IamApiException.class,
                () -> context.requireExpectedVersion("not-a-version"));
        assertEquals("IAM_IF_MATCH_INVALID", invalid.errorCode());
    }

    @Test
    void exposesRequiredMutationEvidence() {
        assertEquals("idem-1", context.requireIdempotencyKey());
        assertEquals("administrative change", context.requireAuditReason());
    }

    @Test
    void enforcesCredentialPermissionBoundary() {
        IamApiRequestContext bounded = new IamApiRequestContext(
                Optional.empty(), "corr-2", "", "", "127.0.0.1", "test",
                Instant.parse("2026-07-23T00:00:00Z"), java.util.Set.of("task.read"));
        bounded.requireCredentialPermission("task.read");
        IamApiException denied = assertThrows(IamApiException.class,
                () -> bounded.requireCredentialPermission("task.update"));
        assertEquals("AUTH_TOKEN_SCOPE_INSUFFICIENT", denied.errorCode());
    }
}
