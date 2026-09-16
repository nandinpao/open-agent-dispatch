package com.opensocket.aievent.core.iam.token.application.command;

public record ReconcileServiceAccountOwnershipCommand(
        String tenantId,
        String serviceAccountId,
        String actorId,
        String correlationId) {
}
