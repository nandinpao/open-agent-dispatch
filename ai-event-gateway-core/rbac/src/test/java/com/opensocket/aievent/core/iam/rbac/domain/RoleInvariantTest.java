package com.opensocket.aievent.core.iam.rbac.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.opensocket.aievent.core.iam.security.contract.PrincipalRef;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoleInvariantTest {
    private static final Instant NOW = Instant.parse("2026-07-23T00:00:00Z");

    @Test void customRoleMustBeTenantOwnedAndMutable() {
        Role role = Role.customTenantRole(new RoleId("role-custom"), "tenant-a",
                new RoleCode("CUSTOM_AUDITOR"), "Custom Auditor", "", "admin", NOW);
        assertEquals(RoleType.CUSTOM_TENANT_ROLE, role.roleType());
        assertEquals(Optional.of("tenant-a"), role.tenantId());
        assertFalse(role.systemManaged());
    }

    @Test void customPlatformRoleMustBeInstanceOwnedAndMutable() {
        Role role = Role.customPlatformRole(new RoleId("role-platform-custom"),
                new RoleCode("PLATFORM_SUPPORT"), "Platform Support", "", "root", NOW);
        assertEquals(RoleType.CUSTOM_PLATFORM_ROLE, role.roleType());
        assertTrue(role.tenantId().isEmpty());
        assertTrue(role.platformOwned());
        assertFalse(role.systemManaged());
    }

    @Test void instanceRootCannotReceiveRoleBinding() {
        PrincipalRef root = new PrincipalRef(PrincipalRef.PrincipalType.INSTANCE_ROOT, "root");
        RbacDomainException exception = assertThrows(RbacDomainException.class,
                () -> PrincipalRoleBinding.create("binding-1", root, new RoleId("system-admin"),
                        ScopeRef.instance(), NOW, null, "root", NOW));
        assertEquals(RbacReasonCode.ROLE_BINDING_PRINCIPAL_FORBIDDEN, exception.reasonCode());
    }

    @Test void systemRoleCannotBeReconstitutedAsTenantOwned() {
        assertThrows(IllegalArgumentException.class,
                () -> Role.reconstitute(new RoleId("bad"), Optional.of("tenant-a"),
                        new RoleCode("SYSTEM_ADMIN"), "System Admin", "", RoleType.SYSTEM_ROLE,
                        RoleStatus.ACTIVE, true, NOW, NOW, "system", "system", 1));
    }
}
