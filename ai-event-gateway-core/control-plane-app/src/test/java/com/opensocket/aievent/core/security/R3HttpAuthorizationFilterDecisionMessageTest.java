package com.opensocket.aievent.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class R3HttpAuthorizationFilterDecisionMessageTest {
    @Test
    void stalePolicyUsesReauthenticationMessageAndUnauthorizedStatus() {
        assertEquals(401, R3HttpAuthorizationFilter.decisionHttpStatus("AUTH_POLICY_VERSION_STALE"));
        assertEquals(
                "The authenticated session security epoch is stale because authorization policy changed. Sign in again.",
                R3HttpAuthorizationFilter.decisionMessage("AUTH_POLICY_VERSION_STALE"));
    }

    @Test
    void permissionDeniedKeepsRoleBindingGuidance() {
        assertEquals(403, R3HttpAuthorizationFilter.decisionHttpStatus("AUTH_PERMISSION_DENIED"));
        assertEquals(
                "No effective Role Binding grants this Atomic Permission and Scope.",
                R3HttpAuthorizationFilter.decisionMessage("AUTH_PERMISSION_DENIED"));
    }
}
