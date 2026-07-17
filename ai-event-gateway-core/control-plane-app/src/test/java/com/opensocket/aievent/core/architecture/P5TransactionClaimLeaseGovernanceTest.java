package com.opensocket.aievent.core.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class P5TransactionClaimLeaseGovernanceTest {

    @Test
    void commonClaimLeaseAndWriteResultContractsShouldExistInKernel() {
        Path root = repositoryRoot();
        Path packageRoot = root.resolve(
                "kernel/src/main/java/com/opensocket/aievent/core/kernel/persistence");
        for (String type : List.of(
                "ClaimRequest.java",
                "ClaimOwnership.java",
                "LeaseRenewalRequest.java",
                "PersistenceWriteOutcome.java",
                "PersistenceWriteResult.java",
                "PersistenceWriteVerifier.java")) {
            assertThat(packageRoot.resolve(type)).exists();
        }
    }

    @Test
    void dispatchShouldClaimBeforeCallingTheExternalGateway() throws IOException {
        Path root = repositoryRoot();
        String service = read(root.resolve(
                "execution-control/src/main/java/"
                        + "com/opensocket/aievent/core/dispatch/DispatchExecutionService.java"));
        assertThat(service)
                .contains("claimById(dispatchRequestId, claimRequest)")
                .contains("claimExecutable(claimRequest)")
                .contains("claimRequest(OffsetDateTime.now(ZoneOffset.UTC), 1)")
                .contains("executeClaimed(request, ownership(request), startedAt)")
                .contains("effectiveClaimLease()")
                .contains("nettyDispatchPort.dispatch(request)")
                .contains("dispatchRepository.saveClaimed(request, ownership)")
                .doesNotContain("@Transactional");
        assertThat(service.indexOf("claimById(dispatchRequestId, claimRequest)"))
                .isLessThan(service.indexOf("nettyDispatchPort.dispatch(request)"));

        String status = read(root.resolve(
                "data-model/src/main/java/"
                        + "com/opensocket/aievent/core/dispatch/DispatchRequestStatus.java"));
        assertThat(status).contains("DISPATCHING");

        String request = read(root.resolve(
                "data-model/src/main/java/"
                        + "com/opensocket/aievent/core/dispatch/DispatchRequest.java"));
        assertThat(request)
                .contains("claimedBy")
                .contains("claimStartedAt")
                .contains("claimUntil");
    }

    @Test
    void synchronousWorkersShouldClaimOneRowImmediatelyBeforeEachExternalOperation() throws IOException {
        Path root = repositoryRoot();
        String outbox = read(root.resolve(
                "domain-events/src/main/java/"
                        + "com/opensocket/aievent/core/outbox/OutboxEventDispatcher.java"));
        String integration = read(root.resolve(
                "integration-events/src/main/java/"
                        + "com/opensocket/aievent/core/integration/IntegrationEventDeliveryService.java"));

        assertThat(outbox)
                .contains("properties.getClaimLease(),")
                .contains("1);")
                .contains("batch.getFirst()");
        assertThat(integration)
                .contains("properties.getClaimLease(),")
                .contains("1);")
                .contains("batch.getFirst()");
    }

    @Test
    void persistentClaimsShouldUseSkipLockedAndOwnershipFencing() throws IOException {
        Path root = repositoryRoot();
        for (String relative : List.of(
                "domainevent/OutboxEventDao.xml",
                "integrationevent/IntegrationEventDao.xml",
                "execution/DispatchRequestDao.xml")) {
            String xml = read(root.resolve(
                    "database-platform/src/main/resources/"
                            + "mybatis/postgresql/"
                            + relative));
            assertThat(xml)
                    .containsIgnoringCase("for update skip locked")
                    .contains("claimed_by")
                    .contains("claim_until");
        }

        String adapter = read(root.resolve(
                "database-platform/src/main/resources/"
                        + "mybatis/postgresql/adapter/AdapterActionDao.xml"));
        assertThat(adapter)
                .contains("claimed_by")
                .contains("lease_expires_at")
                .contains("saveClaimed")
                .contains("recoverExpiredClaim");
    }

    @Test
    void claimAwareRepositoriesShouldReturnExplicitWriteResults() throws IOException {
        Path root = repositoryRoot();
        for (String relative : List.of(
                "data-model/src/main/java/"
                        + "com/opensocket/aievent/core/outbox/OutboxEventRepository.java",
                "data-model/src/main/java/"
                        + "com/opensocket/aievent/core/integration/IntegrationEventRepository.java",
                "data-model/src/main/java/"
                        + "com/opensocket/aievent/core/dispatch/DispatchRequestRepository.java",
                "data-model/src/main/java/"
                        + "com/opensocket/aievent/core/action/AdapterActionRepository.java")) {
            String source = read(root.resolve(relative));
            assertThat(source)
                    .contains("Claim")
                    .contains("PersistenceWriteResult");
        }
    }

    @Test
    void migrationAndConcurrencyCoverageShouldExist() {
        Path root = repositoryRoot();
        assertThat(root.resolve(
                "database-platform/src/main/resources/db/migration/"
                        + "V20__dispatch_claim_lease.sql")).exists();
        assertThat(root.resolve(
                "control-plane-app/src/test/java/com/opensocket/aievent/core/container/"
                        + "PostgresClaimLeaseConcurrencyContainerTest.java")).exists();
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

    private String read(Path path) throws IOException {
        return Files.readString(path);
    }
}
