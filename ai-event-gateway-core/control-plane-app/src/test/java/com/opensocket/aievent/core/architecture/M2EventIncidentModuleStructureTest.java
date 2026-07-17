package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.incident.CoreIncidentModule;
import com.opensocket.aievent.core.incident.IncidentFacade;
import com.opensocket.aievent.core.processing.CoreEventProcessingModule;
import com.opensocket.aievent.core.processing.EventProcessingFacade;

class M2EventIncidentModuleStructureTest {
    @Test
    void eventAndIncidentModulesShouldBeOnApplicationClasspath() {
        assertThat(CoreEventProcessingModule.class).isNotNull();
        assertThat(CoreIncidentModule.class).isNotNull();
        assertThat(EventProcessingFacade.class).isInterface();
        assertThat(IncidentFacade.class).isInterface();
    }

    @Test
    void appModuleShouldNotRetainMovedFeaturePackages() {
        Path root = findRepositoryRoot();
        List<Path> forbidden = List.of(
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/normalize"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/fingerprint"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/dedup"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/incident"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/summary"));
        assertThat(forbidden).allMatch(path -> !Files.exists(path));
    }

    @Test
    void eventProcessingModuleShouldUseIncidentFacadeInsteadOfIncidentRepositories() throws IOException {
        Path root = findRepositoryRoot();
        Path source = root.resolve("event-processing/src/main/java");
        String combined;
        try (var files = Files.walk(source)) {
            combined = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
        assertThat(combined).contains("IncidentFacade");
        assertThat(combined).doesNotContain("IncidentRepository");
        assertThat(combined).doesNotContain("IncidentOccurrenceSummaryRepository");
    }

    @Test
    void decisionEngineShouldCoordinateThroughEventProcessingFacade() {
        Path root = findRepositoryRoot();
        String source = read(root.resolve(
                "control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java"));
        assertThat(source).contains("EventProcessingFacade");
        assertThat(source).doesNotContain("IncidentManager");
        assertThat(source).doesNotContain("IncidentOccurrenceSummaryRepository");
        assertThat(source).doesNotContain("DedupStateStore");
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

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }
}
