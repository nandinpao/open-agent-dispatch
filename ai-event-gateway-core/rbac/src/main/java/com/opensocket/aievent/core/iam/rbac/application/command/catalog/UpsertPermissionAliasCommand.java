package com.opensocket.aievent.core.iam.rbac.application.command.catalog;

import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import com.opensocket.aievent.core.iam.rbac.domain.catalog.PermissionAliasType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record UpsertPermissionAliasCommand(
        UUID revisionId,
        PermissionCode aliasCode,
        PermissionCode canonicalPermissionCode,
        PermissionAliasType aliasType,
        Instant validFrom,
        Optional<Instant> validUntil,
        String reason,
        long expectedVersion,
        String actorId,
        String correlationId,
        Instant requestedAt) {
    public UpsertPermissionAliasCommand {
        Objects.requireNonNull(revisionId,"revisionId");Objects.requireNonNull(aliasCode,"aliasCode");Objects.requireNonNull(canonicalPermissionCode,"canonicalPermissionCode");Objects.requireNonNull(aliasType,"aliasType");Objects.requireNonNull(validFrom,"validFrom");
        validUntil=validUntil==null?Optional.empty():validUntil;
        if(reason==null||reason.isBlank())throw new IllegalArgumentException("reason is required");
        if(expectedVersion<0)throw new IllegalArgumentException("expectedVersion cannot be negative");
        if(actorId==null||actorId.isBlank())throw new IllegalArgumentException("actorId is required");
        correlationId=correlationId==null?"":correlationId.trim();Objects.requireNonNull(requestedAt,"requestedAt");
    }
    public boolean create(){return expectedVersion==0;}
}
