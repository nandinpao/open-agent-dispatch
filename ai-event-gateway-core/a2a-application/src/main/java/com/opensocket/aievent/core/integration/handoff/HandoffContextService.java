package com.opensocket.aievent.core.integration.handoff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.opensocket.aievent.core.a2a.application.port.out.HandoffDispatchReleasePort;
import com.opensocket.aievent.core.a2a.core.HandoffDispatchReleaseGate;
import com.opensocket.aievent.core.a2a.core.HandoffSensitiveDataGuard;
import com.opensocket.aievent.core.a2a.core.HandoffSnapshotIntegrityGuard;
import com.opensocket.aievent.core.assignment.TaskAssignment;
import com.opensocket.aievent.core.assignment.TaskAssignmentRepository;
import com.opensocket.aievent.core.dispatch.DispatchRequest;
import com.opensocket.aievent.core.dispatch.DispatchRequestRepository;
import com.opensocket.aievent.core.organization.SensitivityLevel;
import com.opensocket.aievent.core.task.TaskRecord;
import com.opensocket.aievent.core.task.TaskRepository;
import com.opensocket.aievent.core.task.TaskStatus;
import com.opensocket.aievent.core.task.domain.TaskActorType;
import com.opensocket.aievent.core.task.domain.TaskStateTransitionCommand;

/** Canonical application authority for immutable Handoff Context aggregates. */
public class HandoffContextService {
    private static final int MAX_RELEASE_ATTEMPTS = 8;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(15);

    private final HandoffSnapshotIntegrityGuard integrityGuard = new HandoffSnapshotIntegrityGuard();
    private final HandoffSensitiveDataGuard sensitiveDataGuard = new HandoffSensitiveDataGuard();
    private final HandoffDispatchReleaseGate releaseGate = new HandoffDispatchReleaseGate();
    private final HandoffContextRepository repository;
    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final DispatchRequestRepository dispatches;
    private final HandoffDispatchReleasePort dispatchRelease;
    private final HandoffDomainEventPublisher domainEvents;

    @Autowired
    public HandoffContextService(HandoffContextRepository repository, TaskRepository tasks,
            TaskAssignmentRepository assignments, DispatchRequestRepository dispatches,
            HandoffDispatchReleasePort dispatchRelease, HandoffDomainEventPublisher domainEvents) {
        this.repository = repository;
        this.tasks = tasks;
        this.assignments = assignments;
        this.dispatches = dispatches;
        this.dispatchRelease = dispatchRelease;
        this.domainEvents = domainEvents;
    }

    /** Compatibility constructor for focused contract tests. */
    public HandoffContextService(HandoffContextRepository repository, TaskRepository tasks,
            TaskAssignmentRepository assignments, DispatchRequestRepository dispatches) {
        this(repository, tasks, assignments, dispatches, HandoffDispatchReleasePort.noop(),
                HandoffDomainEventPublisher.noop());
    }

    public HandoffContextService(HandoffContextRepository repository, TaskRepository tasks,
            TaskAssignmentRepository assignments, DispatchRequestRepository dispatches,
            HandoffDispatchReleasePort dispatchRelease) {
        this(repository, tasks, assignments, dispatches, dispatchRelease, HandoffDomainEventPublisher.noop());
    }

    public HandoffContextPolicy policy(String tenant, String id) {
        return repository.findPolicy(req(tenant, "tenantId"), req(id, "policyId"))
                .orElseThrow(() -> new IllegalArgumentException("Handoff Context Policy not found in Tenant: " + id));
    }

    public List<HandoffContextPolicy> policies(String tenant, int limit) {
        return repository.listPolicies(req(tenant, "tenantId"), bound(limit));
    }

    @Transactional
    public HandoffContextPolicy savePolicy(String tenant, String id, HandoffContextPolicy body, Long expected) {
        String t = req(tenant, "tenantId");
        String p = req(id, "policyId");
        var old = repository.findPolicy(t, p);
        version(old.map(HandoffContextPolicy::version).orElse(0L), expected);
        var now = OffsetDateTime.now();
        var value = new HandoffContextPolicy(t, p, req(body.policyName(), "policyName"),
                body.policyType() == null ? HandoffContextPolicyType.SUMMARY_ONLY : body.policyType(),
                body.contextRequirement() == null ? HandoffContextRequirement.OPTIONAL : body.contextRequirement(),
                body.defaultFieldDecision() == null ? HandoffFieldShareDecision.OMIT : body.defaultFieldDecision(),
                normalizeAttachment(body.attachmentPolicy()),
                body.approvalMode() == null ? HandoffApprovalMode.NONE : body.approvalMode(),
                safe(body.allowedFieldPaths()), safe(body.allowedCommentTypes()),
                body.maskingRules() == null ? Map.of() : Map.copyOf(body.maskingRules()),
                body.resultSharingPolicy() == null ? ResultSharingPolicyType.SUMMARY_ONLY : body.resultSharingPolicy(),
                body.enabled(), old.map(v -> v.version() + 1).orElse(1L),
                old.map(HandoffContextPolicy::createdAt).orElse(now), now);
        return repository.savePolicy(value);
    }

