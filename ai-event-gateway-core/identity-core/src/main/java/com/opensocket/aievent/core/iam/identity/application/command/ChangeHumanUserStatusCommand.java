package com.opensocket.aievent.core.iam.identity.application.command;

import com.opensocket.aievent.core.iam.identity.domain.AccountStatus;

public record ChangeHumanUserStatusCommand(String userId, AccountStatus targetStatus, long expectedVersion,
                                           String reason, String actorId, String correlationId, String eventId) { }
