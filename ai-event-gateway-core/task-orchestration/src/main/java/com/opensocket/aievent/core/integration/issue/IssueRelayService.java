package com.opensocket.aievent.core.integration.issue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.integration.handoff.HandoffContextReasonCode;
import com.opensocket.aievent.core.integration.handoff.HandoffContextSnapshot;
import com.opensocket.aievent.core.integration.handoff.HandoffSnapshotReadPort;
import com.opensocket.aievent.core.integration.handoff.HandoffSnapshotStatus;
import com.opensocket.aievent.core.integration.identity.CrossProjectRelayReadiness;
import com.opensocket.aievent.core.integration.identity.IntegrationConnection;
import com.opensocket.aievent.core.integration.identity.IntegrationIdentityService;
import com.opensocket.aievent.core.integration.identity.IntegrationProjectMapping;
import com.opensocket.aievent.core.integration.issue.projection.CrossProjectIssueRelay;
import com.opensocket.aievent.core.integration.issue.projection.IssueProjectionReasonCode;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayAttempt;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayOperationStatus;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayProviderGateway;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayProviderResult;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayRepository;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayState;
import com.opensocket.aievent.core.integration.issue.projection.IssueRelayStrategy;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;

/**
 * Provider-side projection workflow for an approved Handoff snapshot.
 *
 * <p>The service owns only Issue projection records. Handoff is accessed through a
 * read-only port, and Task records are read only to validate the source/target chain.
 * It cannot create, transition or complete Task, A2A, Assignment or Result authority.</p>
 */
@Service
public class IssueRelayService {
    private final IssueRelayRepository repository;
    private final HandoffSnapshotReadPort handoffSnapshots;
    private final TaskRepository tasks;
    private final IntegrationIdentityService identities;
    private final IssueRelayProviderGateway relayGateway;
    private IssueRelayProjectionMaterializer relayProjectionMaterializer =
            IssueRelayProjectionMaterializer.noop();

    public IssueRelayService(
            IssueRelayRepository repository,
            HandoffSnapshotReadPort handoffSnapshots,
            TaskRepository tasks,
            IntegrationIdentityService identities,
            IssueRelayProviderGateway relayGateway) {
        this.repository = repository;
        this.handoffSnapshots = handoffSnapshots;
        this.tasks = tasks;
        this.identities = identities;
        this.relayGateway = relayGateway;
    }

    @Autowired(required = false)
    public void setRelayProjectionMaterializer(IssueRelayProjectionMaterializer value) {
        this.relayProjectionMaterializer = value == null
                ? IssueRelayProjectionMaterializer.noop()
                : value;
    }

    @Transactional
    public CrossProjectIssueRelay createRelay(
            String tenant,
            String relayId,
            String a2aRequestId,
            String sourceTaskId,
            String targetTaskId,
            String sourceMappingId,
            String targetMappingId,
            String snapshotId,
            IssueRelayStrategy strategy,
            String sourceIssueLinkId,
            String idempotencyKey,
            String correlationId) {
        throw new IllegalStateException("LEGACY_ISSUE_RELAY_RETIRED_USE_A2A");
    }

    public CrossProjectIssueRelay relay(String tenant, String relayId) {
        return repository.findRelay(required(tenant, "tenantId"), required(relayId, "relayId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Issue Relay not found in Tenant: " + relayId));
    }

    public List<CrossProjectIssueRelay> relays(String tenant, String taskId, int limit) {
        return repository.listRelays(required(tenant, "tenantId"), taskId, bounded(limit));
    }

    public List<IssueRelayAttempt> relayAttempts(String tenant, String relayId, int limit) {
        String tenantId = required(tenant, "tenantId");
        relay(tenantId, relayId);
        return repository.listRelayAttempts(tenantId, relayId, bounded(limit));
    }