    public HandoffContextPreview preview(String tenant, String sourceTaskId, String targetTaskId,
            String policyId, String summary, Map<String, Object> sourceContext,
            List<HandoffContextField> requestedFields, List<String> commentRefs,
            List<HandoffAttachmentMetadata> attachments, SensitivityLevel sensitivity) {
        String t = req(tenant, "tenantId");
        TaskRecord source = task(t, sourceTaskId);
        TaskRecord target = task(t, targetTaskId);
        sameRoot(source, target);
        HandoffContextPolicy p = policy(t, policyId);
        Map<String, Object> shared = new LinkedHashMap<>();
        List<HandoffContextField> decisions = new ArrayList<>();
        List<String> redacted = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> input = sourceContext == null ? Map.of() : sourceContext;
        Map<String, HandoffContextField> explicit = (requestedFields == null
                ? List.<HandoffContextField>of() : requestedFields).stream()
                .filter(Objects::nonNull).filter(value -> value.fieldPath() != null)
                .collect(Collectors.toMap(HandoffContextField::fieldPath, value -> value,
                        (left, right) -> right, LinkedHashMap::new));

        for (var entry : input.entrySet()) {
            String path = entry.getKey();
            HandoffContextField supplied = explicit.get(path);
            HandoffFieldShareDecision decision = decision(p, path, supplied);
            if (sensitiveDataGuard.isForbiddenPath(path)
                    || sensitiveDataGuard.containsForbiddenContent(entry.getValue())) {
                decision = HandoffFieldShareDecision.OMIT;
                omitted.add(path + ": forbidden credential, token or raw payload container");
                warnings.add("Field " + path + " was omitted by the Phase 2E secret hygiene guard.");
            }
            String masking = supplied == null ? null : supplied.maskingMethod();
            Object projected = null;
            if (decision == HandoffFieldShareDecision.ALLOW) {
                projected = entry.getValue();
                shared.put(path, projected);
            } else if (decision == HandoffFieldShareDecision.MASK) {
                projected = mask(entry.getValue(), masking == null ? p.maskingRules().get(path) : masking);
                shared.put(path, projected);
                redacted.add(path);
            } else if (decision == HandoffFieldShareDecision.REQUIRE_APPROVAL) {
                omitted.add(path + ": approval required");
                warnings.add("Field " + path + " requires approval before sharing.");
            } else if (omitted.stream().noneMatch(reason -> reason.startsWith(path + ":"))) {
                omitted.add(path + ": omitted by policy");
            }
            decisions.add(new HandoffContextField(path,
                    supplied == null ? null : supplied.classification(),
                    supplied == null ? (sensitivity == null ? SensitivityLevel.INTERNAL : sensitivity)
                            : supplied.sensitivityLevel(),
                    decision, masking, supplied == null ? null : supplied.sourceReference(), projected,
                    hash(String.valueOf(entry.getValue()))));
        }

        List<String> allowedComments = new ArrayList<>();
        if (allowsComments(p)) {
            for (String reference : safe(commentRefs)) {
                try {
                    sensitiveDataGuard.requireSafeReference(reference);
                    allowedComments.add(reference);
                } catch (IllegalArgumentException exception) {
                    omitted.add("Comment reference omitted by secret hygiene guard.");
                }
            }
        } else if (commentRefs != null && !commentRefs.isEmpty()) {
            omitted.add("Comments omitted by Context Policy.");
        }
        List<HandoffAttachmentMetadata> metadata = attachmentMetadata(p, attachments, omitted);
        String safeSummary = p.policyType() == HandoffContextPolicyType.NONE ? null : trim(summary);
        if (sensitiveDataGuard.containsForbiddenContent(safeSummary)) {
            safeSummary = null;
            omitted.add("Summary omitted by secret hygiene guard.");
            warnings.add("Summary contained credential-like material and was not persisted.");
        }
        boolean approval = p.approvalMode() != HandoffApprovalMode.NONE
                || decisions.stream().anyMatch(value -> value.shareDecision() == HandoffFieldShareDecision.REQUIRE_APPROVAL)
                || p.policyType() == HandoffContextPolicyType.FULL_APPROVED_SNAPSHOT;
        String contentHash = hash(canonical(sourceTaskId, targetTaskId, policyId, safeSummary, shared,
                allowedComments, metadata, redacted, omitted));
        return new HandoffContextPreview(sourceTaskId, targetTaskId, policyId, safeSummary,
                Map.copyOf(shared), List.copyOf(decisions), List.copyOf(allowedComments),
                List.copyOf(metadata), List.copyOf(redacted), List.copyOf(omitted),
                sensitivity == null ? SensitivityLevel.INTERNAL : sensitivity,
                approval, contentHash, List.copyOf(warnings));
    }

    @Transactional
    public HandoffContextSnapshot createSnapshot(String tenant, String rootTaskId,
            HandoffContextPreview preview, String actorType, String actorId,
            String correlationId, OffsetDateTime expiresAt) {
        return createSnapshotInternal(tenant, rootTaskId, preview, actorType, actorId,
                correlationId, expiresAt, null);
    }

    private HandoffContextSnapshot createSnapshotInternal(String tenant, String rootTaskId,
            HandoffContextPreview preview, String actorType, String actorId,
            String correlationId, OffsetDateTime expiresAt, String supersedesSnapshotId) {
        String t = req(tenant, "tenantId");
        TaskRecord source = task(t, preview.sourceTaskId());
        TaskRecord target = task(t, preview.targetTaskId());
        sameRoot(source, target);
        String root = blank(source.getRootTaskId()) ? source.getTaskId() : source.getRootTaskId();
        if (!blank(rootTaskId) && !root.equals(rootTaskId)) {
            throw new IllegalArgumentException(HandoffContextReasonCode.CROSS_TENANT_HANDOFF_DENIED.name()
                    + ": rootTaskId mismatch.");
        }
        HandoffContextPolicy currentPolicy = policy(t, preview.policyId());
        String aggregateId = integrityGuard.aggregateId(t, preview.sourceTaskId(),
                preview.targetTaskId(), preview.policyId());
        var existing = repository.listSnapshotsForTask(t, preview.targetTaskId(), 1000).stream()
                .filter(value -> aggregateId.equals(value.aggregateId()))
                .filter(value -> value.status() == HandoffSnapshotStatus.PENDING_APPROVAL
                        || value.status() == HandoffSnapshotStatus.APPROVED)
                .findFirst();
        if (existing.isPresent() && Objects.equals(existing.get().structuredContext(), preview.sharedContext())
                && Objects.equals(existing.get().summary(), preview.summary())) {
            return existing.get();
        }
        if (existing.isPresent()) {
            throw new IllegalStateException(HandoffContextReasonCode.HANDOFF_CONTEXT_SUPERSEDE_CONFLICT.name()
                    + ": active Aggregate version must be superseded first.");
        }
        int snapshotVersion = repository.listSnapshotsForTask(t, preview.targetTaskId(), 1000).stream()
                .filter(value -> aggregateId.equals(value.aggregateId()))
                .mapToInt(HandoffContextSnapshot::snapshotVersion).max().orElse(0) + 1;
        OffsetDateTime now = OffsetDateTime.now();
        HandoffSnapshotStatus status = preview.approvalRequired()
                ? HandoffSnapshotStatus.PENDING_APPROVAL : HandoffSnapshotStatus.APPROVED;
        String targetDomain = normalize(target.getExecutorDomainId());
        String sourceAgent = normalize(source.getRequestingAgentId());
        String targetAgent = assignments.findOpenByTenantAndTaskId(t, target.getTaskId())
                .map(TaskAssignment::getAgentId).map(this::normalize).orElse("UNASSIGNED");
        String targetBindingHash = integrityGuard.targetBindingHash(t, target.getTaskId(), targetAgent, targetDomain);
        String approvalEvidenceHash = status == HandoffSnapshotStatus.APPROVED
                ? integrityGuard.approvalEvidenceHash(List.of("AUTO", actorType, actorId, now)) : null;
        int schemaVersion = HandoffSnapshotIntegrityGuard.SNAPSHOT_SCHEMA_VERSION;
        String immutableHash = integrityGuard.contentHash(schemaVersion, t, root, preview.sourceTaskId(),
                preview.targetTaskId(), sourceAgent, targetAgent, targetDomain, targetBindingHash,
                preview.policyId(), currentPolicy.version(), snapshotVersion, preview.summary(),
                preview.sharedContext(), preview.allowedCommentRefs(), preview.attachmentMetadata(),
                preview.redactedFieldPaths(), preview.omittedContentReasons());
        HandoffSnapshotReleaseStatus releaseStatus = status == HandoffSnapshotStatus.APPROVED
                ? HandoffSnapshotReleaseStatus.READY : HandoffSnapshotReleaseStatus.WAITING_APPROVAL;
        var snapshot = new HandoffContextSnapshot(t, "hctx-" + UUID.randomUUID(), aggregateId,
                schemaVersion, root, preview.sourceTaskId(), preview.targetTaskId(), sourceAgent,
                targetAgent, targetDomain, targetBindingHash, preview.policyId(), currentPolicy.version(),
                snapshotVersion, preview.summary(), preview.sharedContext(), preview.allowedCommentRefs(),
                preview.attachmentMetadata(), preview.redactedFieldPaths(), preview.omittedContentReasons(),
                preview.sensitivityLevel(), immutableHash, now, now, req(actorType, "createdByType"),
                req(actorId, "createdById"), expiresAt, status,
                status == HandoffSnapshotStatus.APPROVED ? actorId : null,
                status == HandoffSnapshotStatus.APPROVED ? now : null,
                approvalEvidenceHash, supersedesSnapshotId, correlationId, preview.fieldDecisions(),
                releaseStatus, null, null, null, HandoffReconciliationClassification.NONE,
                status == HandoffSnapshotStatus.APPROVED ? now : null, 0, 1L);
        HandoffContextSnapshot saved = repository.saveSnapshot(snapshot);
        publish(saved, HandoffDomainEventType.HANDOFF_SNAPSHOT_CREATED, actorType, actorId,
                Map.of("status", saved.status().name(), "contentHash", saved.contentHash(),
                        "aggregateId", saved.aggregateId(), "schemaVersion", saved.schemaVersion()));
        if (saved.status() == HandoffSnapshotStatus.APPROVED) {
            recordEvidence(saved, HandoffReleaseEvidenceType.SNAPSHOT_READY,
                    HandoffSnapshotReleaseStatus.READY, HandoffReconciliationClassification.NONE,
                    null, "AUTO_APPROVED", actorType, actorId);
            publish(saved, HandoffDomainEventType.HANDOFF_SNAPSHOT_APPROVED, actorType, actorId,
                    Map.of("approvalMode", "AUTO", "approvalEvidenceHash", saved.approvalEvidenceHash()));
            return releaseApprovedSnapshot(saved, actorType, actorId, false);
        }
        return saved;
    }

