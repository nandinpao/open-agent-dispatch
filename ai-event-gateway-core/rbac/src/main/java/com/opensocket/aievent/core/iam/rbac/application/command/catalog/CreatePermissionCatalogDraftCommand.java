package com.opensocket.aievent.core.iam.rbac.application.command.catalog;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CreatePermissionCatalogDraftCommand(
        UUID revisionId,
        String revisionCode,
        String description,
        String actorId,
        String correlationId,
        Instant requestedAt) {
    public CreatePermissionCatalogDraftCommand {
        Objects.requireNonNull(revisionId, "revisionId");
        revisionCode = required(revisionCode, "revisionCode", 128);
        description = description == null ? "" : description.trim();
        actorId = required(actorId, "actorId", 128);
        correlationId = correlationId == null ? "" : correlationId.trim();
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
    private static String required(String value,String field,int max){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");String x=value.trim();if(x.length()>max)throw new IllegalArgumentException(field+" exceeds "+max);return x;}
}
