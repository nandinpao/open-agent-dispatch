package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.database.DatabasePlatformModule;
import com.opensocket.aievent.database.config.DatabasePersistenceAutoConfiguration;
import com.opensocket.aievent.database.config.DatabasePlatformAutoConfiguration;
import com.opensocket.aievent.database.config.DatabasePlatformProperties;
import com.opensocket.aievent.database.mybatis.typehandler.JsonMapTypeHandler;

class P0DatabasePlatformStructureTest {

    @Test
    void databasePlatformBootstrapTypesShouldBeOnCoreClasspath() {
        assertThat(DatabasePlatformModule.class).isNotNull();
        assertThat(DatabasePlatformAutoConfiguration.class).isNotNull();
        assertThat(DatabasePersistenceAutoConfiguration.class).isNotNull();
        assertThat(DatabasePlatformProperties.class).isNotNull();
        assertThat(JsonMapTypeHandler.class).isNotNull();
    }

    @Test
    void coreAppShouldUseTheDatabasePlatformBoundary() throws IOException {
        String pom = Files.readString(repositoryRoot().resolve("control-plane-app/pom.xml"));
        assertThat(pom).contains("database-platform");
        assertThat(pom).doesNotContain("ai-event-gateway-core-db-support");
        assertThat(pom).doesNotContain("ai-event-gateway-core-db-migration");
    }

    @Test
    void platformShouldOwnSharedUtilityAndFlywayWithoutTheLegacyMigrationArtifact() throws IOException {
        String pom = Files.readString(repositoryRoot().resolve("database-platform/pom.xml"));
        assertThat(pom).doesNotContain("ai-event-gateway-core-db-support");
        assertThat(pom).doesNotContain("ai-event-gateway-core-db-migration");
        assertThat(pom).contains("<artifactId>database</artifactId>");
        assertThat(pom).contains("<artifactId>flyway-core</artifactId>");
        assertThat(repositoryRoot().resolve("database-platform/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")).exists();
    }

    private Path repositoryRoot() {
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
