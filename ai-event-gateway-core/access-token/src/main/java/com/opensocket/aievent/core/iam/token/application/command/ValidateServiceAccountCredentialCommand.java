package com.opensocket.aievent.core.iam.token.application.command;

public record ValidateServiceAccountCredentialCommand(
        String tenantId,
        String clientId,
        String clientSecret,
        String correlationId) {}