    public HandoffContextSnapshot snapshot(String tenant, String id) {
        return repository.findSnapshot(req(tenant, "tenantId"), req(id, "snapshotId"))
                .orElseThrow(() -> new IllegalArgumentException("Handoff Context Snapshot not found in Tenant: " + id));
    }

    public List<HandoffContextSnapshot> snapshots(String tenant, String taskId, int limit) {
        return repository.listSnapshotsForTask(req(tenant, "tenantId"), req(taskId, "taskId"), bound(limit));
    }

    public List<HandoffReleaseEvidence> releaseEvidence(String tenant, String snapshotId, int limit) {
        return repository.listReleaseEvidence(req(tenant, "tenantId"), req(snapshotId, "snapshotId"), bound(limit));
    }

    public HandoffContextReadiness readiness(String tenant, String taskId, String policyId,
            HandoffContextCheckpoint checkpoint) {
        String t = req(tenant, "tenantId");
        String task = req(taskId, "taskId");
        HandoffContextPolicy p = policy(t, policyId);
        HandoffContextCheckpoint cp = checkpoint == null ? HandoffContextCheckpoint.DISPATCH : checkpoint;
        var current = repository.latestApprovedSnapshotForTask(t, task)
                .filter(value -> value.expiresAt() == null || value.expiresAt().isAfter(OffsetDateTime.now()));
        boolean required = p.contextRequirement() == HandoffContextRequirement.REQUIRED_BEFORE_DISPATCH
                || (p.contextRequirement() == HandoffContextRequirement.REQUIRED_BEFORE_COMPLETION
                        && cp == HandoffContextCheckpoint.COMPLETION);
        List<String> blockers = new ArrayList<>();
        if (required && current.isEmpty()) blockers.add(HandoffContextReasonCode.HANDOFF_CONTEXT_REQUIRED_BUT_UNAVAILABLE.name());
        if (required && current.isPresent() && current.get().releaseStatus() == HandoffSnapshotReleaseStatus.WAIT_HUMAN) {
            blockers.add(firstNonBlank(current.get().lastReleaseErrorCode(), HandoffContextReasonCode.HANDOFF_CONTEXT_RELEASE_FAILED.name()));
        }
        List<String> warnings = !required && current.isEmpty()
                && p.contextRequirement() == HandoffContextRequirement.OPTIONAL
                        ? List.of("No approved Handoff Context Snapshot is available. Dispatch may continue because the policy is OPTIONAL.")
                        : List.of();
        return new HandoffContextReadiness(blockers.isEmpty(), p.contextRequirement(), cp,
                current.map(HandoffContextSnapshot::snapshotId).orElse(null),
                current.map(HandoffContextSnapshot::snapshotVersion).orElse(null), blockers, warnings);
    }

