package com.opensocket.aievent.core.iam.token.application.command;

import java.time.Duration;

public record RotateServiceAccountCredentialCommand(
        String tenantId,
        String serviceAccountId,
        String credentialId,
        Duration overlap,
        String actorId,
        String correlationId) {}
