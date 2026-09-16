package com.opensocket.aievent.core.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.opensocket.aievent.core.api.contract.ApiPermissionPointResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase3JPermissionBoundaryTest {
    @Test
    void releaseReadinessUsesDedicatedPostgresqlCatalogPermission() throws Exception {
        var resolver = new ApiPermissionPointResolver();
        String permission = resolver.resolve("GET", "/api/integrations/phase3-release-readiness");
        assertThat(permission).isEqualTo("integration.phase3_release.read");

        String migration = Files.readString(Path.of("../database-platform/src/main/resources/db/migration/"
                + "V66__phase3j_operations_release_evidence.sql"));
        assertThat(migration).contains("integration.phase3_release.read");
        assertThat(migration).contains("integration.phase3_release.manage");
    }
}
