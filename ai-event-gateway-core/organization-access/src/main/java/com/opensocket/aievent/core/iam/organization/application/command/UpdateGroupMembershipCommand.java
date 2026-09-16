package com.opensocket.aievent.core.iam.organization.application.command;
import com.opensocket.aievent.core.iam.organization.domain.GroupMembershipRole;import java.time.Instant;
public record UpdateGroupMembershipCommand(String tenantId,String membershipId,GroupMembershipRole membershipRole,Instant expiresAt,boolean remove,long expectedVersion,String actorId,String correlationId,String eventId) { }
