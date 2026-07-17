package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Prevents accidental reintroduction of Jackson 2 databind into the Spring Boot 4 runtime.
 *
 * <p>Two narrow exceptions are intentional:
 * <ul>
 *   <li>jackson-annotations remains on the com.fasterxml.jackson package;</li>
 *   <li>SharedRedissonDedupAtomicContainerTest uses Jackson 2 only when constructing the
 *       external com.agitg:redisson-client 1.0.0 RedissonAccess compatibility boundary.</li>
 * </ul>
 */
class Jackson3MigrationGovernanceTest {

    private static final List<String> FORBIDDEN_JAVA_IMPORTS = List.of(
            "import com.fasterxml.jackson.databind.",
            "import com.fasterxml.jackson.core.type.",
            "import com.fasterxml.jackson.core.JsonProcessingException;",
            "import com.fasterxml.jackson.datatype.");

    private static final String SHARED_REDISSON_COMPATIBILITY_TEST =
            "control-plane-app/src/test/java/com/opensocket/aievent/core/container/"
                    + "SharedRedissonDedupAtomicContainerTest.java";

    @Test
    void javaSourcesShouldUseJackson3DatabindAndCorePackages() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.getFileName().toString().equals("Jackson3MigrationGovernanceTest.java"))
                    .filter(path -> !root.relativize(path).toString().replace('\\', '/')
                            .equals(SHARED_REDISSON_COMPATIBILITY_TEST))
                    .forEach(path -> {
                        String source = read(path);
                        for (String forbidden : FORBIDDEN_JAVA_IMPORTS) {
                            if (source.contains(forbidden)) {
                                violations.add(root.relativize(path) + " contains " + forbidden);
                            }
                        }
                    });
        }

        assertThat(violations)
                .as("Spring Boot 4 code must use Jackson 3 except the documented shared-utility Redisson test boundary")
                .isEmpty();
    }

    @Test
    void modulePomsShouldNotDeclareJackson2DatabindOrJavaTimeModules() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(root)) {
            files.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .forEach(path -> {
                        String pom = read(path);
                        if (pom.contains("<groupId>com.fasterxml.jackson.core</groupId>")
                                && pom.contains("<artifactId>jackson-databind</artifactId>")) {
                            violations.add(root.relativize(path) + " declares Jackson 2 databind");
                        }
                        if (pom.contains("<groupId>com.fasterxml.jackson.datatype</groupId>")) {
                            violations.add(root.relativize(path) + " declares a Jackson 2 datatype module");
                        }
                    });
        }

        assertThat(violations)
                .as("Jackson 3 embeds Java time support and uses tools.jackson coordinates")
                .isEmpty();
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
