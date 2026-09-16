package com.opensocket.aievent.core.iam.authentication.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RootBootstrapStateTest {
    @Test
    void requiresAllSteps() {
        var initial = RootBootstrapState.required();

        assertThrows(AuthenticationDomainException.class, () -> initial.complete(Instant.now()));

        var ready = initial
                .markPasswordConfigured()
                .markMfaConfigured()
                .markTenantCreated()
                .markTenantAdminCreated();

        assertEquals(RootBootstrapState.Status.COMPLETED, ready.complete(Instant.now()).status());
    }
}
