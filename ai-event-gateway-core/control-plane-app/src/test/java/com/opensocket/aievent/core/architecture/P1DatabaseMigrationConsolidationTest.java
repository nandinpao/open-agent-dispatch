package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class P1DatabaseMigrationConsolidationTest {

    @Test
    void databasePlatformShouldOwnAllFlywayMigrations() throws IOException {
        Path root = repositoryRoot();
        Path migrationDirectory = root.resolve(
                "database-platform/src/main/resources/db/migration");
        assertThat(migrationDirectory).isDirectory();
        try (Stream<Path> files = Files.list(migrationDirectory)) {
            assertThat(files.filter(path -> path.getFileName().toString().matches("V\\d+__.+\\.sql")))
                    .hasSizeGreaterThanOrEqualTo(19);
        }
        assertThat(migrationDirectory.resolve("V1__incident_store.sql")).exists();
        assertThat(migrationDirectory.resolve("V19__integration_event_outbox.sql")).exists();
        assertThat(getClass().getClassLoader().getResource("db/migration/V1__incident_store.sql"))
                .isNotNull();
        assertThat(getClass().getClassLoader().getResource("db/migration/V19__integration_event_outbox.sql"))
                .isNotNull();
    }

    @Test
    void legacyMigrationModuleShouldBeRemovedFromTheReactorAndDependencies() throws IOException {
        Path root = repositoryRoot();
        assertThat(root.resolve("ai-event-gateway-core-db-migration")).doesNotExist();
        assertThat(Files.readString(root.resolve("pom.xml")))
                .doesNotContain("ai-event-gateway-core-db-migration");
        assertThat(Files.readString(root.resolve("control-plane-app/pom.xml")))
                .doesNotContain("ai-event-gateway-core-db-migration");
        assertThat(Files.readString(root.resolve("database-platform/pom.xml")))
                .doesNotContain("ai-event-gateway-core-db-migration");
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
