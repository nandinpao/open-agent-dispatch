package com.opensocket.aievent.core.enforcement.activation.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanRoute;
import com.opensocket.aievent.core.enforcement.activation.contract.CutoverPlanStatus;
import com.opensocket.aievent.core.enforcement.activation.contract.ReadinessEvidenceBinding;

public record CutoverPlan(
        UUID planId,
        String title,
        String description,
        CutoverPlanStatus status,
        long version,
        List<CutoverPlanRoute> routes,
        List<ReadinessEvidenceBinding> evidenceBindings,
        String createdBy,
        Instant createdAt,
        String submittedBy,
        Instant submittedAt,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String rejectionReason,
        Long publishedRevision,
        String publishedBy,
        Instant publishedAt,
        String lastAuditReason,
        String correlationId) {

    public CutoverPlan {
        if (planId == null) throw new IllegalArgumentException("planId is required");
        title = required(title, "title", 160);
        description = description == null ? "" : description.trim();
        if (description.length() > 2000) throw new IllegalArgumentException("description exceeds 2000");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        routes = routes == null ? List.of() : List.copyOf(routes);
        evidenceBindings = evidenceBindings == null ? List.of() : List.copyOf(evidenceBindings);
        createdBy = required(createdBy, "createdBy", 128);
        if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
        submittedBy = normalize(submittedBy);
        approvedBy = normalize(approvedBy);
        rejectedBy = normalize(rejectedBy);
        rejectionReason = normalize(rejectionReason);
        publishedBy = normalize(publishedBy);
        lastAuditReason = normalize(lastAuditReason);
        correlationId = normalize(correlationId);
    }

    private static String required(String value, String field, int max) {
        String result = normalize(value);
        if (result.isBlank()) throw new IllegalArgumentException(field + " is required");
        if (result.length() > max) throw new IllegalArgumentException(field + " exceeds " + max);
        return result;
    }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
}
