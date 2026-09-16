package com.opensocket.aievent.core.iam.authentication.application.command;

import java.time.Instant;

/** Replaces an active browser session identifier after authentication assurance changes. */
public record RotateSessionCommand(
        String sessionId,
        String actorId,
        String ipAddress,
        String userAgent,
        String correlationId,
        Instant occurredAt,
        long expectedVersion) {
}
