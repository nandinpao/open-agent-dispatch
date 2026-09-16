package com.opensocket.aievent.core.enforcement.activation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshState;
import com.opensocket.aievent.core.enforcement.activation.contract.SnapshotRefreshStatus;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotData;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotFactory;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotLoader;
import com.opensocket.aievent.core.enforcement.activation.core.AuthoritySnapshotRepository;
import com.opensocket.aievent.core.enforcement.activation.core.RevisionedAuthorityRouter;

class SnapshotBootstrapServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void restoresPersistedLastKnownGoodInsteadOfLatestRevision() {
        AuthoritySnapshotData one = snapshot(1);
        AuthoritySnapshotData two = snapshot(2);
        RevisionedAuthorityRouter router = new RevisionedAuthorityRouter();
        RecordingStatusRepository statuses = new RecordingStatusRepository(status(1), false);
        SnapshotBootstrapService service = new SnapshotBootstrapService(
                new AuthoritySnapshotLoader(new FixedRepository(two, one), router),
                router,
                statuses,
                CLOCK);

        SnapshotRefreshStatus result = service.bootstrap("bootstrap", "correlation-bootstrap");

        assertEquals(SnapshotRefreshState.APPLIED, result.state());
        assertEquals(1L, result.activeRevision());
        assertEquals(1L, router.currentRevision());
    }

    @Test
    void failsClosedToLegacyAndRetainsPersistedLastKnownGoodReference() {
        RevisionedAuthorityRouter router = new RevisionedAuthorityRouter();
        RecordingStatusRepository statuses = new RecordingStatusRepository(status(7), false);
        SnapshotBootstrapService service = new SnapshotBootstrapService(
                new AuthoritySnapshotLoader(new EmptyRepository(), router),
                router,
                statuses,
                CLOCK);

        SnapshotRefreshStatus result = service.bootstrap("bootstrap", "correlation-bootstrap-failed");

        assertEquals(SnapshotRefreshState.FAILED, result.state());
        assertEquals(0L, result.activeRevision());
        assertEquals(7L, result.attemptedRevision());
        assertEquals(7L, result.lastKnownGoodRevision());
        assertEquals(0L, router.currentRevision());
    }

    private static SnapshotRefreshStatus status(long lastKnownGood) {
        return new SnapshotRefreshStatus(
                SnapshotRefreshState.APPLIED,
                lastKnownGood,
                lastKnownGood,
                lastKnownGood,
                "sha256:" + "a".repeat(64),
                "",
                "",
                NOW);
    }

    private static AuthoritySnapshotData snapshot(long revision) {
        String checksum = new AuthoritySnapshotFactory().checksum(revision, List.of());
        return new AuthoritySnapshotData(revision, NOW, checksum, List.of());
    }

    private record FixedRepository(
            AuthoritySnapshotData latest,
            AuthoritySnapshotData exact) implements AuthoritySnapshotRepository {
        @Override
        public Optional<AuthoritySnapshotData> findLatestPublished() {
            return Optional.of(latest);
        }

        @Override
        public Optional<AuthoritySnapshotData> findPublished(long revision) {
            return exact.revision() == revision ? Optional.of(exact) : Optional.empty();
        }
    }

    private static final class EmptyRepository implements AuthoritySnapshotRepository {
        @Override
        public Optional<AuthoritySnapshotData> findLatestPublished() {
            return Optional.empty();
        }

        @Override
        public Optional<AuthoritySnapshotData> findPublished(long revision) {
            return Optional.empty();
        }
    }

    private static final class RecordingStatusRepository implements SnapshotRefreshStatusRepository {
        private SnapshotRefreshStatus current;
        private final boolean fail;

        private RecordingStatusRepository(SnapshotRefreshStatus current, boolean fail) {
            this.current = current;
            this.fail = fail;
        }

        @Override
        public Optional<SnapshotRefreshStatus> current() {
            return Optional.ofNullable(current);
        }

        @Override
        public void save(SnapshotRefreshStatus status, String actorId, String correlationId) {
            if (fail) {
                throw new IllegalStateException("status store unavailable");
            }
            current = status;
        }
    }
}
