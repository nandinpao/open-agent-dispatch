package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * M8 preserves the reviewed logical package dependency baseline while allowing
 * source files to live in separate Maven modules. Existing package-relative
 * dependency records remain stable across physical module moves.
 */
class ModularizationArchitectureBaselineTest {
    private static final String CORE_BASE_PACKAGE = "com.opensocket.aievent.core";
    private static final String DATABASE_BASE_PACKAGE = "com.opensocket.aievent.database";
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("(?m)^package\\s+([\\w.]+);");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^import\\s+([\\w.]+);");

    @Test
    void shouldNotIntroduceNewModuleCandidateDependencyEdges() throws Exception {
        Snapshot snapshot = scan();
        Set<ContextEdge> allowed = readContextBaseline(snapshot.repositoryRoot()
                .resolve("architecture/baseline/m8-context-edges.csv"));

        Set<ContextEdge> unexpected = new HashSet<>(snapshot.contextEdges());
        unexpected.removeAll(allowed);

        assertThat(unexpected)
                .as("New cross-context dependency edges require an explicit architecture review")
                .isEmpty();
    }

    @Test
    void shouldNotIntroduceNewCrossContextRepositoryOrDaoImports() throws Exception {
        Snapshot snapshot = scan();
        Set<DetailedDependency> allowed = readDetailedBaseline(snapshot.repositoryRoot()
                .resolve("architecture/baseline/m8-cross-context-repository-imports.csv"));

        Set<DetailedDependency> unexpected = new HashSet<>(snapshot.repositoryDependencies());
        unexpected.removeAll(allowed);

        assertThat(unexpected)
                .as("New direct Repository/DAO imports across module candidates are prohibited")
                .isEmpty();
    }

    @Test
    void daoTypesShouldOnlyBeImportedByMybatisAdapterPackages() throws Exception {
        Snapshot snapshot = scan();
        List<DetailedDependency> violations = snapshot.allDependencies().stream()
                .filter(dependency -> isPersistenceDaoType(dependency.importedType()))
                .filter(dependency -> !(dependency.sourceContext().equals("database-platform")
                        && dependency.sourceFile().contains("/repository/")))
                .toList();

        assertThat(violations)
                .as("Only database-platform Repository adapters may import MyBatis DAO types directly")
                .isEmpty();
    }

    @Test
    void controllerClassesShouldStayInsideApiPackageAndApiShouldRemainInboundOnly() throws Exception {
        Snapshot snapshot = scan();
        List<String> controllerViolations = new ArrayList<>();
        for (Path sourceRoot : snapshot.sourceRoots()) {
            try (var files = Files.walk(sourceRoot)) {
                files.filter(path -> path.toString().endsWith("Controller.java"))
                        .forEach(path -> {
                            String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
                            if (!relative.startsWith("api/")) {
                                controllerViolations.add(relative);
                            }
                        });
            }
        }
        List<DetailedDependency> inboundViolations = snapshot.allDependencies().stream()
                .filter(dependency -> !dependency.sourceContext().equals("api"))
                .filter(dependency -> dependency.targetContext().equals("api"))
                .toList();

        assertThat(controllerViolations).as("Controllers belong in the API package").isEmpty();
        assertThat(inboundViolations).as("Production code must not depend on inbound API controllers/DTOs").isEmpty();
    }

