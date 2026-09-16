package com.opensocket.aievent.core.iam.token.application.command;

import java.time.Duration;

public record IssueServiceAccountCredentialCommand(
        String tenantId,
        String serviceAccountId,
        String name,
        Duration ttl,
        String actorId,
        String correlationId) {}
