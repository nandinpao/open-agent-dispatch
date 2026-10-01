package com.opensocket.aievent.gateway.netty.configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

/**
 * Node-local durable store for the last authenticated snapshot of each configuration set.
 *
 * <p>The persisted file never becomes an independent authority: it is accepted only after the
 * original Core signature/hash/authority contract is revalidated during recovery.</p>
 */
public final class GatewayRuntimeConfigurationLkgStore {
    private final Path directory;
    private final ObjectMapper objectMapper;

    public GatewayRuntimeConfigurationLkgStore(String directory, ObjectMapper objectMapper) {
        if (directory == null || directory.isBlank()) throw new IllegalArgumentException("Gateway LKG directory is required");
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public void save(GatewayRuntimeConfigurationSnapshot snapshot) {
        if (snapshot == null || snapshot.configSetId() == null || snapshot.configSetId().isBlank()) {
            throw new IllegalArgumentException("snapshot/configSetId is required");
        }
        try {
            Files.createDirectories(directory);
            Path target = file(snapshot.configSetId());
            Path tmp = Files.createTempFile(directory, ".runtime-config-lkg-", ".tmp");
            Files.write(tmp, objectMapper.writeValueAsBytes(snapshot), StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("RUNTIME_CONFIGURATION_LKG_PERSIST_FAILED configSetId=" + snapshot.configSetId(), ex);
        }
    }

    public List<LoadResult> loadAll() {
        if (!Files.isDirectory(directory)) return List.of();
        List<LoadResult> results = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : stream) {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    GatewayRuntimeConfigurationSnapshot snapshot = objectMapper.readValue(bytes, GatewayRuntimeConfigurationSnapshot.class);
                    results.add(new LoadResult(path.toString(), snapshot, null));
                } catch (Exception ex) {
                    results.add(new LoadResult(path.toString(), null, ex.getClass().getSimpleName() + ": " + ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("RUNTIME_CONFIGURATION_LKG_SCAN_FAILED directory=" + directory, ex);
        }
        results.sort(Comparator.comparing(LoadResult::source));
        return List.copyOf(results);
    }

    public Path directory() { return directory; }

    private Path file(String configSetId) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(configSetId.getBytes(StandardCharsets.UTF_8));
        return directory.resolve(encoded + ".json");
    }

    public record LoadResult(String source, GatewayRuntimeConfigurationSnapshot snapshot, String error) {
        public boolean readable() { return snapshot != null && error == null; }
    }
}
