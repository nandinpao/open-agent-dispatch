package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class P4PersistenceBoundaryGovernanceTest {
    private static final Pattern CORE_IMPORT = Pattern.compile(
            "^import\\s+(com\\.opensocket\\.aievent\\.core\\.[\\w.]+);",
            Pattern.MULTILINE);

    @Test
    void persistenceAutoConfigurationShouldUseOnlyApprovedStereotypes() throws IOException {
        Path root = repositoryRoot();
        Path platform = root.resolve("database-platform/src/main/java");
        String infrastructure = read(platform.resolve(
                "com/opensocket/aievent/database/config/DatabasePlatformAutoConfiguration.java"));
        String persistence = read(platform.resolve(
                "com/opensocket/aievent/database/config/DatabasePersistenceAutoConfiguration.java"));
        assertThat(infrastructure).doesNotContain("@ComponentScan");
        assertThat(persistence)
                .contains("basePackageClasses = DatabasePersistenceModule.class")
                .contains("useDefaultFilters = false")
                .contains("DatabaseRepositoryAdapter.class")
                .contains("DatabasePersistenceConverter.class");

        Path persistenceRoot = platform.resolve("com/opensocket/aievent/database/persistence");
        try (Stream<Path> files = Files.walk(persistenceRoot)) {
            List<String> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/repository/"))
                    .map(this::read)
                    .filter(source -> !source.contains("@DatabaseRepositoryAdapter"))
                    .toList();
            assertThat(violations).isEmpty();
        }
        try (Stream<Path> files = Files.walk(persistenceRoot)) {
            List<String> violations = files.filter(path -> path.toString().endsWith("PersistenceConverter.java"))
                    .filter(path -> !path.toString().contains("/spi/"))
                    .map(this::read)
                    .filter(source -> !source.contains("@DatabasePersistenceConverter"))
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void databasePlatformCoreImportsShouldMatchApprovedContractBaseline() throws IOException {
        Path root = repositoryRoot();
        Set<String> actual = new HashSet<>();
        Path platform = root.resolve("database-platform/src/main/java");
        try (Stream<Path> files = Files.walk(platform)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .forEach(source -> {
                        Matcher matcher = CORE_IMPORT.matcher(source);
                        while (matcher.find()) actual.add(matcher.group(1));
                    });
        }
        Set<String> approved = new HashSet<>();
        List<String> lines = Files.readAllLines(root.resolve(
                "architecture/baseline/p4-database-platform-contract-imports.csv"));
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (!line.isEmpty()) approved.add(line.split(",", 2)[0]);
        }
        assertThat(actual).isEqualTo(approved);
        assertThat(actual).noneMatch(type -> type.endsWith("Service")
                || type.endsWith("Facade")
                || type.endsWith("Controller")
                || type.endsWith("Properties")
                || type.endsWith("Configuration")
                || type.endsWith("Publisher")
                || type.endsWith("Client"));
    }

    @Test
    void applicationAndFeatureCodeShouldNotUseDaoPoOrPersistenceConverters() throws IOException {
        Path root = repositoryRoot();
        try (Stream<Path> modules = Files.list(root)) {
            List<Path> violations = modules
                    .filter(path -> Files.isRegularFile(path.resolve("pom.xml")))
                    .filter(path -> Files.isDirectory(path.resolve("src/main/java/com/opensocket/aievent/core")))
                    .filter(path -> !path.getFileName().toString().equals("data-model"))
                    .flatMap(module -> walk(module.resolve("src/main/java")))
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        String source = read(path);
                        return source.contains("com.opensocket.aievent.database.persistence")
                                && (source.contains(".dao.")
                                || source.contains(".po.")
                                || source.contains(".converter."));
                    })
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void dataModelShouldExposePersistenceModelsButNotPersistenceAdapters() throws IOException {
        Path root = repositoryRoot();
        Path dataModel = root.resolve("data-model/src/main/java");
        try (Stream<Path> files = Files.walk(dataModel)) {
            List<Path> adapterViolations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/repository/")
                            || path.toString().contains("/converter/")
                            || path.getFileName().toString().startsWith("Mybatis"))
                    .toList();
            assertThat(adapterViolations).isEmpty();
        }
    }

    @Test
    void featureDependenciesShouldBeOptionalAndTransitionalAliasesRemoved() throws IOException {
        Path root = repositoryRoot();
        String platformPom = read(root.resolve("database-platform/pom.xml"));
        for (String module : List.of(
                "contracts",
                "domain-events",
                "integration-events",
                "incident",
                "event-processing",
                "agent-control",
                "task-orchestration",
                "execution-control")) {
            Pattern block = Pattern.compile("<dependency>.*?<artifactId>" + Pattern.quote(module)
                    + "</artifactId>.*?<optional>true</optional>.*?</dependency>", Pattern.DOTALL);
            assertThat(block.matcher(platformPom).find()).isTrue();
        }
        assertThat(root.resolve(
                "control-plane-app/src/main/java/com/opensocket/aievent/core/store/CoreStoreMode.java"))
                .doesNotExist();
        assertThat(read(root.resolve("deploy/docker/docker-compose.core-hybrid-worker.yml")))
                .doesNotContain("STORE: POSTGRES");
        assertThat(read(root.resolve("control-plane-app/src/main/resources/application.yml")))
                .doesNotContain("DATABASE_PLATFORM_MAPPER_LOCATIONS")
                .doesNotContain("DATABASE_PLATFORM_MIGRATION_LOCATIONS");
    }

    private Stream<Path> walk(Path root) {
        if (!Files.isDirectory(root)) return Stream.empty();
        try {
            return Files.walk(root);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("architecture/module-candidates.csv"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Repository root not found");
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
