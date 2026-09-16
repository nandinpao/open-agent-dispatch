package com.opensocket.aievent.core.iam.rbac.application.command.catalog;

import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeletePermissionDefinitionCommand(
        UUID revisionId,
        PermissionCode permissionCode,
        long expectedVersion,
        String actorId,
        String correlationId,
        Instant requestedAt) {
    public DeletePermissionDefinitionCommand {
        Objects.requireNonNull(revisionId,"revisionId");
        Objects.requireNonNull(permissionCode,"permissionCode");
        if(expectedVersion<1)throw new IllegalArgumentException("expectedVersion must be positive");
        if(actorId==null||actorId.isBlank())throw new IllegalArgumentException("actorId is required");
        Objects.requireNonNull(requestedAt,"requestedAt");
        correlationId=correlationId==null?"":correlationId.trim();
    }
}
