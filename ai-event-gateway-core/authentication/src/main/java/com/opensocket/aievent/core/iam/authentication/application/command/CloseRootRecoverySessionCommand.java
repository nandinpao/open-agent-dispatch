package com.opensocket.aievent.core.iam.authentication.application.command;
import java.time.Instant;
public record CloseRootRecoverySessionCommand(String sessionId,String actorId,String reason,String correlationId,Instant occurredAt,long expectedSessionVersion) {}
