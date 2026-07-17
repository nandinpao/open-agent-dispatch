package com.opensocket.aievent.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class AdminPrincipalTest {
    @Test
    void exposesRolesPermissionsAndTenantSelection() {
        AdminPrincipal principal = AdminPrincipal.from(new AdminAccount(
                "user-1", "admin", "Admin", "{noop}secret", Set.of(AdminRole.ADMIN),
                Set.of("tenant-a", "tenant-b"), "tenant-a", true));

        assertThat(principal.selectedTenantId()).isEqualTo("tenant-a");
        assertThat(principal.permissions()).contains(AdminPermission.IDENTITY_ADMIN.code());
        assertThat(principal.getAuthorities()).extracting(Object::toString)
                .contains("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_RECOVERY_ADMIN");

        assertThat(principal.withSelectedTenant("tenant-b").selectedTenantId()).isEqualTo("tenant-b");
        assertThatThrownBy(() -> principal.withSelectedTenant("tenant-x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