    @Transactional
    public HandoffContextSnapshot approve(String tenant, String snapshotId,
            HandoffApprovalDecision decision, String actorType, String actorId,
            String reason, String idempotencyKey, String correlationId) {
        String t = req(tenant, "tenantId");
        String key = req(idempotencyKey, "idempotencyKey");
        HandoffContextSnapshot snapshot = snapshot(t, snapshotId);
        var prior = repository.listApprovals(t, snapshotId, 200).stream()
                .filter(value -> key.equals(value.idempotencyKey())).findFirst();
        if (prior.isPresent()) return snapshot(t, snapshotId);
        OffsetDateTime now = OffsetDateTime.now();
        repository.saveApproval(new HandoffContextApproval(t, "hca-" + UUID.randomUUID(), snapshotId,
                decision, req(actorType, "actorType"), req(actorId, "actorId"), reason, key, now, correlationId));
        HandoffContextPolicy p = policy(t, snapshot.contextPolicyId());
        var approvals = repository.listApprovals(t, snapshotId, 200);
        if (decision == HandoffApprovalDecision.REJECT) {
            HandoffContextSnapshot rejected = copyOperational(snapshot, HandoffSnapshotStatus.REJECTED,
                    null, null, null, HandoffSnapshotReleaseStatus.WAIT_HUMAN, null, null,
                    HandoffContextReasonCode.HANDOFF_CONTEXT_REJECTED.name(),
                    HandoffReconciliationClassification.NONE, null, snapshot.reconciliationCount());
            rejected = repository.saveSnapshot(rejected);
            publish(rejected, HandoffDomainEventType.HANDOFF_SNAPSHOT_REJECTED, actorType, actorId,
                    Map.of("decision", "REJECT"));
            return rejected;
        }
        long distinct = approvals.stream().filter(value -> value.decision() == HandoffApprovalDecision.APPROVE)
                .map(HandoffContextApproval::actorId).distinct().count();
        int required = p.approvalMode() == HandoffApprovalMode.DUAL_APPROVAL ? 2 : 1;
        if (distinct < required) return snapshot;
        String approvedBy = approvals.stream().filter(value -> value.decision() == HandoffApprovalDecision.APPROVE)
                .map(HandoffContextApproval::actorId).distinct().sorted().collect(Collectors.joining(","));
        String approvalHash = integrityGuard.approvalEvidenceHash(approvals.stream()
                .sorted(Comparator.comparing(HandoffContextApproval::decidedAt)
                        .thenComparing(HandoffContextApproval::approvalId))
                .map(value -> List.of(value.approvalId(), value.actorId(), value.decision().name(),
                        value.idempotencyKey(), value.decidedAt().toString())).toList());
        HandoffContextSnapshot approved = copyOperational(snapshot, HandoffSnapshotStatus.APPROVED,
                approvedBy, now, approvalHash, HandoffSnapshotReleaseStatus.READY, null, null,
                null, HandoffReconciliationClassification.NONE, now, snapshot.reconciliationCount());
        approved = repository.saveSnapshot(approved);
        recordEvidence(approved, HandoffReleaseEvidenceType.SNAPSHOT_READY,
                HandoffSnapshotReleaseStatus.READY, HandoffReconciliationClassification.NONE,
                null, "APPROVED", actorType, actorId);
        publish(approved, HandoffDomainEventType.HANDOFF_SNAPSHOT_APPROVED, actorType, actorId,
                Map.of("decision", "APPROVE", "approvalEvidenceHash", approvalHash));
        return releaseApprovedSnapshot(approved, actorType, actorId, false);
    }

    @Transactional
    public HandoffContextSnapshot supersede(String tenant, String snapshotId,
            HandoffContextPreview preview, String actorType, String actorId, String correlationId) {
        String t = req(tenant, "tenantId");
        HandoffContextSnapshot old = snapshot(t, snapshotId);
        if (old.status() != HandoffSnapshotStatus.APPROVED) {
            throw new IllegalStateException(HandoffContextReasonCode.HANDOFF_CONTEXT_SUPERSEDE_CONFLICT.name());
        }
        if (old.contentHash().equals(preview.contentHash())) {
            throw new IllegalStateException(HandoffContextReasonCode.HANDOFF_CONTEXT_SUPERSEDE_CONFLICT.name()
                    + ": replacement content is unchanged.");
        }
        HandoffContextSnapshot superseded = repository.saveSnapshot(copyOperational(old,
                HandoffSnapshotStatus.SUPERSEDED, old.approvedBy(), old.approvedAt(),
                old.approvalEvidenceHash(), HandoffSnapshotReleaseStatus.WAIT_HUMAN,
                old.releaseEvidenceId(), old.releasedAt(), "SUPERSEDED",
                HandoffReconciliationClassification.NONE, null, old.reconciliationCount()));
        HandoffContextSnapshot replacement = createSnapshotInternal(t, old.rootTaskId(), preview,
                actorType, actorId, correlationId, old.expiresAt(), old.snapshotId());
        publish(superseded, HandoffDomainEventType.HANDOFF_SNAPSHOT_SUPERSEDED, actorType, actorId,
                Map.of("replacementSnapshotId", replacement.snapshotId()));
        return replacement;
    }

    @Transactional
    public ResultContextSnapshot createResultSnapshot(String tenant, String sourceTaskId,
            String targetTaskId, String policyId, String summary, List<String> evidence,
            Map<String, Object> output, List<String> omitted, String agentId, String correlationId) {
        String t = req(tenant, "tenantId");
        TaskRecord source = task(t, sourceTaskId);
        TaskRecord target = task(t, targetTaskId);
        sameRoot(source, target);
        HandoffContextPolicy p = policy(t, policyId);
        Map<String, Object> safeOutput = p.resultSharingPolicy() == ResultSharingPolicyType.NONE
                || p.resultSharingPolicy() == ResultSharingPolicyType.SUMMARY_ONLY
                        ? Map.of() : safeOutput(output);
        List<String> safeEvidence = p.resultSharingPolicy() == ResultSharingPolicyType.SELECTED_EVIDENCE
                || p.resultSharingPolicy() == ResultSharingPolicyType.FULL_APPROVED_RESULT
                        ? safeReferences(evidence) : List.of();
        String safeSummary = sensitiveDataGuard.containsForbiddenContent(summary) ? null : trim(summary);
        List<String> omittedReasons = new ArrayList<>(safe(omitted));
        if (safeSummary == null && summary != null) omittedReasons.add("Result summary omitted by secret hygiene guard.");
        String contentHash = hash(canonical(sourceTaskId, targetTaskId, policyId, safeSummary,
                safeOutput, safeEvidence, omittedReasons));
        var existing = repository.listResultSnapshots(t, targetTaskId, 1000).stream()
                .filter(value -> contentHash.equals(value.resultContentHash())).findFirst();
        if (existing.isPresent()) return existing.get();
        int snapshotVersion = repository.listResultSnapshots(t, targetTaskId, 1000).stream()
                .filter(value -> policyId.equals(value.policyId()))
                .mapToInt(ResultContextSnapshot::snapshotVersion).max().orElse(0) + 1;
        String root = blank(source.getRootTaskId()) ? source.getTaskId() : source.getRootTaskId();
        ResultContextSnapshot saved = repository.saveResultSnapshot(new ResultContextSnapshot(t,
                "rctx-" + UUID.randomUUID(), root, sourceTaskId, targetTaskId, policyId,
                snapshotVersion, safeSummary, safeEvidence, safeOutput, List.copyOf(omittedReasons),
                contentHash, agentId, OffsetDateTime.now(), "CREATED", correlationId));
        domainEvents.publish(new HandoffDomainModuleEvent("evt-" + UUID.randomUUID(),
                HandoffDomainEventType.HANDOFF_RESULT_SNAPSHOT_CREATED, t, saved.resultSnapshotId(),
                root, sourceTaskId, targetTaskId, correlationId, null, "AGENT", agentId,
                OffsetDateTime.now(), Map.of("resultSnapshotId", saved.resultSnapshotId(),
                        "resultContentHash", saved.resultContentHash())));
        return saved;
    }

