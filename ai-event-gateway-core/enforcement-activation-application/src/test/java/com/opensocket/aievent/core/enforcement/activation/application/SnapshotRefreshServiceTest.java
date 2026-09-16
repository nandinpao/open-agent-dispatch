package com.opensocket.aievent.core.enforcement.activation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class SnapshotRefreshServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void activatesTheExactPublishedRevision() {
        AuthoritySnapshotData revisionOne = snapshot(1);
        AuthoritySnapshotData revisionTwo = snapshot(2);
        RevisionedAuthorityRouter router = new RevisionedAuthorityRouter();
        RecordingStatusRepository statuses = new RecordingStatusRepository(false);
        SnapshotRefreshService service = new SnapshotRefreshService(
                new AuthoritySnapshotLoader(new FixedRepository(revisionTwo, revisionOne), router),
                router,
                statuses,
                CLOCK);

        SnapshotRefreshStatus result = service.refresh(1, "publisher", "correlation-1");

        assertEquals(SnapshotRefreshState.APPLIED, result.state());
        assertEquals(1L, router.currentRevision());
        assertEquals(1L, result.lastKnownGoodRevision());
    }

    @Test
    void restoresThePreviousSnapshotWhenRefreshEvidenceCannotBePersisted() {
        AuthoritySnapshotData revisionOne = snapshot(1);
        AuthoritySnapshotData revisionTwo = snapshot(2);
        RevisionedAuthorityRouter router = new RevisionedAuthorityRouter();
        router.publish(revisionOne);
        SnapshotRefreshService service = new SnapshotRefreshService(
                new AuthoritySnapshotLoader(new FixedRepository(revisionTwo, revisionTwo), router),
                router,
                new RecordingStatusRepository(true),
                CLOCK);

        assertThrows(IllegalStateException.class, () -> service.refresh(2, "publisher", "correlation-2"));
        assertEquals(1L, router.currentRevision());
        assertEquals(new AuthoritySnapshotFactory().checksum(1, List.of()), router.currentChecksum());
    }

    private static AuthoritySnapshotData snapshot(long revision) {
        String checksum = new AuthoritySnapshotFactory().checksum(revision, List.of());
        return new AuthoritySnapshotData(revision, NOW, checksum, List.of());
    }

    private record FixedRepository(
            AuthoritySnapshotData latest,
            AuthoritySnapshotData exact) implements AuthoritySnapshotRepository {
        @Override public Optional<AuthoritySnapshotData> findLatestPublished() { return Optional.of(latest); }
        @Override public Optional<AuthoritySnapshotData> findPublished(long revision) {
            return exact.revision() == revision ? Optional.of(exact) : Optional.empty();
        }
    }

    private static final class RecordingStatusRepository implements SnapshotRefreshStatusRepository {
        private final boolean fail;
        private SnapshotRefreshStatus current;
        private RecordingStatusRepository(boolean fail) { this.fail = fail; }
        @Override public Optional<SnapshotRefreshStatus> current() { return Optional.ofNullable(current); }
        @Override public void save(SnapshotRefreshStatus status, String actorId, String correlationId) {
            if (fail) throw new IllegalStateException("status store unavailable");
            current = status;
        }
    }
}
