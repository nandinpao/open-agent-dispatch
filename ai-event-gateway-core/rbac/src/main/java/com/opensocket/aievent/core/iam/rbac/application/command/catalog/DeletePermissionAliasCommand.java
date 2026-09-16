package com.opensocket.aievent.core.iam.rbac.application.command.catalog;

import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeletePermissionAliasCommand(UUID revisionId, PermissionCode aliasCode, long expectedVersion,
        String actorId, String correlationId, Instant requestedAt) {
    public DeletePermissionAliasCommand {
        Objects.requireNonNull(revisionId,"revisionId");Objects.requireNonNull(aliasCode,"aliasCode");
        if(expectedVersion<1)throw new IllegalArgumentException("expectedVersion must be positive");
        if(actorId==null||actorId.isBlank())throw new IllegalArgumentException("actorId is required");
        correlationId=correlationId==null?"":correlationId.trim();Objects.requireNonNull(requestedAt,"requestedAt");
    }
}
