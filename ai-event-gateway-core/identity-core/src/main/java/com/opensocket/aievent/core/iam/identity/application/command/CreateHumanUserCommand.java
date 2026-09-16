package com.opensocket.aievent.core.iam.identity.application.command;

import com.opensocket.aievent.core.iam.identity.domain.UserCreationMode;

public record CreateHumanUserCommand(String userId, String username, String email, String displayName,
                                     UserCreationMode creationMode, String actorId, String correlationId,
                                     String eventId) { }
