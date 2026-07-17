package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.agent.AgentDirectoryFacade;
import com.opensocket.aievent.core.agent.CoreAgentControlModule;
import com.opensocket.aievent.core.task.CoreTaskOrchestrationModule;
import com.opensocket.aievent.core.task.TaskOrchestrationFacade;

class M3AgentTaskModuleStructureTest {
    @Test
    void agentAndTaskModulesShouldBeOnApplicationClasspath() {
        assertThat(CoreAgentControlModule.class).isNotNull();
        assertThat(CoreTaskOrchestrationModule.class).isNotNull();
        assertThat(AgentDirectoryFacade.class).isInterface();
        assertThat(TaskOrchestrationFacade.class).isInterface();
    }

    @Test
    void appModuleShouldNotRetainMovedFeaturePackages() {
        Path root = findRepositoryRoot();
        List<Path> forbidden = List.of(
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/agent"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/gateway"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/task"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/routing"),
                root.resolve("control-plane-app/src/main/java/com/opensocket/aievent/core/assignment"));
        assertThat(forbidden).allMatch(path -> !Files.exists(path));
    }

    @Test
    void taskModuleShouldUseAgentFacadeInsteadOfAgentRepository() throws IOException {
        Path root = findRepositoryRoot();
        Path source = root.resolve("task-orchestration/src/main/java");
        String combined;
        try (var files = Files.walk(source)) {
            combined = files.filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
        assertThat(combined).contains("AgentDirectoryFacade");
        assertThat(combined).doesNotContain("AgentDirectoryRepository");
    }

    @Test
    void decisionEngineShouldUseTaskOrchestrationFacade() {
        Path root = findRepositoryRoot();
        String source = read(root.resolve(
                "control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java"));
        assertThat(source).contains("TaskOrchestrationFacade");
        assertThat(source).doesNotContain("TaskDecisionService");
        assertThat(source).doesNotContain("TaskAssignmentService");
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