    @Transactional
    public CrossProjectIssueRelay executeRelay(String tenant, String relayId) {
        String tenantId = required(tenant, "tenantId");
        CrossProjectIssueRelay relay = relay(tenantId, relayId);
        HandoffContextSnapshot snapshot = snapshot(tenantId, relay.sourceSnapshotId());

        if (relay.relayState() == IssueRelayState.LINKED) {
            return relay;
        }
        if (relay.strategy() == IssueRelayStrategy.OPEN_DISPATCH_ONLY) {
            return repository.saveRelay(move(relay, IssueRelayState.LINKED, false,
                    "NOT_REQUIRED", "NOT_REQUIRED", null, null));
        }

        CrossProjectIssueRelay working = relay;
        String targetIssueId = working.targetIssueLinkId();
        String targetIssueUrl = null;
        if (targetIssueId == null || targetIssueId.isBlank()) {
            working = repository.saveRelay(move(working, IssueRelayState.TARGET_ISSUE_PENDING,
                    null, working.sourceBacklinkStatus(), working.targetBacklinkStatus(),
                    null, null));
            IssueRelayProviderResult created = relayGateway.createTargetIssue(working, snapshot);
            recordAttempt(tenantId, working, "CREATE_TARGET_ISSUE", "TARGET", created);
            if (!created.success()) {
                return failRelay(working, created);
            }
            targetIssueId = created.externalIssueId();
            targetIssueUrl = created.externalIssueUrl();
            working = copyLinks(working, working.sourceIssueLinkId(), targetIssueId,
                    IssueRelayState.TARGET_ISSUE_CREATED);
            working = repository.saveRelay(working);
            materialize(working, targetIssueId, targetIssueUrl);
        }

        if (working.strategy() == IssueRelayStrategy.NATIVE_RELATION
                || working.strategy() == IssueRelayStrategy.SHARED_SCOPED_PRINCIPAL) {
            IssueRelayProviderResult relation = relayGateway.createNativeRelation(
                    working, working.sourceIssueLinkId(), targetIssueId);
            recordAttempt(tenantId, working, "CREATE_NATIVE_RELATION", "INTERNAL", relation);
            if (relation.success()) {
                return repository.saveRelay(move(working, IssueRelayState.LINKED, true,
                        "NOT_REQUIRED", "NOT_REQUIRED", null, null));
            }
            if (relation.retryable()) {
                return failRelay(working, relation);
            }
            working = repository.saveRelay(move(working, IssueRelayState.BACKLINK_PENDING,
                    false, "PENDING", "PENDING",
                    IssueProjectionReasonCode.ISSUE_RELAY_NATIVE_RELATION_UNAVAILABLE.name(),
                    relation.responseSummary()));
        } else if (working.relayState() != IssueRelayState.BACKLINK_PENDING) {
            working = repository.saveRelay(move(working, IssueRelayState.BACKLINK_PENDING,
                    working.nativeRelationSupported(),
                    "SYNCED".equals(working.sourceBacklinkStatus()) ? "SYNCED" : "PENDING",
                    "SYNCED".equals(working.targetBacklinkStatus()) ? "SYNCED" : "PENDING",
                    working.lastErrorCode(), working.lastErrorMessage()));
        }

        IssueRelayProviderResult sourceBack = "SYNCED".equals(working.sourceBacklinkStatus())
                ? IssueRelayProviderResult.success(null, null,
                        "Source backlink already synchronized.")
                : relayGateway.appendSourceBacklink(working, working.sourceIssueLinkId(),
                        targetIssueId, targetIssueUrl);
        if (!"SYNCED".equals(working.sourceBacklinkStatus())) {
            recordAttempt(tenantId, working, "APPEND_SOURCE_BACKLINK", "SOURCE", sourceBack);
        }

        IssueRelayProviderResult targetBack = "SYNCED".equals(working.targetBacklinkStatus())
                ? IssueRelayProviderResult.success(null, null,
                        "Target backlink already synchronized.")
                : relayGateway.appendTargetBacklink(working, working.sourceIssueLinkId(),
                        null, targetIssueId);
        if (!"SYNCED".equals(working.targetBacklinkStatus())) {
            recordAttempt(tenantId, working, "APPEND_TARGET_BACKLINK", "TARGET", targetBack);
        }

        String sourceStatus = operationStatus(sourceBack);
        String targetStatus = operationStatus(targetBack);
        IssueRelayState state = sourceBack.success() && targetBack.success()
                ? IssueRelayState.LINKED
                : sourceBack.success() || targetBack.success()
                        ? IssueRelayState.PARTIALLY_LINKED
                        : sourceBack.retryable() || targetBack.retryable()
                                ? IssueRelayState.FAILED_RETRYABLE
                                : IssueRelayState.FAILED_PERMANENT;
        return repository.saveRelay(move(working, state, false, sourceStatus, targetStatus,
                state == IssueRelayState.PARTIALLY_LINKED
                        ? IssueProjectionReasonCode.ISSUE_RELAY_PARTIAL_SUCCESS.name()
                        : null,
                null));
    }

