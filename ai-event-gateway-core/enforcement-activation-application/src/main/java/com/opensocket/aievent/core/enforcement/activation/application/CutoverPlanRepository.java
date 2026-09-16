package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanRoute;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceBinding;

public interface CutoverPlanRepository {
    CutoverPlan create(UUID planId, String title, String description, String actorId, String auditReason,
                       String correlationId, String idempotencyKey, String requestHash, Instant now);
    Optional<CutoverPlan> find(UUID planId);
    List<CutoverPlan> list(CutoverPlanStatus status, int limit);
    CutoverPlan replaceRoutes(UUID planId, long expectedVersion, List<CutoverPlanRoute> routes, String actorId,
                              String auditReason, String correlationId, String idempotencyKey, String requestHash, Instant now);
    CutoverPlan bindEvidence(UUID planId, long expectedVersion, ReadinessEvidenceBinding binding, String actorId,
                             String auditReason, String correlationId, String idempotencyKey, String requestHash, Instant now);
    CutoverPlan submit(UUID planId, long expectedVersion, String actorId, String auditReason,
                       String correlationId, String idempotencyKey, String requestHash, Instant now);
    CutoverPlan approve(UUID planId, long expectedVersion, String actorId, String auditReason,
                        String correlationId, String idempotencyKey, String requestHash, Instant now);
    CutoverPlan reject(UUID planId, long expectedVersion, String actorId, String rejectionReason, String auditReason,
                       String correlationId, String idempotencyKey, String requestHash, Instant now);
}