    @Transactional
    public AgentTaskContext agentContext(String tenant, String taskId, String agentId,
            String assignmentId, String agentSessionId, String dispatchToken,
            String clientAddress, String correlationId) {
        String t = req(tenant, "tenantId");
        String tokenHash = dispatchToken == null ? null : hash(dispatchToken);
        try {
            TaskAssignment assignment = assignments.findOpenByTenantAndTaskId(t, req(taskId, "taskId"))
                    .orElseThrow(() -> new IllegalStateException(
                            HandoffContextReasonCode.AGENT_CONTEXT_ASSIGNMENT_MISMATCH.name()));
            if (!req(agentId, "agentId").equals(assignment.getAgentId())
                    || !req(assignmentId, "assignmentId").equals(assignment.getAssignmentId())) {
                throw new IllegalStateException(HandoffContextReasonCode.AGENT_CONTEXT_ASSIGNMENT_MISMATCH.name());
            }
            if (!blank(assignment.getAgentSessionId())
                    && !Objects.equals(assignment.getAgentSessionId(), agentSessionId)) {
                throw new IllegalStateException(HandoffContextReasonCode.AGENT_CONTEXT_SESSION_MISMATCH.name());
            }
            DispatchRequest dispatch = dispatches.findOpenByAssignmentId(assignmentId)
                    .orElseThrow(() -> new IllegalStateException(
                            HandoffContextReasonCode.AGENT_CONTEXT_DISPATCH_TOKEN_MISMATCH.name()));
            if (!constantEquals(dispatch.getDispatchToken(), dispatchToken)
                    || !taskId.equals(dispatch.getTaskId()) || !agentId.equals(dispatch.getAgentId())) {
                throw new IllegalStateException(HandoffContextReasonCode.AGENT_CONTEXT_DISPATCH_TOKEN_MISMATCH.name());
            }
            HandoffContextSnapshot snapshot = repository.latestApprovedSnapshotForTask(t, taskId)
                    .orElseThrow(() -> new IllegalStateException(
                            HandoffContextReasonCode.HANDOFF_CONTEXT_REQUIRED_BUT_UNAVAILABLE.name()));
            if (snapshot.releaseStatus() != HandoffSnapshotReleaseStatus.RELEASED) {
                throw new IllegalStateException(HandoffContextReasonCode.HANDOFF_CONTEXT_RELEASE_FAILED.name());
            }
            TaskRecord targetTask = task(t, taskId);
            String targetDomain = normalize(targetTask.getExecutorDomainId());
            if (!Objects.equals(snapshot.targetDomainId(), targetDomain)) {
                throw new IllegalStateException(HandoffContextReasonCode.HANDOFF_CONTEXT_TARGET_BINDING_MISMATCH.name());
            }
            if (!blank(snapshot.targetAgentId()) && !"UNASSIGNED".equals(snapshot.targetAgentId())
                    && !Objects.equals(snapshot.targetAgentId(), agentId)) {
                throw new IllegalStateException(HandoffContextReasonCode.HANDOFF_CONTEXT_TARGET_AGENT_MISMATCH.name());
            }
            String actualHash = integrityGuard.contentHash(snapshot.schemaVersion(), snapshot.tenantId(),
                    snapshot.rootTaskId(), snapshot.sourceTaskId(), snapshot.targetTaskId(),
                    snapshot.sourceAgentId(), snapshot.targetAgentId(), snapshot.targetDomainId(),
                    snapshot.targetBindingHash(), snapshot.contextPolicyId(), snapshot.policyVersion(),
                    snapshot.snapshotVersion(), snapshot.summary(), snapshot.structuredContext(),
                    snapshot.allowedCommentRefs(), snapshot.attachmentMetadata(), snapshot.redactedFieldPaths(),
                    snapshot.omittedContentReasons());
            integrityGuard.verify(t, taskId, targetDomain, snapshot.expiresAt(), snapshot.status().name(),
                    snapshot.contentHash(), actualHash);
            integrityGuard.verifyBinding(taskId, snapshot.targetTaskId(),
                    HandoffContextReasonCode.HANDOFF_CONTEXT_TARGET_BINDING_MISMATCH.name());
            access(t, taskId, assignmentId, dispatch.getDispatchRequestId(), agentId, agentSessionId,
                    snapshot, "ALLOWED", "HANDOFF_CONTEXT_SNAPSHOT_DELIVERED", tokenHash,
                    clientAddress, correlationId);
            return new AgentTaskContext(taskId, assignmentId, dispatch.getDispatchRequestId(), agentId,
                    agentSessionId, snapshot.snapshotId(), snapshot.snapshotVersion(), snapshot.summary(),
                    snapshot.structuredContext(), snapshot.allowedCommentRefs(), snapshot.attachmentMetadata(),
                    List.of(), snapshot.contentHash());
        } catch (RuntimeException exception) {
            access(t, taskId, null, null, agentId, agentSessionId, null, "DENIED",
                    exception.getMessage() == null
                            ? HandoffContextReasonCode.AGENT_CONTEXT_ACCESS_DENIED.name()
                            : exception.getMessage(),
                    tokenHash, clientAddress, correlationId);
            throw exception;
        }
    }

    public List<AgentContextAccessEvent> accessEvents(String tenant, String taskId, int limit) {
        return repository.listAccessEvents(req(tenant, "tenantId"), taskId, bound(limit));
    }

    @Transactional
    public HandoffContextSnapshot retryRelease(String tenant, String snapshotId,
            String actorType, String actorId, String reason) {
        HandoffContextSnapshot current = snapshot(tenant, snapshotId);
        if (current.releaseStatus() != HandoffSnapshotReleaseStatus.FAILED_RETRYABLE
                && current.releaseStatus() != HandoffSnapshotReleaseStatus.READY) {
            throw new IllegalStateException("HANDOFF_RELEASE_NOT_RETRYABLE");
        }
        HandoffContextSnapshot ready = repository.saveSnapshot(copyOperational(current,
                current.status(), current.approvedBy(), current.approvedAt(), current.approvalEvidenceHash(),
                HandoffSnapshotReleaseStatus.READY, current.releaseEvidenceId(), current.releasedAt(),
                reason, HandoffReconciliationClassification.NONE, OffsetDateTime.now(),
                current.reconciliationCount()));
        return releaseApprovedSnapshot(ready, actorType, actorId, true);
    }

