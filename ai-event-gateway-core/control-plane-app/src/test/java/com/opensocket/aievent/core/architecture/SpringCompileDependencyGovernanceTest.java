package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpringCompileDependencyGovernanceTest {

    @Test
    void modulesUsingSpringAnnotationsShouldDeclareCompileDependenciesDirectly() throws IOException {
        Path root = repositoryRoot();

        assertDependencies(root, "domain-events",
                "spring-context", "spring-boot", "spring-boot-autoconfigure");
        assertDependencies(root, "integration-events",
                "spring-context", "spring-boot", "spring-boot-autoconfigure");
        assertDependencies(root, "incident",
                "spring-context", "spring-tx", "spring-boot", "spring-boot-autoconfigure");
        assertDependencies(root, "agent-control",
                "spring-context", "spring-tx", "spring-boot-autoconfigure");
        assertDependencies(root, "task-orchestration",
                "spring-context", "spring-tx", "spring-boot", "spring-boot-autoconfigure");
        assertDependencies(root, "execution-control",
                "spring-context", "spring-tx", "spring-boot", "spring-boot-autoconfigure");
        assertDependencies(root, "event-processing", "spring-tx");
        assertDependencies(root, "adapter-action", "spring-tx");
        assertDependencies(root, "control-plane-app", "spring-tx");
    }

    @Test
    void domainEventsShouldNotRelyOnTestScopeForSpringRuntimeAnnotations() throws IOException {
        Path root = repositoryRoot();
        Path pom = root.resolve("domain-events/pom.xml");
        String source = Files.readString(pom);

        assertThat(source)
                .contains("<artifactId>spring-context</artifactId>")
                .contains("<artifactId>spring-boot</artifactId>")
                .contains("<artifactId>spring-boot-autoconfigure</artifactId>");

        List<String> mainImports = new ArrayList<>();
        Path main = root.resolve("domain-events/src/main/java");
        try (var paths = Files.walk(main)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path);
                            if (text.contains("org.springframework.")) {
                                mainImports.add(root.relativize(path).toString());
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
        assertThat(mainImports).isNotEmpty();
    }

    private void assertDependencies(Path root, String module, String... artifactIds) throws IOException {
        String pom = Files.readString(root.resolve(module).resolve("pom.xml"));
        for (String artifactId : artifactIds) {
            assertThat(pom)
                    .as("%s must directly declare %s", module, artifactId)
                    .contains("<artifactId>" + artifactId + "</artifactId>");
        }
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
}
