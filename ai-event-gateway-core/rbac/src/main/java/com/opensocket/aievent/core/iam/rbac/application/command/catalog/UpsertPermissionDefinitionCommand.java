package com.opensocket.aievent.core.iam.rbac.application.command.catalog;

import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.ScopeType;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionLifecycle;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionRiskLane;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record UpsertPermissionDefinitionCommand(
        UUID revisionId,
        PermissionCode permissionCode,
        String ownerModule,
        String resourceType,
        String actionCode,
        String description,
        String riskLevel,
        PermissionRiskLane riskLane,
        PermissionLifecycle lifecycle,
        Set<ScopeType> allowedScopes,
        boolean systemManaged,
        Optional<PermissionCode> replacementPermissionCode,
        Optional<Instant> deprecatedAt,
        Optional<Instant> retiredAt,
        long expectedVersion,
        String actorId,
        String correlationId,
        Instant requestedAt) {
    public UpsertPermissionDefinitionCommand {
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(permissionCode, "permissionCode");
        ownerModule = required(ownerModule,"ownerModule",128);
        resourceType = required(resourceType,"resourceType",96);
        actionCode = required(actionCode,"actionCode",96);
        description = required(description,"description",1000);
        riskLevel = required(riskLevel,"riskLevel",24);
        Objects.requireNonNull(riskLane,"riskLane");
        Objects.requireNonNull(lifecycle,"lifecycle");
        allowedScopes = allowedScopes == null ? Set.of() : Set.copyOf(allowedScopes);
        if (allowedScopes.isEmpty()) throw new IllegalArgumentException("allowedScopes is required");
        replacementPermissionCode = replacementPermissionCode == null ? Optional.empty() : replacementPermissionCode;
        deprecatedAt = deprecatedAt == null ? Optional.empty() : deprecatedAt;
        retiredAt = retiredAt == null ? Optional.empty() : retiredAt;
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion cannot be negative");
        actorId = required(actorId,"actorId",128);
        correlationId = correlationId == null ? "" : correlationId.trim();
        Objects.requireNonNull(requestedAt,"requestedAt");
    }
    public boolean create(){return expectedVersion==0;}
    private static String required(String value,String field,int max){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");String x=value.trim();if(x.length()>max)throw new IllegalArgumentException(field+" exceeds "+max);return x;}
}
