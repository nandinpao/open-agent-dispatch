package com.opensocket.aievent.core.iam.organization.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TenantTest {
    @Test void decommissioningIsTerminal() {
        Instant now = Instant.parse("2026-07-23T00:00:00Z");
        Tenant tenant = Tenant.provision(new TenantId("tenant-a"), "A", "Tenant A", "", ZoneId.of("Asia/Taipei"),
                Locale.ENGLISH, "TW", "root", now).changeStatus(TenantStatus.ACTIVE, "root", now.plusSeconds(1))
                .changeStatus(TenantStatus.DECOMMISSIONING, "root", now.plusSeconds(2));
        assertThrows(OrganizationDomainException.class, () -> tenant.changeStatus(TenantStatus.ACTIVE, "root", now.plusSeconds(3)));
    }
}
