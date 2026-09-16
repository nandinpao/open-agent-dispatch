package com.opensocket.aievent.core.iam.identity.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RootIdentityTest {
    @Test void rootUsesSeparateLifecycle() {
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        RootIdentity root = RootIdentity.bootstrapPending("installer", now);
        RootIdentity active = root.changeStatus(RootIdentityStatus.ACTIVE, "root", "Bootstrap complete", now.plusSeconds(1));
        RootIdentity locked = active.changeStatus(RootIdentityStatus.LOCKED_AFTER_RECOVERY, "system", "Recovery ended", now.plusSeconds(2));
        assertEquals(RootIdentityId.INSTANCE, locked.rootIdentityId());
        assertEquals(RootIdentityStatus.LOCKED_AFTER_RECOVERY, locked.status());
        assertEquals(3, locked.version());
    }
}
