package com.opensocket.aievent.core.iam.rbac.application.command.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PublishPermissionCatalogCommand(UUID revisionId,long expectedVersion,String auditReason,
        String actorId,String correlationId,Instant requestedAt) {
    public PublishPermissionCatalogCommand {
        Objects.requireNonNull(revisionId,"revisionId");if(expectedVersion<1)throw new IllegalArgumentException("expectedVersion must be positive");
        if(auditReason==null||auditReason.isBlank())throw new IllegalArgumentException("auditReason is required");
        if(actorId==null||actorId.isBlank())throw new IllegalArgumentException("actorId is required");
        correlationId=correlationId==null?"":correlationId.trim();Objects.requireNonNull(requestedAt,"requestedAt");
    }
}
