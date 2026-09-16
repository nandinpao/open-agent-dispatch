package com.opensocket.aievent.core.enforcement.activation.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanRoute;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceBinding;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;

public final class CutoverPlanService {
    private final CutoverPlanRepository plans;
    private final ReadinessEvidenceRepository evidence;
    private final AuthorityRevisionPublisher publisher;
    private final SnapshotRefreshService refresh;
    private final CutoverPlanValidator validator;
    private final Clock clock;

    public CutoverPlanService(CutoverPlanRepository plans, ReadinessEvidenceRepository evidence,
                              AuthorityRevisionPublisher publisher, SnapshotRefreshService refresh,
                              CutoverPlanValidator validator, Clock clock) {
        this.plans = plans; this.evidence = evidence; this.publisher = publisher; this.refresh = refresh;
        this.validator = validator; this.clock = clock;
    }

    public List<CutoverPlan> list(CutoverPlanStatus status, int limit) { return plans.list(status, bounded(limit)); }
    public CutoverPlan get(UUID id) { return plans.find(id).orElseThrow(() -> fail("CUTOVER_PLAN_NOT_FOUND", "Cutover plan not found.")); }

    public CutoverPlan create(String title, String description, ActorCommand command) {
        requireCommand(command);
        UUID id = UUID.randomUUID();
        return plans.create(id, title, description, command.actorId(), command.auditReason(), command.correlationId(),
                command.idempotencyKey(), hash("CREATE", title, description), clock.instant());
    }

    public CutoverPlan replaceRoutes(UUID id, long expectedVersion, List<CutoverPlanRoute> routes, ActorCommand command) {
        requireCommand(command);
        if (routes == null || routes.size() > 500) throw fail("CUTOVER_PLAN_ROUTE_LIMIT", "A plan supports at most 500 routes.");
        return plans.replaceRoutes(id, expectedVersion, routes, command.actorId(), command.auditReason(), command.correlationId(),
                command.idempotencyKey(), hash("ROUTES", id, expectedVersion, routes), clock.instant());
    }

    public CutoverPlan bindEvidence(UUID id, long expectedVersion, ReadinessEvidenceType type, UUID evidenceId, ActorCommand command) {
        requireCommand(command);
        ReadinessEvidenceBinding binding = evidence.resolve(type, evidenceId)
                .orElseThrow(() -> fail("CUTOVER_EVIDENCE_NOT_FOUND", "Readiness evidence not found."));
        if (!binding.eligibleAt(clock.instant())) throw fail("CUTOVER_EVIDENCE_NOT_ELIGIBLE", "Evidence must be ELIGIBLE/PASS and unexpired.");
        return plans.bindEvidence(id, expectedVersion, binding, command.actorId(), command.auditReason(), command.correlationId(),
                command.idempotencyKey(), hash("EVIDENCE", id, expectedVersion, binding.evidenceType(), binding.evidenceId(), binding.checksum()), clock.instant());
    }

    public CutoverPlan submit(UUID id, long expectedVersion, ActorCommand command) {
        requireCommand(command);
        CutoverPlan plan = get(id);
        requireVersion(plan, expectedVersion);
        if (plan.status() != CutoverPlanStatus.DRAFT) throw fail("CUTOVER_PLAN_STATE_INVALID", "Only DRAFT plans can be submitted.");
        validator.validateForReview(plan);
        validator.validateEvidenceUnchanged(plan.evidenceBindings(), evidence);
        return plans.submit(id, expectedVersion, command.actorId(), command.auditReason(), command.correlationId(),
                command.idempotencyKey(), hash("SUBMIT", id, expectedVersion), clock.instant());
    }

    public CutoverPlan approve(UUID id, long expectedVersion, ActorCommand command) {
        requireCommand(command);
        CutoverPlan plan = get(id);
        requireVersion(plan, expectedVersion);
        if (plan.status() != CutoverPlanStatus.IN_REVIEW) throw fail("CUTOVER_PLAN_STATE_INVALID", "Only IN_REVIEW plans can be approved.");
        validator.requireIndependentApprover(plan, command.actorId());
        validator.validateForReview(plan);
        validator.validateEvidenceUnchanged(plan.evidenceBindings(), evidence);
        return plans.approve(id, expectedVersion, command.actorId(), command.auditReason(), command.correlationId(),
                command.idempotencyKey(), hash("APPROVE", id, expectedVersion), clock.instant());
    }

    public CutoverPlan reject(UUID id, long expectedVersion, String rejectionReason, ActorCommand command) {
        requireCommand(command);
        CutoverPlan plan = get(id);
        requireVersion(plan, expectedVersion);
        if (plan.status() != CutoverPlanStatus.IN_REVIEW) throw fail("CUTOVER_PLAN_STATE_INVALID", "Only IN_REVIEW plans can be rejected.");
        validator.requireIndependentApprover(plan, command.actorId());
        if (rejectionReason == null || rejectionReason.trim().length() < 12) throw fail("CUTOVER_REJECTION_REASON_REQUIRED", "Rejection reason must contain at least 12 characters.");
        return plans.reject(id, expectedVersion, command.actorId(), rejectionReason.trim(), command.auditReason(), command.correlationId(),
                command.idempotencyKey(), hash("REJECT", id, expectedVersion, rejectionReason.trim()), clock.instant());
    }

    public PublishCutoverResult publish(UUID id, long expectedVersion, ActorCommand command) {
        requireCommand(command);
        CutoverPlan plan = get(id);
        requireVersion(plan, expectedVersion);
        if (plan.status() != CutoverPlanStatus.APPROVED) throw fail("CUTOVER_PLAN_STATE_INVALID", "Only APPROVED plans can be published.");
        validator.requireIndependentPublisher(plan, command.actorId());
        validator.validateForReview(plan);
        validator.validateEvidenceUnchanged(plan.evidenceBindings(), evidence);
        String requestHash = hash("PUBLISH", id, expectedVersion, plan.routes(), plan.evidenceBindings());
        PublishedAuthorityRevision revision = publisher.publish(plan, command.actorId(), command.auditReason(), command.correlationId(),
                command.idempotencyKey(), requestHash, clock.instant());
        CutoverPlan published = get(id);
        return new PublishCutoverResult(published, revision, refresh.refresh(revision.revision(), command.actorId(), command.correlationId()));
    }

    public SnapshotRefreshService refreshService() { return refresh; }

    private static int bounded(int limit) { return Math.max(1, Math.min(limit, 200)); }
    private static void requireVersion(CutoverPlan plan, long expected) { if (expected < 1 || plan.version() != expected) throw fail("CUTOVER_PLAN_VERSION_CONFLICT", "Cutover plan version conflict."); }
    private static void requireCommand(ActorCommand command) {
        if (command == null || command.actorId().isBlank() || command.auditReason().length() < 12 || command.idempotencyKey().isBlank() || command.correlationId().isBlank()) {
            throw fail("CUTOVER_COMMAND_CONTEXT_INVALID", "Actor, correlation, idempotency key and an audit reason of at least 12 characters are required.");
        }
    }
    private static String hash(Object... values) {
        String canonical = java.util.Arrays.deepToString(values);
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static CutoverPlanException fail(String code, String message) { return new CutoverPlanException(code, message); }

    public record ActorCommand(String actorId, String auditReason, String correlationId, String idempotencyKey) {
        public ActorCommand {
            actorId = actorId == null ? "" : actorId.trim();
            auditReason = auditReason == null ? "" : auditReason.trim();
            correlationId = correlationId == null ? "" : correlationId.trim();
            idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        }
    }
}
