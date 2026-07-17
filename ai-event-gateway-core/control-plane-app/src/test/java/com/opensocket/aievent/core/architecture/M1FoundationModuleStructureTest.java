package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.contracts.CoreContractsModule;
import com.opensocket.aievent.core.kernel.CoreVersion;
import com.opensocket.aievent.database.DatabasePlatformModule;

class M1FoundationModuleStructureTest {
    @Test
    void foundationModulesShouldBeOnTheApplicationClasspath() {
        assertThat(CoreVersion.CURRENT).isEqualTo("1.0.0-p25.7.4-p5-callback-transition-governance-fix");
        assertThat(CoreContractsModule.class).isNotNull();
        assertThat(DatabasePlatformModule.class).isNotNull();
    }

    @Test
    void flywayAndMybatisResourcesShouldBeAggregatedByDatabasePlatform() {
        ClassLoader classLoader = getClass().getClassLoader();
        assertThat(classLoader.getResource("db/migration/V1__incident_store.sql")).isNotNull();
        assertThat(classLoader.getResource("db/migration/V16__observability_ops_indexes.sql")).isNotNull();
        assertThat(classLoader.getResource("mybatis/postgresql/incident/IncidentDao.xml")).isNotNull();
        assertThat(classLoader.getResource("mybatis/postgresql/adapter/AdapterActionDao.xml")).isNotNull();
    }

    @Test
    void appModuleShouldNotRetainMovedResourcesOrContracts() {
        Path root = findRepositoryRoot();
        List<Path> forbidden = List.of(
                root.resolve("control-plane-app/src/main/resources/db/migration"),
                root.resolve("control-plane-app/src/main/resources/mybatis/postgresql"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackRequest.java"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/NettyDispatchCommand.java"));
        assertThat(forbidden).allMatch(path -> !Files.exists(path));
    }

    private Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("architecture/module-candidates.csv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
