package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class P3PersistenceAdapterConsolidationTest {

    @Test
    void databasePlatformShouldOwnAllMybatisRepositoriesAndConverters() throws IOException {
        Path root = repositoryRoot();
        Path persistence = root.resolve(
                "database-platform/src/main/java/com/opensocket/aievent/database/persistence");

        List<Path> repositories;
        List<Path> converters;
        try (var paths = Files.walk(persistence)) {
            repositories = paths
                    .filter(path -> path.toString().contains("/repository/"))
                    .filter(path -> path.getFileName().toString().startsWith("Mybatis"))
                    .filter(path -> path.getFileName().toString().endsWith("Repository.java"))
                    .toList();
        }
        try (var paths = Files.walk(persistence)) {
            converters = paths
                    .filter(path -> path.toString().contains("/converter/"))
                    .filter(path -> path.getFileName().toString().endsWith("PersistenceConverter.java"))
                    .toList();
        }

        assertThat(repositories).hasSize(23);
        assertThat(converters).hasSize(23);
    }

    @Test
    void featureModulesShouldNotOwnMybatisAdaptersOrImportDatabasePlatform() throws IOException {
        Path root = repositoryRoot();
        List<Path> violations;
        try (var paths = Files.walk(root)) {
            violations = paths
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/src/main/java/com/opensocket/aievent/core/"))
                    .filter(path -> !path.toString().contains("/data-model/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().startsWith("Mybatis")
                            || read(path).contains("com.opensocket.aievent.database.persistence"))
                    .toList();
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void dataModelShouldOwnDaoPoContractsButNotMybatisAdaptersOrConverters() throws IOException {
        Path root = repositoryRoot();
        Path dataModel = root.resolve("data-model/src/main/java");

        List<Path> daoAndPoFiles;
        List<Path> adapterViolations;
        try (var paths = Files.walk(dataModel)) {
            daoAndPoFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/com/opensocket/aievent/database/persistence/"))
                    .filter(path -> path.toString().contains("/dao/") || path.toString().contains("/po/"))
                    .toList();
        }
        try (var paths = Files.walk(dataModel)) {
            adapterViolations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().startsWith("Mybatis")
                            || path.toString().contains("/repository/")
                            || path.toString().contains("/converter/")
                            || read(path).contains("@DatabaseRepositoryAdapter")
                            || read(path).contains("@DatabasePersistenceConverter"))
                    .toList();
        }

        assertThat(daoAndPoFiles).isNotEmpty();
        assertThat(adapterViolations).isEmpty();
    }

    @Test
    void dependencyDirectionShouldPointFromDatabasePlatformToFeaturePorts() throws IOException {
        Path root = repositoryRoot();
        String platformPom = read(root.resolve("database-platform/pom.xml"));
        List<String> featureModules = List.of(
                "domain-events",
                "integration-events",
                "incident",
                "event-processing",
                "agent-control",
                "task-orchestration",
                "execution-control",
                "execution-control");

        for (String module : featureModules) {
            assertThat(platformPom).contains("<artifactId>" + module + "</artifactId>");
            assertThat(read(root.resolve(module).resolve("pom.xml")))
                    .doesNotContain("<artifactId>database-platform</artifactId>");
        }
    }

    @Test
    void eventDecisionPortShouldBelongToEventProcessingRatherThanCoreApp() {
        Path root = repositoryRoot();
        assertThat(root.resolve(
                "data-model/src/main/java/com/opensocket/aievent/core/decision/EventDecisionRepository.java"))
                .exists();
        assertThat(root.resolve(
                "data-model/src/main/java/com/opensocket/aievent/core/decision/EventDecisionRecord.java"))
                .exists();
        assertThat(root.resolve(
                "control-plane-app/src/main/java/com/opensocket/aievent/core/decision/EventDecisionRepository.java"))
                .doesNotExist();
    }

    @Test
    void databaseAutoConfigurationShouldScanPersistenceAdapters() {
        Path root = repositoryRoot();
        String platformSource = read(root.resolve(
                "database-platform/src/main/java/com/opensocket/aievent/database/config/DatabasePlatformAutoConfiguration.java"));
        String persistenceSource = read(root.resolve(
                "database-platform/src/main/java/com/opensocket/aievent/database/config/DatabasePersistenceAutoConfiguration.java"));
        assertThat(platformSource).doesNotContain("@ComponentScan");
        assertThat(persistenceSource)
                .contains("basePackageClasses = DatabasePersistenceModule.class")
                .contains("useDefaultFilters = false")
                .contains("DatabaseRepositoryAdapter.class")
                .contains("DatabasePersistenceConverter.class");
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("architecture/module-candidates.csv"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Repository root not found");
        }
        return current;
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