    @Transactional
    public HandoffReconciliationSummary reconcileDue(int limit) {
        List<HandoffContextSnapshot> candidates = repository.findReleaseCandidates(OffsetDateTime.now(), bound(limit));
        int released = 0;
        int deferred = 0;
        int waitHuman = 0;
        int expired = 0;
        for (HandoffContextSnapshot candidate : candidates) {
            try {
                HandoffContextSnapshot result = releaseApprovedSnapshot(candidate, "SYSTEM",
                        "handoff-reconciler", true);
                if (result.releaseStatus() == HandoffSnapshotReleaseStatus.RELEASED) released++;
                else if (result.status() == HandoffSnapshotStatus.EXPIRED) expired++;
                else if (result.releaseStatus() == HandoffSnapshotReleaseStatus.WAIT_HUMAN) waitHuman++;
                else deferred++;
            } catch (RuntimeException exception) {
                deferred++;
            }
        }
        return new HandoffReconciliationSummary(candidates.size(), released, deferred, waitHuman, expired);
    }

    private HandoffContextSnapshot releaseApprovedSnapshot(HandoffContextSnapshot original,
            String actorType, String actorId, boolean reconciliation) {
        HandoffContextSnapshot current = snapshot(original.tenantId(), original.snapshotId());
        TaskRecord source = task(current.tenantId(), current.sourceTaskId());
        TaskRecord target = task(current.tenantId(), current.targetTaskId());
        String currentTargetAgent = assignments.findOpenByTenantAndTaskId(current.tenantId(), current.targetTaskId())
                .map(TaskAssignment::getAgentId).map(this::normalize).orElse("UNASSIGNED");
        var binding = new HandoffDispatchReleaseGate.CurrentBinding(current.tenantId(),
                current.sourceTaskId(), current.targetTaskId(), normalize(source.getRequestingAgentId()),
                currentTargetAgent, normalize(target.getExecutorDomainId()), current.policyVersion());
        var decision = releaseGate.evaluate(current, binding, OffsetDateTime.now());
        if (!decision.allowed()) {
            return quarantineRelease(current, decision.reasonCode(), actorType, actorId, reconciliation);
        }
        int attempt = current.reconciliationCount() + 1;
        HandoffReconciliationClassification classification = reconciliation
                ? HandoffReconciliationClassification.RELEASE_EVENT_MISSING
                : HandoffReconciliationClassification.NONE;
        HandoffContextSnapshot releasing = repository.saveSnapshot(copyOperational(current,
                current.status(), current.approvedBy(), current.approvedAt(), current.approvalEvidenceHash(),
                HandoffSnapshotReleaseStatus.RELEASING, current.releaseEvidenceId(), current.releasedAt(),
                null, classification, null, attempt));
        recordEvidence(releasing, HandoffReleaseEvidenceType.RELEASE_REQUESTED,
                HandoffSnapshotReleaseStatus.RELEASING, classification, null,
                reconciliation ? "RECONCILIATION_RELEASE" : "APPROVAL_RELEASE", actorType, actorId);
        publish(releasing, HandoffDomainEventType.HANDOFF_DISPATCH_RELEASE_REQUESTED,
                actorType, actorId, Map.of("attempt", attempt));
        try {
            TaskRecord releasedTask = releaseTaskAuthority(releasing, target, actorType, actorId);
            HandoffDispatchReleaseResult result = dispatchRelease.requestDispatch(releasedTask,
                    releasing.correlationId());
            if (!result.accepted()) throw new IllegalStateException("HANDOFF_CONTEXT_RELEASE_NOT_ACCEPTED");
            String evidenceId = recordEvidence(releasing, HandoffReleaseEvidenceType.RELEASED,
                    HandoffSnapshotReleaseStatus.RELEASED, classification,
                    result.evidenceReference(), result.replayed() ? "IDEMPOTENT_REPLAY" : "DISPATCH_INTENT_SAVED",
                    actorType, actorId).evidenceId();
            HandoffContextSnapshot released = repository.saveSnapshot(copyOperational(releasing,
                    releasing.status(), releasing.approvedBy(), releasing.approvedAt(),
                    releasing.approvalEvidenceHash(), HandoffSnapshotReleaseStatus.RELEASED,
                    evidenceId, OffsetDateTime.now(), null, HandoffReconciliationClassification.NONE,
                    null, attempt));
            publish(released, reconciliation ? HandoffDomainEventType.HANDOFF_RECONCILED
                    : HandoffDomainEventType.HANDOFF_DISPATCH_RELEASED, actorType, actorId,
                    Map.of("releaseEvidenceId", evidenceId,
                            "dispatchEvidenceReference", firstNonBlank(result.evidenceReference(), "none")));
            return released;
        } catch (RuntimeException exception) {
            HandoffReconciliationClassification failure = HandoffReconciliationClassification.RELEASE_PERSISTENCE_UNCERTAIN;
            if (attempt >= MAX_RELEASE_ATTEMPTS) {
                HandoffContextSnapshot waitHuman = repository.saveSnapshot(copyOperational(releasing,
                        releasing.status(), releasing.approvedBy(), releasing.approvedAt(),
                        releasing.approvalEvidenceHash(), HandoffSnapshotReleaseStatus.WAIT_HUMAN,
                        releasing.releaseEvidenceId(), releasing.releasedAt(),
                        HandoffContextReasonCode.HANDOFF_CONTEXT_RELEASE_RETRY_EXHAUSTED.name(),
                        HandoffReconciliationClassification.RETRY_EXHAUSTED, null, attempt));
                recordEvidence(waitHuman, HandoffReleaseEvidenceType.RELEASE_FAILED,
                        HandoffSnapshotReleaseStatus.WAIT_HUMAN,
                        HandoffReconciliationClassification.RETRY_EXHAUSTED, null,
                        safeMessage(exception), actorType, actorId);
                return waitHuman;
            }
            OffsetDateTime retryAt = OffsetDateTime.now().plus(retryDelay(attempt));
            HandoffContextSnapshot failed = repository.saveSnapshot(copyOperational(releasing,
                    releasing.status(), releasing.approvedBy(), releasing.approvedAt(),
                    releasing.approvalEvidenceHash(), HandoffSnapshotReleaseStatus.FAILED_RETRYABLE,
                    releasing.releaseEvidenceId(), releasing.releasedAt(),
                    HandoffContextReasonCode.HANDOFF_CONTEXT_RELEASE_FAILED.name(), failure,
                    retryAt, attempt));
            recordEvidence(failed, HandoffReleaseEvidenceType.RELEASE_FAILED,
                    HandoffSnapshotReleaseStatus.FAILED_RETRYABLE, failure, null,
                    safeMessage(exception), actorType, actorId);
            publish(failed, HandoffDomainEventType.HANDOFF_RECONCILIATION_REQUIRED, actorType, actorId,
                    Map.of("classification", failure.name(), "nextReconcileAt", retryAt.toString()));
            return failed;
        }
    }

