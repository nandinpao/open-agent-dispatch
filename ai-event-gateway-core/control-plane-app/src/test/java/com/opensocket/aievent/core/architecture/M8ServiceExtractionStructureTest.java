package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.opensocket.aievent.core.integration.IntegrationEventOperationalQuery;
import com.opensocket.aievent.service.adapter.AdapterWorkItem;
import com.opensocket.aievent.service.events.IntegrationEventEnvelope;
import org.junit.jupiter.api.Test;

class M8ServiceExtractionStructureTest {

    @Test
    void serviceContractsAndIntegrationBoundaryAreOnCoreClasspath() {
        assertThat(AdapterWorkItem.class).isRecord();
        assertThat(IntegrationEventEnvelope.class).isRecord();
        assertThat(IntegrationEventOperationalQuery.class).isInterface();
    }

    @Test
    void externalWorkerMustNotDependOnCoreDomainModules() throws IOException {
        String pom = Files.readString(repositoryRoot().resolve("adapter-worker-app/pom.xml"));
        assertThat(pom).contains("service-contracts");
        assertThat(pom).doesNotContain("adapter-action");
        assertThat(pom).doesNotContain("ai-event-gateway-core-db-support");
        assertThat(pom).doesNotContain("domain-events");
    }

    @Test
    void integrationEventOutboxHasExplicitOwnership() throws IOException {
        String ownership = Files.readString(repositoryRoot().resolve("architecture/table-ownership.csv"));
        assertThat(ownership).contains("integration_event_outbox,integration-events");
        assertThat(repositoryRoot().resolve(
                "database-platform/src/main/resources/db/migration/V19__integration_event_outbox.sql"))
                .exists();
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("architecture/module-candidates.csv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