    private void materialize(CrossProjectIssueRelay relay, String issueId, String issueUrl) {
        try {
            IntegrationProjectMapping mapping = identities.mapping(
                    relay.tenantId(), relay.targetMappingId());
            IntegrationConnection connection = identities.connection(
                    relay.tenantId(), mapping.connectionId());
            relayProjectionMaterializer.materializeRelay(
                    relay, mapping, connection, issueId, issueUrl);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    IssueProjectionReasonCode.ISSUE_RELAY_CANONICAL_MATERIALIZATION_FAILED.name()
                            + ": " + exception.getMessage(), exception);
        }
    }

    private void recordAttempt(String tenantId, CrossProjectIssueRelay relay,
            String operation, String side, IssueRelayProviderResult result) {
        int attemptNo = repository.listRelayAttempts(tenantId, relay.relayId(), 1000).stream()
                .filter(value -> operation.equals(value.operationCode())
                        && side.equals(value.operationSide()))
                .mapToInt(IssueRelayAttempt::attemptNo)
                .max()
                .orElse(0) + 1;
        IssueRelayOperationStatus status = result.success()
                ? IssueRelayOperationStatus.SUCCEEDED
                : result.retryable()
                        ? IssueRelayOperationStatus.FAILED_RETRYABLE
                        : IssueRelayOperationStatus.FAILED_PERMANENT;
        repository.saveRelayAttempt(new IssueRelayAttempt(
                tenantId,
                "relay-attempt-" + UUID.randomUUID(),
                relay.relayId(),
                operation,
                side,
                null,
                "SOURCE".equals(side) ? relay.sourceMappingId()
                        : "TARGET".equals(side) ? relay.targetMappingId() : null,
                null,
                attemptNo,
                status,
                result.providerStatus(),
                result.externalIssueId(),
                result.externalIssueUrl(),
                result.responseSummary(),
                result.errorCode(),
                result.retryable(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                relay.correlationId()));
    }

    private CrossProjectIssueRelay failRelay(
            CrossProjectIssueRelay relay, IssueRelayProviderResult result) {
        return repository.saveRelay(move(relay,
                result.retryable() ? IssueRelayState.FAILED_RETRYABLE
                        : IssueRelayState.FAILED_PERMANENT,
                result.nativeRelationSupported(),
                relay.sourceBacklinkStatus(),
                relay.targetBacklinkStatus(),
                result.errorCode(),
                result.responseSummary()));
    }

    private CrossProjectIssueRelay copyLinks(CrossProjectIssueRelay relay,
            String sourceIssueLinkId, String targetIssueLinkId, IssueRelayState state) {
        return new CrossProjectIssueRelay(
                relay.tenantId(), relay.relayId(), relay.a2aRequestId(), relay.rootTaskId(),
                relay.sourceTaskId(), relay.targetTaskId(), relay.sourceMappingId(),
                relay.targetMappingId(), relay.sourceSnapshotId(), relay.resultSnapshotId(),
                sourceIssueLinkId, targetIssueLinkId, relay.strategy(), state,
                relay.nativeRelationSupported(), relay.sourceBacklinkStatus(),
                relay.targetBacklinkStatus(), relay.retryCount(), relay.nextRetryAt(),
                relay.lastErrorCode(), relay.lastErrorMessage(), relay.idempotencyKey(),
                relay.correlationId(), relay.version() + 1, relay.createdAt(),
                OffsetDateTime.now());
    }

    private CrossProjectIssueRelay move(CrossProjectIssueRelay relay,
            IssueRelayState state, Boolean nativeSupport, String sourceStatus,
            String targetStatus, String errorCode, String errorMessage) {
        boolean retryable = state == IssueRelayState.FAILED_RETRYABLE;
        return new CrossProjectIssueRelay(
                relay.tenantId(), relay.relayId(), relay.a2aRequestId(), relay.rootTaskId(),
                relay.sourceTaskId(), relay.targetTaskId(), relay.sourceMappingId(),
                relay.targetMappingId(), relay.sourceSnapshotId(), relay.resultSnapshotId(),
                relay.sourceIssueLinkId(), relay.targetIssueLinkId(), relay.strategy(), state,
                nativeSupport, sourceStatus, targetStatus,
                retryable ? relay.retryCount() + 1 : relay.retryCount(),
                retryable ? OffsetDateTime.now().plusMinutes(5) : null,
                errorCode, errorMessage, relay.idempotencyKey(), relay.correlationId(),
                relay.version() + 1, relay.createdAt(), OffsetDateTime.now());
    }

    private String operationStatus(IssueRelayProviderResult result) {
        return result.success() ? "SYNCED"
                : result.retryable() ? "FAILED_RETRYABLE" : "FAILED_PERMANENT";
    }

    private HandoffContextSnapshot snapshot(String tenantId, String snapshotId) {
        return handoffSnapshots.findSnapshot(required(tenantId, "tenantId"),
                        required(snapshotId, "snapshotId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Handoff Context Snapshot not found in Tenant: " + snapshotId));
    }

    private TaskRecord task(String tenantId, String taskId) {
        return tasks.findByTenantAndId(tenantId, required(taskId, "taskId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task not found in Tenant: " + taskId));
    }

    private void requireSameRoot(TaskRecord source, TaskRecord target) {
        String sourceRoot = source.getRootTaskId() == null
                ? source.getTaskId() : source.getRootTaskId();
        String targetRoot = target.getRootTaskId() == null
                ? target.getTaskId() : target.getRootTaskId();
        if (!Objects.equals(sourceRoot, targetRoot)) {
            throw new IllegalArgumentException("Tasks must belong to the same Root Task chain.");
        }
    }

    private String canonical(Object... values) {
        return Arrays.stream(values).map(this::canonicalValue).collect(Collectors.joining("|"));
    }

    private String canonicalValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> String.valueOf(entry.getKey()) + "="
                            + canonicalValue(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::canonicalValue).sorted()
                    .collect(Collectors.joining(",", "[", "]"));
        }
        return String.valueOf(value);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private int bounded(int value) {
        return Math.max(1, Math.min(value <= 0 ? 200 : value, 1000));
    }
}
