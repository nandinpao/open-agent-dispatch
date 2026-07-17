package com.opensocket.aievent.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminRoleStage7LegacySupportTest {

    @Test
    void onlySupportRoleHasLegacyReadPermission() {
        assertThat(AdminRole.SUPPORT.permissions()).contains(AdminPermission.SUPPORT_LEGACY_READ);
        assertThat(AdminRole.ADMIN.permissions()).doesNotContain(AdminPermission.SUPPORT_LEGACY_READ);
        assertThat(AdminRole.VIEWER.permissions()).doesNotContain(AdminPermission.SUPPORT_LEGACY_READ);
    }
}
