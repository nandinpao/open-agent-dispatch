package com.opensocket.aievent.core.iam.token.application.command;

public record RevokeServiceAccountCredentialCommand(
        String tenantId,
        String serviceAccountId,
        String credentialId,
        String reason,
        String actorId,
        String correlationId) {}
