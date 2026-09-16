package com.opensocket.aievent.core.integration.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InMemoryProjectionOutboxReliabilityRepositoryTest {

    private final OffsetDateTime now = OffsetDateTime.of(
            2026, 7, 28, 0, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void memoryCompositionProvidesRepositoryForLocalStore() {
        new ApplicationContextRunner()
                .withUserConfiguration(InMemoryProjectionOutboxReliabilityRepository.class)
                .withPropertyValues("integration-sync.store=MEMORY")
                .run(context -> assertThat(context)
                        .hasSingleBean(ProjectionOutboxReliabilityRepository.class));
    }

    @Test
    void mybatisCompositionDoesNotRegisterMemoryRepository() {
        new ApplicationContextRunner()
                .withUserConfiguration(InMemoryProjectionOutboxReliabilityRepository.class)
                .withPropertyValues("integration-sync.store=MYBATIS")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ProjectionOutboxReliabilityRepository.class));
    }

    @Test
    void lowerSequenceBlocksLaterWorkUntilTerminal() {
        var repository = new InMemoryProjectionOutboxReliabilityRepository();
        repository.save(work("outbox-1", 1, ProjectionOutboxReliabilityStatus.PENDING, 1));
        repository.save(work("outbox-2", 2, ProjectionOutboxReliabilityStatus.PENDING, 1));

        var firstClaim = repository.claimDue("worker-a", now, now.plusMinutes(1), 10);
        assertThat(firstClaim.stream()
                .map(ProjectionOutboxReliability::outboxId)
                .toList()).containsExactly("outbox-1");

        var claimed = firstClaim.getFirst();
        repository.saveExpectedVersion(copyStatus(
                claimed,
                ProjectionOutboxReliabilityStatus.ACKNOWLEDGED,
                claimed.rowVersion() + 1), claimed.rowVersion());

        var secondClaim = repository.claimDue("worker-b", now.plusSeconds(1), now.plusMinutes(1), 10);
        assertThat(secondClaim.stream()
                .map(ProjectionOutboxReliability::outboxId)
                .toList()).containsExactly("outbox-2");
    }

    @Test
    void optimisticVersionAndClaimTokenAreEnforced() {
        var repository = new InMemoryProjectionOutboxReliabilityRepository();
        repository.save(work("outbox-1", 1, ProjectionOutboxReliabilityStatus.PENDING, 1));

        var claimed = repository.claimDue("worker-a", now, now.plusMinutes(1), 1).getFirst();
        assertThat(claimed.status()).isEqualTo(ProjectionOutboxReliabilityStatus.CLAIMED);
        assertThat(claimed.claimTokenHash()).hasSize(64);

        assertThat(repository.heartbeat(
                claimed.tenantId(),
                claimed.outboxId(),
                "worker-a",
                claimed.claimTokenHash(),
                claimed.rowVersion(),
                now.plusSeconds(10),
                now.plusMinutes(2))).isTrue();

        assertThat(repository.heartbeat(
                claimed.tenantId(),
                claimed.outboxId(),
                "worker-b",
                claimed.claimTokenHash(),
                claimed.rowVersion() + 1,
                now.plusSeconds(20),
                now.plusMinutes(3))).isFalse();

        assertThatThrownBy(() -> repository.saveExpectedVersion(
                work("outbox-1", 1, ProjectionOutboxReliabilityStatus.ACKNOWLEDGED, 99),
                98))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PROJECTION_OUTBOX_VERSION_CONFLICT");
    }

    private ProjectionOutboxReliability work(
            String outboxId,
            long operationSequence,
            ProjectionOutboxReliabilityStatus status,
            long rowVersion) {
        return new ProjectionOutboxReliability(
                "tenant-a",
                outboxId,
                "projection-a",
                operationSequence,
                1,
                "canonical-hash",
                1,
                "credential-v1",
                "fingerprint",
                status,
                null,
                null,
                null,
                null,
                rowVersion,
                null,
                null,
                "READBACK_ON_UNCERTAIN",
                "marker-" + outboxId,
                null,
                null,
                null,
                null,
                status == ProjectionOutboxReliabilityStatus.ACKNOWLEDGED ? now : null,
                now,
                now);
    }

    private ProjectionOutboxReliability copyStatus(
            ProjectionOutboxReliability current,
            ProjectionOutboxReliabilityStatus status,
            long rowVersion) {
        return new ProjectionOutboxReliability(
                current.tenantId(),
                current.outboxId(),
                current.projectionId(),
                current.operationSequence(),
                current.projectionVersion(),
                current.canonicalDocumentHash(),
                current.mappingVersion(),
                current.credentialVersion(),
                current.providerRequestFingerprint(),
                status,
                null,
                null,
                null,
                null,
                rowVersion,
                current.retryAfterAt(),
                current.rateLimitResetAt(),
                current.verificationMode(),
                current.externalIdempotencyMarker(),
                current.lastAttemptId(),
                current.lastErrorCode(),
                current.lastErrorMessage(),
                current.sentAt(),
                status == ProjectionOutboxReliabilityStatus.ACKNOWLEDGED ? now : current.acknowledgedAt(),
                current.createdAt(),
                now);
    }
}
