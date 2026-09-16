package com.opensocket.aievent.core.iam.identity.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HumanUserTest {
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    @Test void invitationStartsPendingAndCanActivate() {
        HumanUser user = HumanUser.invited(new UserId("u-1"), new Username("David"),
                new EmailAddress("David@Example.com"), "David", "admin", NOW);
        assertEquals(AccountStatus.PENDING_ACTIVATION, user.status());
        assertEquals("david", user.username().normalizedValue());
        assertEquals("david@example.com", user.email().orElseThrow().normalizedValue());
        HumanUser active = user.changeStatus(AccountStatus.ACTIVE, "admin", "Invitation accepted", NOW.plusSeconds(1));
        assertTrue(active.active());
        assertEquals(2, active.version());
    }

    @Test void administrativelyCreatedUserStartsActiveWhileCredentialLifecycleRemainsSeparate() {
        HumanUser user = HumanUser.administrativelyCreated(new UserId("u-admin"), new Username("admin.user"),
                new EmailAddress("admin.user@example.com"), "Admin User", "admin", NOW);
        assertEquals(AccountStatus.ACTIVE, user.status());
        assertTrue(user.active());
        assertEquals(UserCreationMode.ADMIN_CREATED, user.creationMode());
    }

    @Test void deletedUserIsTerminal() {
        HumanUser user = HumanUser.administrativelyCreated(new UserId("u-2"), new Username("user2"), null,
                "User 2", "admin", NOW).changeStatus(AccountStatus.DELETED, "admin", "Privacy deletion", NOW.plusSeconds(1));
        IdentityDomainException error = assertThrows(IdentityDomainException.class, () ->
                user.changeStatus(AccountStatus.ACTIVE, "admin", "Restore", NOW.plusSeconds(2)));
        assertEquals(IdentityReasonCode.INVALID_STATUS_TRANSITION, error.reasonCode());
    }

    @Test void reservedUsernameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Username("ROOT"));
    }
}
