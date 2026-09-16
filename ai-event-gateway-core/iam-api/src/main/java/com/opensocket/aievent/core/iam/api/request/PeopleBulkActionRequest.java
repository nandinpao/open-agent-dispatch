package com.opensocket.aievent.core.iam.api.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One server-side bulk command. The HTTP X-Audit-Reason and Idempotency-Key remain authoritative
 * write metadata; this body only carries the governed targets and operation.
 */
public record PeopleBulkActionRequest(
        @NotNull PeopleBulkOperation operation,
        @NotEmpty @Size(max = 500) List<@Size(min = 1, max = 160) String> userIds,
        @Size(max = 160) String targetDepartmentId,
        @Size(max = 100) List<@Size(min = 1, max = 160) String> groupIds,
        @Size(max = 32) String membershipRole,
        @Size(max = 32) String deliveryMethod) {
    public PeopleBulkActionRequest {
        userIds = userIds == null ? List.of() : userIds.stream().map(String::trim).distinct().toList();
        targetDepartmentId = targetDepartmentId == null ? "" : targetDepartmentId.trim();
        groupIds = groupIds == null ? List.of() : groupIds.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        membershipRole = membershipRole == null || membershipRole.isBlank() ? "MEMBER" : membershipRole.trim().toUpperCase();
        deliveryMethod = deliveryMethod == null || deliveryMethod.isBlank() ? "EMAIL" : deliveryMethod.trim().toUpperCase();
    }
}
