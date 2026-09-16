package com.opensocket.aievent.core.iam.organization.application.command;

import com.opensocket.aievent.core.iam.organization.domain.GroupMembershipRole;
import java.time.Instant;
public record AddGroupMembershipCommand(String membershipId, String tenantId, String userId, String groupId, GroupMembershipRole membershipRole, Instant expiresAt, String actorId, String correlationId, String eventId) { }
