package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.callback.TaskCallbackRequest;
import com.opensocket.aievent.core.model.CoreDataModelModule;
import com.opensocket.aievent.database.persistence.incident.dao.IncidentDao;
import com.opensocket.aievent.database.persistence.incident.po.IncidentPo;

class P3DataModelModuleStructureTest {

    @Test
    void coreDataModelShouldOwnDaoPoAndStableDtoClasses() throws IOException {
        assertThat(CoreDataModelModule.class).isNotNull();
        assertThat(IncidentDao.class).isInterface();
        assertThat(IncidentPo.class).isNotNull();
        assertThat(TaskCallbackRequest.class).isNotNull();

        Path root = repositoryRoot();
        Path model = root.resolve("data-model");
        try (Stream<Path> files = Files.walk(model.resolve("src/main/java/com/opensocket/aievent/database/persistence"))) {
            assertThat(files.filter(path -> path.toString().endsWith("Dao.java"))).hasSize(24);
        }
        try (Stream<Path> files = Files.walk(model.resolve("src/main/java/com/opensocket/aievent/database/persistence"))) {
            assertThat(files.filter(path -> path.toString().endsWith("Po.java"))).hasSize(42);
        }
        try (Stream<Path> files = Files.walk(root.resolve("database-platform/src/main/resources/mybatis/postgresql"))) {
            assertThat(files.filter(path -> path.toString().endsWith("Dao.xml"))).hasSize(24);
        }
    }

    @Test
    void databasePlatformShouldNoLongerOwnDaoOrPoSourceFiles() throws IOException {
        Path root = repositoryRoot();
        Path platformJava = root.resolve("database-platform/src/main/java/com/opensocket/aievent/database/persistence");
        try (Stream<Path> files = Files.walk(platformJava)) {
            assertThat(files
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .filter(path -> path.endsWith("Dao.java") || path.endsWith("Po.java")))
                    .isEmpty();
        }
    }


    @Test
    void agentControlShouldDependOnCoreDataModelForGovernanceDiagnostics() throws IOException {
        Path root = repositoryRoot();
        String agentControlPom = Files.readString(root.resolve("agent-control/pom.xml"));
        assertThat(agentControlPom)
                .contains("<artifactId>data-model</artifactId>");
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
