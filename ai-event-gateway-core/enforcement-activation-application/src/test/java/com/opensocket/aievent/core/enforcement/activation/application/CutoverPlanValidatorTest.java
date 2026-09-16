package com.opensocket.aievent.core.enforcement.activation.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteDefinition;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteKey;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanRoute;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceBinding;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;

class CutoverPlanValidatorTest {
    private final Instant now = Instant.parse("2026-07-31T00:00:00Z");
    private final CutoverPlanValidator validator = new CutoverPlanValidator(
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void requiresIndependentApprover() {
        CutoverPlan plan = plan("creator", "creator");
        CutoverPlanException error = assertThrows(
                CutoverPlanException.class,
                () -> validator.requireIndependentApprover(plan, "creator"));
        assertEquals("CUTOVER_SOD_VIOLATION", error.code());
    }

    @Test
    void acceptsExplicitTenantWithPhase6AndRuntimeEvidence() {
        assertDoesNotThrow(() -> validator.validateForReview(plan("creator", "submitter")));
    }


    @Test
    void instanceControlPlaneRouteRequiresRuntimeCertificationButNotTenantEligibility() {
        CutoverPlan base = plan("creator", "submitter");
        var route = new CutoverPlanRoute(
                0,
                new AuthorityRouteDefinition(
                        new AuthorityRouteKey("INSTANCE", "ENFORCEMENT", "READ", "READ", "WAVE0_ENFORCEMENT_RUNTIME_STATUS"),
                        AuthorityMode.SHADOW,
                        0,
                        Set.of(),
                        Set.of(),
                        "WAVE0_INSTANCE_READ"));
        ReadinessEvidenceBinding runtime = base.evidenceBindings().stream()
                .filter(item -> item.evidenceType() == ReadinessEvidenceType.RUNTIME_CERTIFICATION)
                .findFirst().orElseThrow();
        CutoverPlan instancePlan = new CutoverPlan(
                base.planId(), base.title(), base.description(), base.status(), base.version(), List.of(route),
                List.of(runtime), base.createdBy(), base.createdAt(), base.submittedBy(), base.submittedAt(),
                base.approvedBy(), base.approvedAt(), base.rejectedBy(), base.rejectedAt(), base.rejectionReason(),
                base.publishedRevision(), base.publishedBy(), base.publishedAt(), base.lastAuditReason(), base.correlationId());
        assertDoesNotThrow(() -> validator.validateForReview(instancePlan));
    }

    @Test
    void blocksTargetOnlyBeforePhase6H() {
        CutoverPlan base = plan("creator", "submitter");
        AuthorityRouteDefinition targetOnly = new AuthorityRouteDefinition(
                base.routes().getFirst().definition().key(),
                AuthorityMode.TARGET_ONLY,
                10_000,
                Set.of(),
                Set.of(),
                "TOO_EARLY");
        CutoverPlan changed = copyWithRoutes(
                base,
                List.of(new CutoverPlanRoute(0, targetOnly)));

        CutoverPlanException error = assertThrows(
                CutoverPlanException.class,
                () -> validator.validateForReview(changed));
        assertEquals("CUTOVER_PLAN_TARGET_ONLY_NOT_ALLOWED", error.code());
    }

    private CutoverPlan plan(String creator, String submitter) {
        String tenant = "tenant-a";
        var route = new CutoverPlanRoute(
                0,
                new AuthorityRouteDefinition(
                        new AuthorityRouteKey(tenant, "TASK", "READ", "READ", "task.list"),
                        AuthorityMode.TARGET_CANARY,
                        1_000,
                        Set.of(),
                        Set.of(),
                        "WAVE0"));
        var phase6 = new ReadinessEvidenceBinding(
                UUID.randomUUID(),
                ReadinessEvidenceType.PHASE6_ELIGIBILITY,
                tenant,
                "",
                "ELIGIBLE",
                "sha256:" + "a".repeat(64),
                now,
                now.plusSeconds(3_600),
                "phase5i",
                "{}");
        var runtime = new ReadinessEvidenceBinding(
                UUID.randomUUID(),
                ReadinessEvidenceType.RUNTIME_CERTIFICATION,
                "INSTANCE",
                "",
                "CERTIFIED",
                "sha256:" + "b".repeat(64),
                now,
                now.plusSeconds(3_600),
                "phase5j",
                "{}");
        return new CutoverPlan(
                UUID.randomUUID(),
                "Wave 0",
                "",
                CutoverPlanStatus.IN_REVIEW,
                2,
                List.of(route),
                List.of(phase6, runtime),
                creator,
                now,
                submitter,
                now,
                "",
                null,
                "",
                null,
                "",
                null,
                "",
                null,
                "",
                "");
    }

    private static CutoverPlan copyWithRoutes(CutoverPlan source, List<CutoverPlanRoute> routes) {
        return new CutoverPlan(
                source.planId(),
                source.title(),
                source.description(),
                source.status(),
                source.version(),
                routes,
                source.evidenceBindings(),
                source.createdBy(),
                source.createdAt(),
                source.submittedBy(),
                source.submittedAt(),
                source.approvedBy(),
                source.approvedAt(),
                source.rejectedBy(),
                source.rejectedAt(),
                source.rejectionReason(),
                source.publishedRevision(),
                source.publishedBy(),
                source.publishedAt(),
                source.lastAuditReason(),
                source.correlationId());
    }
}
