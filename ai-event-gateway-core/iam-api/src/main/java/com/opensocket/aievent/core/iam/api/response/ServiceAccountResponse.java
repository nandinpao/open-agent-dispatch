package com.opensocket.aievent.core.iam.api.response;

import com.opensocket.aievent.core.iam.token.domain.ServiceAccount;
import java.time.Instant;
import java.util.Set;

public record ServiceAccountResponse(
        String tenantId,
        String serviceAccountId,
        String name,
        String description,
        String ownerUserId,
        String ownerDepartmentId,
        String responsibilityBindingId,
        String responsibilityRoleId,
        Set<String> permissions,
        Set<String> audiences,
        Set<String> apiPrefixes,
        Set<String> cidrs,
        Set<String> machineScopes,
        Set<String> allowedSourceSystems,
        long tokenMaxTtlSeconds,
        int maxActiveTokens,
        long credentialMaxTtlSeconds,
        int maxActiveCredentials,
        int rateLimitPerMinute,
        Instant nextReviewAt,
        String riskLevel,
        String status,
        long version) {
    public static ServiceAccountResponse from(ServiceAccount s) { return from(s, ""); }
    public static ServiceAccountResponse from(ServiceAccount s, String responsibilityRoleId) {
        return new ServiceAccountResponse(
                s.tenantId(),s.serviceAccountId().value(),s.name(),s.description(),s.ownerUserId(),s.ownerDepartmentId(),
                s.responsibilityBindingId(), responsibilityRoleId == null ? "" : responsibilityRoleId,
                s.restrictions().permissions(),s.restrictions().audiences(),s.restrictions().apiPrefixes(),
                s.restrictions().cidrs().stream().map(c->c.notation()).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                s.machineScopes(),s.allowedSourceSystems(),s.tokenMaxTtl().toSeconds(),s.maxActiveTokens(),
                s.credentialMaxTtl().toSeconds(),s.maxActiveCredentials(),s.rateLimitPerMinute(),s.nextReviewAt(),
                s.riskLevel().name(),s.status().name(),s.version());
    }
}
