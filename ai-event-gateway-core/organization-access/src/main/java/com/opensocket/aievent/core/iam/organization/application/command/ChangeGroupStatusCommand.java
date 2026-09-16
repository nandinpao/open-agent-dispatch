package com.opensocket.aievent.core.iam.organization.application.command;
import com.opensocket.aievent.core.iam.organization.domain.GroupStatus;
public record ChangeGroupStatusCommand(String tenantId,String groupId,GroupStatus status,long expectedVersion,String reason,String actorId,String correlationId,String eventId) { }
