package com.opensocket.aievent.core.iam.identity.application.command;

import com.opensocket.aievent.core.iam.identity.domain.RootIdentityStatus;

public record ChangeRootIdentityStatusCommand(RootIdentityStatus targetStatus, long expectedVersion, String reason,
                                              String actorId, String correlationId, String eventId) { }