    private HandoffContextSnapshot quarantineRelease(HandoffContextSnapshot current, String reasonCode,
            String actorType, String actorId, boolean reconciliation) {
        HandoffReconciliationClassification classification;
        HandoffReleaseEvidenceType evidenceType;
        HandoffSnapshotStatus status = current.status();
        if (HandoffContextReasonCode.HANDOFF_CONTEXT_SNAPSHOT_EXPIRED.name().equals(reasonCode)) {
            classification = HandoffReconciliationClassification.SNAPSHOT_EXPIRED;
            evidenceType = HandoffReleaseEvidenceType.EXPIRED;
            status = HandoffSnapshotStatus.EXPIRED;
        } else if (HandoffContextReasonCode.HANDOFF_CONTEXT_CONTENT_HASH_CONFLICT.name().equals(reasonCode)) {
            classification = HandoffReconciliationClassification.HASH_CONFLICT;
            evidenceType = HandoffReleaseEvidenceType.HASH_CONFLICT;
        } else {
            classification = HandoffReconciliationClassification.TARGET_BINDING_CHANGED;
            evidenceType = HandoffReleaseEvidenceType.BINDING_CHANGED;
        }
        HandoffContextSnapshot quarantined = repository.saveSnapshot(copyOperational(current, status,
                current.approvedBy(), current.approvedAt(), current.approvalEvidenceHash(),
                HandoffSnapshotReleaseStatus.WAIT_HUMAN, current.releaseEvidenceId(), current.releasedAt(),
                reasonCode, classification, null, current.reconciliationCount() + (reconciliation ? 1 : 0)));
        recordEvidence(quarantined, evidenceType, HandoffSnapshotReleaseStatus.WAIT_HUMAN,
                classification, null, reasonCode, actorType, actorId);
        publish(quarantined, HandoffDomainEventType.HANDOFF_RECONCILIATION_REQUIRED,
                actorType, actorId, Map.of("classification", classification.name(), "reasonCode", reasonCode));
        return quarantined;
    }

    private TaskRecord releaseTaskAuthority(HandoffContextSnapshot snapshot, TaskRecord target,
            String actorType, String actorId) {
        if (target.getStatus() != TaskStatus.WAITING_CONTEXT) return target;
        TaskActorType actor;
        try {
            actor = TaskActorType.valueOf(actorType == null ? "SYSTEM" : actorType.toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            actor = TaskActorType.SYSTEM;
        }
        return tasks.transitionGovernanceState(new TaskStateTransitionCommand(snapshot.tenantId(),
                target.getTaskId(), target.getVersion(), TaskStatus.QUEUED, "HANDOFF_CONTEXT_READY",
                "Approved Handoff Context Snapshot " + snapshot.snapshotId() + " passed the Phase 2E release gate.",
                actor, actorId == null ? "HANDOFF_CONTEXT" : actorId, snapshot.correlationId(),
                "handoff-context-ready:" + snapshot.snapshotId(), OffsetDateTime.now()))
                .orElseThrow(() -> new IllegalStateException(
                        "RESOURCE_VERSION_CONFLICT: Handoff Context release failed for Task " + target.getTaskId()));
    }

    private HandoffReleaseEvidence recordEvidence(HandoffContextSnapshot snapshot,
            HandoffReleaseEvidenceType type, HandoffSnapshotReleaseStatus releaseStatus,
            HandoffReconciliationClassification classification, String dispatchReference,
            String reasonCode, String actorType, String actorId) {
        int attempt = Math.max(1, snapshot.reconciliationCount());
        HandoffReleaseEvidence evidence = new HandoffReleaseEvidence(snapshot.tenantId(),
                "hrel-" + UUID.randomUUID(), snapshot.snapshotId(), type, releaseStatus,
                classification, dispatchReference, reasonCode, attempt,
                firstNonBlank(actorType, "SYSTEM"), firstNonBlank(actorId, "opendispatch"),
                snapshot.correlationId(), OffsetDateTime.now());
        return repository.saveReleaseEvidence(evidence);
    }

    private HandoffContextSnapshot copyOperational(HandoffContextSnapshot snapshot,
            HandoffSnapshotStatus status, String approvedBy, OffsetDateTime approvedAt,
            String approvalEvidenceHash, HandoffSnapshotReleaseStatus releaseStatus,
            String releaseEvidenceId, OffsetDateTime releasedAt, String lastReleaseErrorCode,
            HandoffReconciliationClassification classification, OffsetDateTime nextReconcileAt,
            int reconciliationCount) {
        return new HandoffContextSnapshot(snapshot.tenantId(), snapshot.snapshotId(), snapshot.aggregateId(),
                snapshot.schemaVersion(), snapshot.rootTaskId(), snapshot.sourceTaskId(),
                snapshot.targetTaskId(), snapshot.sourceAgentId(), snapshot.targetAgentId(),
                snapshot.targetDomainId(), snapshot.targetBindingHash(), snapshot.contextPolicyId(),
                snapshot.policyVersion(), snapshot.snapshotVersion(), snapshot.summary(),
                snapshot.structuredContext(), snapshot.allowedCommentRefs(), snapshot.attachmentMetadata(),
                snapshot.redactedFieldPaths(), snapshot.omittedContentReasons(), snapshot.sensitivityLevel(),
                snapshot.contentHash(), snapshot.sourceObservedAt(), snapshot.createdAt(),
                snapshot.createdByType(), snapshot.createdById(), snapshot.expiresAt(), status,
                approvedBy, approvedAt, approvalEvidenceHash, snapshot.supersedesSnapshotId(),
                snapshot.correlationId(), snapshot.fieldDecisions(), releaseStatus, releaseEvidenceId,
                releasedAt, lastReleaseErrorCode, classification, nextReconcileAt,
                reconciliationCount, snapshot.rowVersion());
    }

    private void access(String tenant, String task, String assignment, String dispatch,
            String agent, String session, HandoffContextSnapshot snapshot, String decision,
            String reason, String tokenHash, String client, String correlation) {
        repository.saveAccessEvent(new AgentContextAccessEvent(tenant, "ctx-access-" + UUID.randomUUID(),
                task, assignment, dispatch, agent == null ? "unknown" : agent, session,
                snapshot == null ? null : snapshot.snapshotId(), snapshot == null ? 0 : snapshot.snapshotVersion(),
                decision, reason, tokenHash, client, correlation, OffsetDateTime.now()));
    }

    private void publish(HandoffContextSnapshot snapshot, HandoffDomainEventType type,
            String actorType, String actorId, Map<String, Object> payload) {
        domainEvents.publish(new HandoffDomainModuleEvent("evt-" + UUID.randomUUID(), type,
                snapshot.tenantId(), snapshot.snapshotId(), snapshot.rootTaskId(), snapshot.sourceTaskId(),
                snapshot.targetTaskId(), snapshot.correlationId(), null,
                firstNonBlank(actorType, "SYSTEM"), firstNonBlank(actorId, "opendispatch"),
                OffsetDateTime.now(), payload));
    }

    private HandoffFieldShareDecision decision(HandoffContextPolicy policy, String path,
            HandoffContextField explicit) {
        if (policy.policyType() == HandoffContextPolicyType.NONE
                || policy.policyType() == HandoffContextPolicyType.SUMMARY_ONLY
                || policy.policyType() == HandoffContextPolicyType.METADATA_ONLY
                || policy.policyType() == HandoffContextPolicyType.SELECTED_COMMENTS) {
            return HandoffFieldShareDecision.OMIT;
        }
        if (policy.policyType() == HandoffContextPolicyType.FULL_APPROVED_SNAPSHOT) {
            return HandoffFieldShareDecision.REQUIRE_APPROVAL;
        }
        if (!policy.allowedFieldPaths().isEmpty() && !policy.allowedFieldPaths().contains(path)) {
            return HandoffFieldShareDecision.OMIT;
        }
        return explicit != null && explicit.shareDecision() != null
                ? explicit.shareDecision() : policy.defaultFieldDecision();
    }

    private boolean allowsComments(HandoffContextPolicy policy) {
        return policy.policyType() == HandoffContextPolicyType.SELECTED_COMMENTS
                || policy.policyType() == HandoffContextPolicyType.FULL_APPROVED_SNAPSHOT
                || policy.policyType() == HandoffContextPolicyType.CUSTOM_TEMPLATE;
    }

    private List<HandoffAttachmentMetadata> attachmentMetadata(HandoffContextPolicy policy,
            List<HandoffAttachmentMetadata> input, List<String> omitted) {
        if (input == null || input.isEmpty()) return List.of();
        if (policy.attachmentPolicy() == HandoffContextPolicyType.NO_ATTACHMENTS
                || policy.policyType() == HandoffContextPolicyType.NO_ATTACHMENTS) {
            omitted.add("Attachments omitted by policy.");
            return List.of();
        }
        List<HandoffAttachmentMetadata> result = new ArrayList<>();
        for (HandoffAttachmentMetadata value : input) {
            try {
                sensitiveDataGuard.requireSafeReference(value.sourceReference());
                result.add(new HandoffAttachmentMetadata(value.filename(), value.contentType(), value.sizeBytes(),
                        value.sha256(), value.sourceReference(),
                        policy.attachmentPolicy() == HandoffContextPolicyType.FULL_APPROVED_SNAPSHOT
                                ? AttachmentAvailabilityStatus.CONTENT_REQUIRES_APPROVAL
                                : AttachmentAvailabilityStatus.METADATA_AVAILABLE));
            } catch (IllegalArgumentException exception) {
                omitted.add("Attachment metadata omitted by secret hygiene guard: " + value.filename());
            }
        }
        return List.copyOf(result);
    }

    private HandoffContextPolicyType normalizeAttachment(HandoffContextPolicyType value) {
        if (value == HandoffContextPolicyType.NO_ATTACHMENTS
                || value == HandoffContextPolicyType.FULL_APPROVED_SNAPSHOT) return value;
        return HandoffContextPolicyType.ATTACHMENT_METADATA_ONLY;
    }

    private Object mask(Object value, String method) {
        String text = String.valueOf(value);
        String normalized = method == null ? "FULL" : method.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LAST4" -> text.length() <= 4 ? "****"
                    : "*".repeat(Math.max(4, text.length() - 4)) + text.substring(text.length() - 4);
            case "EMAIL" -> {
                int at = text.indexOf('@');
                yield at > 0 ? text.substring(0, 1) + "***" + text.substring(at) : "***";
            }
            case "HASH" -> "sha256:" + hash(text).substring(0, 16);
            case "PARTIAL" -> text.length() < 3 ? "***"
                    : text.substring(0, 1) + "***" + text.substring(text.length() - 1);
            default -> "***";
        };
    }

