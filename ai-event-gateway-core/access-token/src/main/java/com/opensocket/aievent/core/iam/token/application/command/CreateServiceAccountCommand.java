package com.opensocket.aievent.core.iam.token.application.command;

import com.opensocket.aievent.core.iam.token.domain.TokenScope;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public record CreateServiceAccountCommand(
        String tenantId,
        String serviceAccountId,
        String name,
        String description,
        String ownerUserId,
        String ownerDepartmentId,
        String responsibilityBindingId,
        TokenScope restrictions,
        Set<String> machineScopes,
        Set<String> allowedSourceSystems,
        Duration tokenMaxTtl,
        int maxActiveTokens,
        Duration credentialMaxTtl,
        int maxActiveCredentials,
        int rateLimitPerMinute,
        Instant nextReviewAt,
        String actorId,
        String correlationId) {
    public CreateServiceAccountCommand(
            String tenantId,String serviceAccountId,String name,String description,String ownerUserId,String ownerDepartmentId,
            TokenScope restrictions,Set<String> machineScopes,Set<String> allowedSourceSystems,Duration tokenMaxTtl,int maxActiveTokens,
            Duration credentialMaxTtl,int maxActiveCredentials,int rateLimitPerMinute,Instant nextReviewAt,String actorId,String correlationId) {
        this(tenantId,serviceAccountId,name,description,ownerUserId,ownerDepartmentId,"",restrictions,machineScopes,allowedSourceSystems,
                tokenMaxTtl,maxActiveTokens,credentialMaxTtl,maxActiveCredentials,rateLimitPerMinute,nextReviewAt,actorId,correlationId);
    }

    public CreateServiceAccountCommand(
            String tenantId,
            String serviceAccountId,
            String name,
            String description,
            String ownerUserId,
            String ownerDepartmentId,
            TokenScope restrictions,
            Duration tokenMaxTtl,
            int maxActiveTokens,
            int rateLimitPerMinute,
            Instant nextReviewAt,
            String actorId,
            String correlationId) {
        this(tenantId, serviceAccountId, name, description, ownerUserId, ownerDepartmentId, "",
                restrictions, Set.of(), Set.of(), tokenMaxTtl, maxActiveTokens,
                Duration.ofDays(180), 2, rateLimitPerMinute, nextReviewAt, actorId, correlationId);
    }
}
