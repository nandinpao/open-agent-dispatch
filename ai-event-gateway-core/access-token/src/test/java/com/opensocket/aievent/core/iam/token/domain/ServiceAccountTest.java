package com.opensocket.aievent.core.iam.token.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ServiceAccountTest {
    @Test
    void requiresCidrAndCapsTokenTtl() {
        Instant now=Instant.parse("2026-07-23T00:00:00Z");
        assertThrows(TokenDomainException.class,()->ServiceAccount.create(
                "t",new ServiceAccountId("s"),"svc","","u","d",
                new TokenScope(Set.of(),Set.of(),Set.of(),Set.of()),Duration.ofDays(30),2,60,
                now.plus(Duration.ofDays(30)),"a",now));
        assertThrows(TokenDomainException.class,()->ServiceAccount.create(
                "t",new ServiceAccountId("s"),"svc","","u","d",
                new TokenScope(Set.of(),Set.of(),Set.of(),Set.of("10.0.0.0/8")),Duration.ofDays(91),2,60,
                now.plus(Duration.ofDays(30)),"a",now));
    }

    @Test
    void persistsCanonicalMachineAndCredentialBoundary() {
        Instant now=Instant.parse("2026-08-13T00:00:00Z");
        ServiceAccount account=ServiceAccount.create(
                "tenant-a",new ServiceAccountId("svc-erp"),"ERP","","u","d",
                new TokenScope(Set.of("events.intake"),Set.of("opendispatch-event-api"),Set.of("/api/events/"),Set.of("10.0.0.0/8")),
                Set.of("events.intake"),Set.of("ERP-TW-PROD"),Duration.ofDays(30),3,
                Duration.ofDays(180),2,300,now.plus(Duration.ofDays(90)),"admin",now);
        assertEquals(Set.of("events.intake"),account.machineScopes());
        assertEquals(Set.of("ERP-TW-PROD"),account.allowedSourceSystems());
        assertEquals(Duration.ofDays(180),account.credentialMaxTtl());
        assertEquals(2,account.maxActiveCredentials());
    }
}
