package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.callback.TaskTerminalActionPort;
import com.opensocket.aievent.core.dispatch.CoreExecutionControlModule;
import com.opensocket.aievent.core.dispatch.ExecutionControlFacade;
import com.opensocket.aievent.core.dispatch.NettyDispatchPort;

class M4ExecutionControlModuleStructureTest {
    @Test
    void executionControlModuleShouldBeOnApplicationClasspath() {
        assertThat(CoreExecutionControlModule.class).isNotNull();
        assertThat(ExecutionControlFacade.class).isInterface();
        assertThat(NettyDispatchPort.class).isInterface();
        assertThat(TaskTerminalActionPort.class).isInterface();
    }

    @Test
    void appModuleShouldNotRetainMovedExecutionPackages() {
        Path root = findRepositoryRoot();
        List<Path> forbidden = List.of(
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/callback"));
        assertThat(forbidden).allMatch(path -> !Files.exists(path));
    }

    @Test
    void executionControlMustUsePortsInsteadOfAppImplementationsOrTaskRepository() throws IOException {
        Path root = findRepositoryRoot();
        Path source = root.resolve("execution-control/src/main/java");
        String combined;
        try (var files = Files.walk(source)) {
            combined = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
        assertThat(combined).contains("NettyDispatchPort");
        assertThat(combined).contains("TaskTerminalActionPort");
        assertThat(combined).contains("ExecutionMetricsPort");
        assertThat(combined).contains("TaskOrchestrationFacade");
        assertThat(combined).doesNotContain("AdapterActionService");
        assertThat(combined).doesNotContain("CoreMetricsService");
        assertThat(combined).doesNotContain("TaskRepository");
    }

    @Test
    void taskOrchestrationMustOnlyExposeDispatchPortWithoutExecutionModuleDependency() throws IOException {
        Path root = findRepositoryRoot();
        Path taskSources = root.resolve("task-orchestration/src/main/java");
        String combined;
        try (var files = Files.walk(taskSources)) {
            combined = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
        assertThat(combined).contains("TaskDispatchPort");
        assertThat(combined).doesNotContain("DispatchRequestService");
        assertThat(combined).doesNotContain("DispatchExecutionService");
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
