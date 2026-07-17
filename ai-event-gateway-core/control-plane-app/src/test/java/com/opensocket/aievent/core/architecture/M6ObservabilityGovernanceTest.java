package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentControlOperationalQuery;
import com.opensocket.aievent.core.dispatch.ExecutionOperationalQuery;
import com.opensocket.aievent.core.incident.IncidentOperationalQuery;
import com.opensocket.aievent.core.observability.CoreObservabilityModule;
import com.opensocket.aievent.core.processing.EventProcessingOperationalQuery;
import com.opensocket.aievent.core.task.TaskOperationalQuery;

class M6ObservabilityGovernanceTest {
    @Test
    void observabilityModuleAndOperationalQueryPortsShouldBeOnClasspath() {
        assertThat(CoreObservabilityModule.class).isNotNull();
        assertThat(IncidentOperationalQuery.class).isInterface();
        assertThat(AgentControlOperationalQuery.class).isInterface();
        assertThat(TaskOperationalQuery.class).isInterface();
        assertThat(ExecutionOperationalQuery.class).isInterface();
        assertThat(EventProcessingOperationalQuery.class).isInterface();
    }

    @Test
    void appShouldNotRetainObservabilityImplementationSources() {
        Path root = repositoryRoot();
        assertThat(root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/observability"))
                .doesNotExist();
        assertThat(root.resolve("observability/src/main/java/com/opensocket/aievent/core/observability/CoreMetricsService.java"))
                .exists();
    }

    @Test
    void apiAndObservabilitySourcesMustNotImportRepositoriesOrDaos() throws IOException {
        Path root = repositoryRoot();
        List<Path> roots = List.of(
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/api"),
                root.resolve("adapter-action/src/main/java/com/opensocket/aievent/core/api"),
                root.resolve("observability/src/main/java"));
        for (Path sourceRoot : roots) {
            if (!Files.exists(sourceRoot)) continue;
            try (var files = Files.walk(sourceRoot)) {
                String combined = files.filter(path -> path.toString().endsWith(".java"))
                        .map(this::read)
                        .reduce("", (left, right) -> left + "\n" + right);
                assertThat(combined).doesNotContain("Repository;");
                assertThat(combined)
                        .doesNotContain(".dao.");
            }
        }
    }

    @Test
    void ownershipRegistriesShouldExist() {
        Path root = repositoryRoot();
        assertThat(root.resolve("architecture/table-ownership.csv")).exists();
        assertThat(root.resolve("architecture/repository-access-exceptions.csv")).exists();
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

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
