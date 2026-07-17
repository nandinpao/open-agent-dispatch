package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SharedUtilityMybatisPersistenceGovernanceTest {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+);");

    @Test
    void productionFeatureSourcesMustNotUseJdbcTemplate() throws IOException {
        Path root = repositoryRoot();
        List<Path> violations;
        try (var paths = Files.walk(root)) {
            violations = paths.filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("JdbcTemplate")
                            || read(path).contains("NamedParameterJdbcTemplate"))
                    .toList();
        }
        assertThat(violations)
                .as("Feature SQL must be implemented through SharedUtility/MyBatis XML")
                .isEmpty();
    }

    @Test
    void everyDatabasePlatformDaoMustHaveDomainScopedXmlResource() throws IOException {
        Path root = repositoryRoot();
        Path daoRoot = root.resolve(
                "database-platform/src/main/java/com/opensocket/aievent/database/persistence");
        Path xmlRoot = root.resolve(
                "database-platform/src/main/resources/mybatis/postgresql");
        try (var paths = Files.walk(daoRoot)) {
            for (Path dao : paths.filter(path -> path.toString().endsWith("Dao.java")).toList()) {
                String source = read(dao);
                Matcher matcher = PACKAGE.matcher(source);
                assertThat(matcher.find()).isTrue();
                String packageName = matcher.group(1);
                String domain = packageName.substring(
                        "com.opensocket.aievent.database.persistence.".length(),
                        packageName.length() - ".dao".length());
                Path xml = xmlRoot.resolve(domain).resolve(
                        dao.getFileName().toString().replace(".java", ".xml"));
                assertThat(xml).as("Missing XML for %s", dao.getFileName()).exists();
                assertThat(read(xml)).contains("namespace=\"" + packageName + "."
                        + dao.getFileName().toString().replace(".java", "") + "\"");
            }
        }
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null
                && !Files.exists(current.resolve("architecture/module-candidates.csv"))) {
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
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
