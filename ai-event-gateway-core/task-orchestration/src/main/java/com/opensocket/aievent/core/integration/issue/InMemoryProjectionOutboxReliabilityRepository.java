package com.opensocket.aievent.core.integration.issue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * Local/development projection reliability store.
 *
 * <p>This adapter mirrors the claim, ordering, optimistic-lock and evidence semantics of the
 * MyBatis implementation closely enough for local operation. Production deliberately remains
 * fail-closed and must use the durable MYBATIS adapter.</p>
 */
@Repository
@Profile("!prod")
@ConditionalOnProperty(
        prefix = "integration-sync",
        name = "store",
        havingValue = "MEMORY",
        matchIfMissing = true)
public class InMemoryProjectionOutboxReliabilityRepository
        implements ProjectionOutboxReliabilityRepository {

    private final Map<String, ProjectionOutboxReliability> work = new ConcurrentHashMap<>();
    private final Map<String, ProjectionProviderAttemptEvidence> attempts = new ConcurrentHashMap<>();
    private final Map<String, ProjectionReadbackEvidence> readbacks = new ConcurrentHashMap<>();

    @Override
    public synchronized ProjectionOutboxReliability save(ProjectionOutboxReliability value) {
        String key = workKey(value.tenantId(), value.outboxId());
        if (work.putIfAbsent(key, value) != null) {
            throw new IllegalStateException("PROJECTION_OUTBOX_ALREADY_EXISTS");
        }
        return value;
    }

    @Override
    public synchronized ProjectionOutboxReliability saveExpectedVersion(
            ProjectionOutboxReliability value,
            long expectedVersion) {
        String key = workKey(value.tenantId(), value.outboxId());
        ProjectionOutboxReliability current = work.get(key);
        if (current == null || current.rowVersion() != expectedVersion) {
            throw new IllegalStateException("PROJECTION_OUTBOX_VERSION_CONFLICT");
        }
        if (value.rowVersion() != expectedVersion + 1) {
            throw new IllegalStateException("PROJECTION_OUTBOX_NEXT_VERSION_INVALID");
        }
        work.put(key, value);
        return value;
    }

    @Override
    public Optional<ProjectionOutboxReliability> find(String tenantId, String outboxId) {
        return Optional.ofNullable(work.get(workKey(tenantId, outboxId)));
    }

    @Override
    public synchronized List<ProjectionOutboxReliability> claimDue(
            String workerId,
            OffsetDateTime now,
            OffsetDateTime claimUntil,
            int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 200));
        List<ProjectionOutboxReliability> due = work.values().stream()
                .filter(value -> claimable(value, now))
                .filter(value -> noLowerSequencePending(value))
                .sorted(claimOrder())
                .limit(cappedLimit)
                .toList();

        List<ProjectionOutboxReliability> claimed = new ArrayList<>(due.size());
        for (ProjectionOutboxReliability current : due) {
            String key = workKey(current.tenantId(), current.outboxId());
            ProjectionOutboxReliability latest = work.get(key);
            if (latest == null
                    || latest.rowVersion() != current.rowVersion()
                    || !claimable(latest, now)
                    || !noLowerSequencePending(latest)) {
                continue;
            }
            ProjectionOutboxReliability next = copyWithClaim(
                    latest,
                    workerId,
                    claimToken(latest, workerId, now),
                    claimUntil,
                    now);
            work.put(key, next);
            claimed.add(next);
        }
        return List.copyOf(claimed);
    }

    @Override
    public synchronized boolean heartbeat(
            String tenantId,
            String outboxId,
            String workerId,
            String claimTokenHash,
            long expectedVersion,
            OffsetDateTime heartbeatAt,
            OffsetDateTime claimUntil) {
        String key = workKey(tenantId, outboxId);
        ProjectionOutboxReliability current = work.get(key);
        if (current == null
                || current.rowVersion() != expectedVersion
                || !Objects.equals(current.claimOwner(), workerId)
                || !Objects.equals(current.claimTokenHash(), claimTokenHash)
                || current.claimUntil() == null
                || !current.claimUntil().isAfter(heartbeatAt)) {
            return false;
        }
        ProjectionOutboxReliability next = new ProjectionOutboxReliability(
                current.tenantId(),
                current.outboxId(),
                current.projectionId(),
                current.operationSequence(),
                current.projectionVersion(),
                current.canonicalDocumentHash(),
                current.mappingVersion(),
                current.credentialVersion(),
                current.providerRequestFingerprint(),
                current.status(),
                current.claimOwner(),
                current.claimTokenHash(),
                claimUntil,
                heartbeatAt,
                current.rowVersion() + 1,
                current.retryAfterAt(),
                current.rateLimitResetAt(),
                current.verificationMode(),
                current.externalIdempotencyMarker(),
                current.lastAttemptId(),
                current.lastErrorCode(),
                current.lastErrorMessage(),
                current.sentAt(),
                current.acknowledgedAt(),
                current.createdAt(),
                heartbeatAt);
        work.put(key, next);
        return true;
    }

    @Override
    public List<ProjectionOutboxReliability> list(
            String tenantId,
            ProjectionOutboxReliabilityStatus status,
            int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 500));
        return work.values().stream()
                .filter(value -> Objects.equals(value.tenantId(), tenantId))
                .filter(value -> status == null || value.status() == status)
                .sorted(Comparator.comparing(
                                ProjectionOutboxReliability::updatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProjectionOutboxReliability::outboxId))
                .limit(cappedLimit)
                .toList();
    }

    @Override
    public ProjectionProviderAttemptEvidence appendAttempt(
            ProjectionProviderAttemptEvidence value) {
        String key = evidenceKey(value.tenantId(), value.attemptId());
        attempts.putIfAbsent(key, value);
        return attempts.get(key);
    }

    @Override
    public List<ProjectionProviderAttemptEvidence> listAttempts(
            String tenantId,
            String outboxId,
            int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 500));
        return attempts.values().stream()
                .filter(value -> Objects.equals(value.tenantId(), tenantId))
                .filter(value -> Objects.equals(value.outboxId(), outboxId))
                .sorted(Comparator.comparingInt(ProjectionProviderAttemptEvidence::attemptNo).reversed())
                .limit(cappedLimit)
                .toList();
    }

    @Override
    public ProjectionReadbackEvidence appendReadback(ProjectionReadbackEvidence value) {
        String key = evidenceKey(value.tenantId(), value.evidenceId());
        readbacks.putIfAbsent(key, value);
        return readbacks.get(key);
    }

    @Override
    public List<ProjectionReadbackEvidence> listReadbacks(
            String tenantId,
            String outboxId,
            int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 500));
        return readbacks.values().stream()
                .filter(value -> Objects.equals(value.tenantId(), tenantId))
                .filter(value -> Objects.equals(value.outboxId(), outboxId))
                .sorted(Comparator.comparing(
                                ProjectionReadbackEvidence::observedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProjectionReadbackEvidence::evidenceId))
                .limit(cappedLimit)
                .toList();
    }

    @Override
    public String mode() {
        return "MEMORY";
    }

    private boolean claimable(ProjectionOutboxReliability value, OffsetDateTime now) {
        if (value.status() != ProjectionOutboxReliabilityStatus.PENDING
                && value.status() != ProjectionOutboxReliabilityStatus.FAILED_RETRYABLE
                && value.status() != ProjectionOutboxReliabilityStatus.VERIFYING) {
            return false;
        }
        OffsetDateTime dueAt = value.retryAfterAt() == null
                ? value.createdAt()
                : value.retryAfterAt();
        if (dueAt != null && dueAt.isAfter(now)) {
            return false;
        }
        return value.claimUntil() == null || !value.claimUntil().isAfter(now);
    }

    private boolean noLowerSequencePending(ProjectionOutboxReliability candidate) {
        return work.values().stream().noneMatch(value ->
                Objects.equals(value.tenantId(), candidate.tenantId())
                        && Objects.equals(value.projectionId(), candidate.projectionId())
                        && value.operationSequence() < candidate.operationSequence()
                        && !value.terminal());
    }

    private Comparator<ProjectionOutboxReliability> claimOrder() {
        return Comparator.comparing(
                        this::effectiveDueAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(
                        ProjectionOutboxReliability::projectionId,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingLong(ProjectionOutboxReliability::operationSequence)
                .thenComparing(
                        ProjectionOutboxReliability::createdAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ProjectionOutboxReliability::outboxId);
    }

    private OffsetDateTime effectiveDueAt(ProjectionOutboxReliability value) {
        if (value.rateLimitResetAt() != null) {
            return value.rateLimitResetAt();
        }
        if (value.retryAfterAt() != null) {
            return value.retryAfterAt();
        }
        return value.createdAt();
    }

    private ProjectionOutboxReliability copyWithClaim(
            ProjectionOutboxReliability current,
            String workerId,
            String claimTokenHash,
            OffsetDateTime claimUntil,
            OffsetDateTime now) {
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
                ProjectionOutboxReliabilityStatus.CLAIMED,
                workerId,
                claimTokenHash,
                claimUntil,
                now,
                current.rowVersion() + 1,
                current.retryAfterAt(),
                current.rateLimitResetAt(),
                current.verificationMode(),
                current.externalIdempotencyMarker(),
                current.lastAttemptId(),
                current.lastErrorCode(),
                current.lastErrorMessage(),
                current.sentAt(),
                current.acknowledgedAt(),
                current.createdAt(),
                now);
    }

    private String claimToken(
            ProjectionOutboxReliability value,
            String workerId,
            OffsetDateTime now) {
        return sha256(value.outboxId() + ':' + workerId + ':' + now);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String workKey(String tenantId, String outboxId) {
        return tenantId + '|' + outboxId;
    }

    private String evidenceKey(String tenantId, String evidenceId) {
        return tenantId + '|' + evidenceId;
    }
}
