package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.core.events.AdapterActionRequestedEvent;
import com.opensocket.aievent.core.events.DispatchDeadLetteredEvent;
import com.opensocket.aievent.core.events.IncidentEscalatedEvent;
import com.opensocket.aievent.core.events.ModuleEvent;
import com.opensocket.aievent.core.events.TaskTerminalEvent;
import com.opensocket.aievent.core.outbox.ModuleEventPublisher;
import com.opensocket.aievent.core.outbox.OutboxEventRepository;

class M7DomainEventsOutboxStructureTest {

    @Test
    void eventContractsAndOutboxBoundaryShouldBeOnClasspath() {
        assertThat(ModuleEvent.class).isInterface();
        assertThat(IncidentEscalatedEvent.class).isRecord();
        assertThat(TaskTerminalEvent.class).isRecord();
        assertThat(AdapterActionRequestedEvent.class).isRecord();
        assertThat(DispatchDeadLetteredEvent.class).isRecord();
        assertThat(ModuleEventPublisher.class).isInterface();
        assertThat(OutboxEventRepository.class).isInterface();
    }

    @Test
    void lifecycleCoordinatorsMustNotImportRepositoriesOrDaos() throws IOException {
        Path lifecycle = repositoryRoot().resolve(
                "control-plane-app/src/main/java/com/opensocket/aievent/core/lifecycle");
        try (var sources = Files.walk(lifecycle)) {
            String combined = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(this::read)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(combined).doesNotContain("Repository;");
            assertThat(combined).doesNotContain("Dao;");
        }
    }

    @Test
    void repositoryExceptionRegistryShouldContainOnlyApprovedP2TransitionRows() throws IOException {
        var lines = Files.readAllLines(
                repositoryRoot().resolve("architecture/repository-access-exceptions.csv"));
        assertThat(lines.get(0)).isEqualTo(
                "source_file,imported_type,reason,removal_phase");
        assertThat(lines).hasSize(5);
        assertThat(lines.subList(1, lines.size()))
                .allSatisfy(line -> assertThat(line).contains("P2-"));
        assertThat(lines)
                .anyMatch(line -> line.contains("P2-timeline-query-port"))
                .anyMatch(line -> line.contains("P2-callback-query-port"));
    }

    @Test
    void executionControlShouldPublishTerminalEventsWithoutAdapterImplementationDependency() {
        String callback = read(repositoryRoot().resolve(
                "execution-control/src/main/java/"
                        + "com/opensocket/aievent/core/callback/TaskCallbackService.java"));
        assertThat(callback).contains("ModuleEventPublisher");
        assertThat(callback).contains("TaskTerminalEvent");
        assertThat(callback).doesNotContain("AdapterActionService");

        assertThat(repositoryRoot().resolve(
                "adapter-action/src/main/java/"
                        + "com/opensocket/aievent/core/action/TaskTerminalEventHandler.java"))
                .exists();
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