    private Map<String, Object> safeOutput(Map<String, Object> output) {
        if (output == null || output.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        output.forEach((key, value) -> {
            if (!sensitiveDataGuard.isForbiddenPath(key)
                    && !sensitiveDataGuard.containsForbiddenContent(value)) result.put(key, value);
        });
        return Map.copyOf(result);
    }

    private List<String> safeReferences(List<String> references) {
        List<String> result = new ArrayList<>();
        for (String reference : safe(references)) {
            try {
                sensitiveDataGuard.requireSafeReference(reference);
                result.add(reference);
            } catch (IllegalArgumentException ignored) {
                // Deliberately omitted.
            }
        }
        return List.copyOf(result);
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 10);
        Duration delay = INITIAL_RETRY_DELAY.multipliedBy(multiplier);
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private TaskRecord task(String tenant, String id) {
        return tasks.findByTenantAndId(tenant, req(id, "taskId"))
                .orElseThrow(() -> new IllegalArgumentException("Task not found in Tenant: " + id));
    }

    private void sameRoot(TaskRecord left, TaskRecord right) {
        String leftRoot = blank(left.getRootTaskId()) ? left.getTaskId() : left.getRootTaskId();
        String rightRoot = blank(right.getRootTaskId()) ? right.getTaskId() : right.getRootTaskId();
        if (!Objects.equals(leftRoot, rightRoot)) {
            throw new IllegalArgumentException("Tasks must belong to the same Root Task chain.");
        }
    }

    private void version(long actual, Long expected) {
        if (expected != null && actual != expected) throw new IllegalStateException("RESOURCE_VERSION_CONFLICT");
    }

    private boolean constantEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String canonical(Object... values) {
        return java.util.Arrays.stream(values).map(this::canon).collect(Collectors.joining("|"));
    }

    private String canon(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) return map.entrySet().stream()
                .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                .map(entry -> String.valueOf(entry.getKey()) + "=" + canon(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Collection<?> collection) return collection.stream().map(this::canon).sorted()
                .collect(Collectors.joining(",", "[", "]"));
        return String.valueOf(value);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String req(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required.");
        return value.trim();
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String normalize(String value) { return blank(value) ? "UNASSIGNED" : value.trim(); }
    private int bound(int value) { return Math.max(1, Math.min(value, 1000)); }
    private <T> List<T> safe(List<T> value) { return value == null ? List.of() : List.copyOf(value); }
    private String firstNonBlank(String value, String fallback) { return blank(value) ? fallback : value; }
    private String safeMessage(Throwable value) {
        String message = value == null ? null : value.getMessage();
        if (message == null || message.isBlank()) return "HANDOFF_CONTEXT_RELEASE_FAILED";
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
