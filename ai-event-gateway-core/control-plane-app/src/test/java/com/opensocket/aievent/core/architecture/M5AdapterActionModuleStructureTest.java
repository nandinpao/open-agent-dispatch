package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.action.AdapterActionFacade;
import com.opensocket.aievent.core.action.AdapterActionMetricsPort;
import com.opensocket.aievent.core.action.CoreAdapterActionModule;
import com.opensocket.aievent.core.action.TaskTerminalEventHandler;

class M5AdapterActionModuleStructureTest {
    @Test
    void adapterActionModuleShouldBeOnApplicationClasspath() {
        assertThat(CoreAdapterActionModule.class).isNotNull();
        assertThat(AdapterActionFacade.class).isInterface();
        assertThat(AdapterActionMetricsPort.class).isInterface();
        assertThat(TaskTerminalEventHandler.class).isNotNull();
    }

    @Test
    void appModuleShouldNotRetainMovedAdapterActionSourcesOrControllers() {
        Path root = findRepositoryRoot();
        assertThat(root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/action"))
                .doesNotExist();
        assertThat(root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/api/AdapterActionController.java"))
                .doesNotExist();
        assertThat(root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/api/InternalAdapterActionWorkerController.java"))
                .doesNotExist();
    }

    @Test
    void adapterActionMustUseFacadesAndPortsInsteadOfAppImplementations() throws IOException {
        Path root = findRepositoryRoot();
        Path source = root.resolve("adapter-action/src/main/java");
        String combined;
        try (var files = Files.walk(source)) {
            combined = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
        assertThat(combined).contains("IncidentFacade");
        assertThat(combined).contains("TaskTerminalEventHandler");
        assertThat(combined).contains("AdapterActionMetricsPort");
        assertThat(combined).doesNotContain("IncidentRepository");
        assertThat(combined).doesNotContain("CoreMetricsService");
        assertThat(combined).doesNotContain("TaskCallbackService");
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
