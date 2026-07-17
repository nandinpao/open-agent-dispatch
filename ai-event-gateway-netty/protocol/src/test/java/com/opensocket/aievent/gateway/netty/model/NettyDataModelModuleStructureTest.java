package com.opensocket.aievent.gateway.netty.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.opensocket.aievent.gateway.netty.agent.AgentSnapshot;
import com.opensocket.aievent.gateway.netty.agent.dto.AgentRegisterPayload;
import com.opensocket.aievent.gateway.netty.authorization.AgentConnectionAuthorizationRequest;
import com.opensocket.aievent.gateway.netty.protocol.AiEventEnvelope;

class NettyDataModelModuleStructureTest {

    @Test
    void nettyDataModelShouldOwnProtocolAndRuntimeModelClasses() throws IOException {
        assertThat(NettyDataModelModule.class).isNotNull();
        assertThat(AiEventEnvelope.class).isNotNull();
        assertThat(AgentRegisterPayload.class).isNotNull();
        assertThat(AgentSnapshot.class).isNotNull();
        assertThat(AgentConnectionAuthorizationRequest.class).isNotNull();

        Path root = repositoryRoot();
        Path model = root.resolve("gateway-model/src/main/java/com/opensocket/aievent/gateway/netty");
        try (Stream<Path> files = Files.walk(model)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))).hasSizeGreaterThanOrEqualTo(29);
        }
    }

    private Path repositoryRoot() {
        List<Path> candidates = new ArrayList<>();

        String mavenRoot = System.getProperty("maven.multiModuleProjectDirectory");
        if (mavenRoot != null && !mavenRoot.isBlank()) {
            addCandidate(candidates, Path.of(mavenRoot));
        }

        addCandidate(candidates, Path.of(System.getProperty("user.dir", ".")));
        addCandidate(candidates, Path.of(""));

        for (Path candidate : candidates) {
            Path current = candidate;
            while (current != null) {
                if (isNettyProjectRoot(current)) {
                    return current;
                }
                current = current.getParent();
            }
        }

        throw new IllegalStateException("Could not locate ai-event-gateway-netty project root");
    }

    private void addCandidate(List<Path> candidates, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        candidates.add(normalized);
        candidates.add(normalized.resolve("ai-event-gateway-netty"));
    }

    private boolean isNettyProjectRoot(Path path) {
        return Files.isRegularFile(path.resolve("pom.xml"))
                && Files.isRegularFile(path.resolve("protocol/pom.xml"))
                && Files.isRegularFile(path.resolve("gateway-model/pom.xml"))
                && Files.isDirectory(path.resolve("gateway-model/src/main/java/com/opensocket/aievent/gateway/netty"));
    }
}
