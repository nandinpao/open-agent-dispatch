package com.opensocket.aievent.core.iam.identity.application.command;

public record CreateRootIdentityCommand(String actorId, String correlationId, String eventId) { }
