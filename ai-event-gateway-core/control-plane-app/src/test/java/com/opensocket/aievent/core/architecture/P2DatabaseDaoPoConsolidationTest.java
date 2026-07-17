package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.database.persistence.DatabasePersistenceModule;
import com.opensocket.aievent.core.model.CoreDataModelModule;
import com.opensocket.aievent.database.persistence.incident.dao.IncidentDao;
import com.opensocket.aievent.database.persistence.incident.po.IncidentPo;

class P2DatabaseDaoPoConsolidationTest {
    @Test
    void databasePlatformShouldOwnDomainScopedDaoPoAndXml() throws IOException {
        assertThat(DatabasePersistenceModule.class).isNotNull();
        assertThat(CoreDataModelModule.class).isNotNull();
        assertThat(IncidentDao.class).isInterface();
        assertThat(IncidentPo.class).isNotNull();
        Path root = repositoryRoot();
        Path model = root.resolve("data-model");
        Path platform = root.resolve("database-platform");
        try (Stream<Path> files = Files.walk(model.resolve(
                "src/main/java/com/opensocket/aievent/database/persistence"))) {
            assertThat(files.filter(path -> path.toString().endsWith("Dao.java"))).hasSize(24);
        }
        try (Stream<Path> files = Files.walk(model.resolve(
                "src/main/java/com/opensocket/aievent/database/persistence"))) {
            assertThat(files.filter(path -> path.toString().endsWith("Po.java"))).hasSize(42);
        }
        try (Stream<Path> files = Files.walk(platform.resolve(
                "src/main/resources/mybatis/postgresql"))) {
            assertThat(files.filter(path -> path.toString().endsWith("Dao.xml"))).hasSize(24);
        }
    }

    @Test
    void legacyDbSupportModuleAndPackagesShouldBeRemoved() throws IOException {
        Path root = repositoryRoot();
        assertThat(root.resolve("ai-event-gateway-core-db-support")).doesNotExist();
        assertThat(Files.readString(root.resolve("pom.xml")))
                .doesNotContain("ai-event-gateway-core-db-support");
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::isProductionJavaSource)
                    .map(this::read)
                    .filter(text -> text.contains(
                            "com.opensocket.aievent.core.infrastructure.mybatis")))
                    .isEmpty();
        }
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


    private boolean isProductionJavaSource(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return normalized.contains("/src/main/java/");
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
