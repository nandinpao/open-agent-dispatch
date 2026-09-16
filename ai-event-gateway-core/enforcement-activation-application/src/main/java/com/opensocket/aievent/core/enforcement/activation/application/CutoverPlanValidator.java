package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityMode;
import com.opensocket.aievent.core.enforcement.activation.contract.AuthorityRouteKey;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanRoute;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceBinding;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceType;

public final class CutoverPlanValidator {
    private final Clock clock;
    public CutoverPlanValidator(Clock clock) { this.clock = clock; }

    public void validateForReview(CutoverPlan plan) {
        if (plan.routes().isEmpty()) throw fail("CUTOVER_PLAN_ROUTES_REQUIRED", "At least one authority route is required.");
        Set<String> routeKeys = new HashSet<>();
        Set<String> routeTenants = new HashSet<>();
        for (CutoverPlanRoute route : plan.routes()) {
            String canonical = route.definition().key().canonicalValue();
            if (!routeKeys.add(canonical)) throw fail("CUTOVER_PLAN_DUPLICATE_ROUTE", "Duplicate authority route: " + canonical);
            String tenant = route.definition().key().tenantId();
            if (AuthorityRouteKey.WILDCARD.equals(tenant)) {
                throw fail("CUTOVER_PLAN_WILDCARD_TENANT_FORBIDDEN", "Phase 6B publication requires explicit Tenant cohorts.");
            }
            routeTenants.add(tenant);
            if (route.definition().mode() == AuthorityMode.TARGET_ONLY) {
                throw fail("CUTOVER_PLAN_TARGET_ONLY_NOT_ALLOWED", "TARGET_ONLY is reserved for Phase 6H.");
            }
        }
        for (String tenant : routeTenants) {
            if ("INSTANCE".equals(tenant)) continue;
            boolean bound = plan.evidenceBindings().stream().anyMatch(e -> e.evidenceType() == ReadinessEvidenceType.PHASE6_ELIGIBILITY
                    && e.tenantId().equals(tenant) && e.eligibleAt(clock.instant()));
            if (!bound) throw fail("CUTOVER_PLAN_PHASE6_EVIDENCE_REQUIRED", "Tenant " + tenant + " requires current ELIGIBLE Phase 6 evidence.");
        }
        boolean runtime = plan.evidenceBindings().stream().anyMatch(e -> e.evidenceType() == ReadinessEvidenceType.RUNTIME_CERTIFICATION
                && e.eligibleAt(clock.instant()));
        if (!runtime) throw fail("CUTOVER_PLAN_RUNTIME_EVIDENCE_REQUIRED", "Current CERTIFIED runtime certification evidence is required.");
    }

    public void validateEvidenceUnchanged(List<ReadinessEvidenceBinding> bound, ReadinessEvidenceRepository repository) {
        for (ReadinessEvidenceBinding expected : bound) {
            ReadinessEvidenceBinding current = repository.resolve(expected.evidenceType(), expected.evidenceId())
                    .orElseThrow(() -> fail("CUTOVER_EVIDENCE_MISSING", "Bound readiness evidence no longer exists: " + expected.evidenceId()));
            if (!current.checksum().equals(expected.checksum())) {
                throw fail("CUTOVER_EVIDENCE_CHECKSUM_CHANGED", "Bound readiness evidence checksum changed: " + expected.evidenceId());
            }
            if (!current.eligibleAt(clock.instant())) {
                throw fail("CUTOVER_EVIDENCE_NOT_ELIGIBLE", "Bound readiness evidence is blocked or expired: " + expected.evidenceId());
            }
        }
    }

    public void requireIndependentApprover(CutoverPlan plan, String actorId) {
        if (actorId.equals(plan.createdBy()) || actorId.equals(plan.submittedBy())) {
            throw fail("CUTOVER_SOD_VIOLATION", "Creator or submitter cannot approve the same cutover plan.");
        }
    }

    public void requireIndependentPublisher(CutoverPlan plan, String actorId) {
        if (actorId.equals(plan.createdBy())) {
            throw fail("CUTOVER_SOD_VIOLATION", "Creator cannot publish the same cutover plan.");
        }
    }

    private CutoverPlanException fail(String code, String message) { return new CutoverPlanException(code, message); }
}