    private Snapshot scan() throws IOException {
        Path root = findRepositoryRoot();
        List<Path> sourceRoots = productionSourceRoots(root);
        Map<String, String> contextMap = readContextMap(root.resolve("architecture/module-candidates.csv"));
        Map<String, SourceType> classes = new HashMap<>();

        for (Path sourceRoot : sourceRoots) {
            try (var files = Files.walk(sourceRoot)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            String text = read(path);
                            Matcher matcher = PACKAGE_PATTERN.matcher(text);
                            if (matcher.find()) {
                                String fileName = path.getFileName().toString();
                                String fqcn = matcher.group(1) + "." + fileName.substring(0, fileName.length() - 5);
                                SourceType previous = classes.put(fqcn, new SourceType(sourceRoot, path));
                                if (previous != null) {
                                    throw new IllegalStateException("Duplicate production type " + fqcn);
                                }
                            }
                        });
            }
        }

        Set<ContextEdge> edges = new HashSet<>();
        Set<DetailedDependency> all = new HashSet<>();
        Set<DetailedDependency> repositories = new HashSet<>();
        for (Map.Entry<String, SourceType> source : classes.entrySet()) {
            String sourceContext = contextForType(source.getKey(), contextMap);
            String sourceFile = source.getValue().sourceRoot().relativize(source.getValue().path())
                    .toString().replace('\\', '/');
            Matcher imports = IMPORT_PATTERN.matcher(read(source.getValue().path()));
            while (imports.find()) {
                String importedType = imports.group(1);
                if (!classes.containsKey(importedType)) {
                    continue;
                }
                String targetContext = contextForType(importedType, contextMap);
                if (sourceContext == null || targetContext == null || sourceContext.equals(targetContext)) {
                    continue;
                }
                ContextEdge edge = new ContextEdge(sourceContext, targetContext);
                DetailedDependency dependency = new DetailedDependency(sourceContext, targetContext, sourceFile, importedType);
                edges.add(edge);
                all.add(dependency);
                String simpleName = importedType.substring(importedType.lastIndexOf('.') + 1);
                if (simpleName.endsWith("Repository") || isPersistenceDaoType(importedType)) {
                    repositories.add(dependency);
                }
            }
        }
        return new Snapshot(root, sourceRoots, edges, all, repositories);
    }

    private List<Path> productionSourceRoots(Path root) throws IOException {
        List<Path> roots = new ArrayList<>();
        try (var modules = Files.list(root)) {
            modules.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("pom.xml")))
                    .map(path -> path.resolve("src/main/java/com/opensocket/aievent/core"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .forEach(roots::add);
        }
        Path databaseRoot = root.resolve(
                "database-platform/src/main/java/com/opensocket/aievent/database");
        if (Files.isDirectory(databaseRoot)) {
            roots.add(databaseRoot);
        }
        return roots;
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

    private Map<String, String> readContextMap(Path path) throws IOException {
        Map<String, String> result = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.startsWith("package_segment,")) {
                continue;
            }
            String[] fields = line.split(",", -1);
            result.put(fields[0].trim(), fields[1].trim());
        }
        return result;
    }

    private Set<ContextEdge> readContextBaseline(Path path) throws IOException {
        Set<ContextEdge> result = new HashSet<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.startsWith("source_context,")) {
                continue;
            }
            String[] fields = line.split(",", -1);
            result.add(new ContextEdge(fields[0], fields[1]));
        }
        return result;
    }

    private Set<DetailedDependency> readDetailedBaseline(Path path) throws IOException {
        Set<DetailedDependency> result = new HashSet<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.startsWith("source_context,")) {
                continue;
            }
            String[] fields = line.split(",", -1);
            result.add(new DetailedDependency(fields[0], fields[1], fields[2], fields[3]));
        }
        return result;
    }

    private boolean isPersistenceDaoType(String importedType) {
        return importedType != null
                && importedType.contains(".database.persistence.")
                && importedType.contains(".dao.")
                && importedType.endsWith("Dao");
    }

    private String contextForType(String type, Map<String, String> contextMap) {
        String databasePrefix = DATABASE_BASE_PACKAGE + ".";
        if (type.equals(DATABASE_BASE_PACKAGE) || type.startsWith(databasePrefix)) {
            return "database-platform";
        }
        String corePrefix = CORE_BASE_PACKAGE + ".";
        if (!type.startsWith(corePrefix)) {
            return null;
        }
        String remainder = type.substring(corePrefix.length());
        String segment = remainder.substring(0, remainder.indexOf('.') < 0 ? remainder.length() : remainder.indexOf('.'));
        if (!segment.isEmpty() && Character.isUpperCase(segment.charAt(0))) {
            segment = "__root__";
        }
        return contextMap.getOrDefault(segment, "unmapped:" + segment);
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
    }

    private record SourceType(Path sourceRoot, Path path) {
    }

    private record ContextEdge(String sourceContext, String targetContext) {
    }

    private record DetailedDependency(String sourceContext, String targetContext, String sourceFile, String importedType) {
    }

    private record Snapshot(Path repositoryRoot,
                            List<Path> sourceRoots,
                            Set<ContextEdge> contextEdges,
                            Set<DetailedDependency> allDependencies,
                            Set<DetailedDependency> repositoryDependencies) {
    }
}
